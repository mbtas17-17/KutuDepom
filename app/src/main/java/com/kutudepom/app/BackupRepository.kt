package com.kutudepom.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import android.net.Uri
import android.system.Os
import android.system.OsConstants
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** SQL snapshots include committed WAL records; restore commits only after durable media staging. */
class BackupRepository(private val context:Context) {
    data class Prepared(val directory:File,val database:File)
    private data class Entry(val path:String,val size:Long,val sha256:String)
    companion object {
        const val FORMAT_VERSION=1
        const val SCHEMA_VERSION=3
        const val MAX_TOTAL=512L*1024*1024
        const val MAX_DATABASE=64L*1024*1024
        const val MAX_MANIFEST=1024L*1024
        const val MAX_ENTRIES=5000
        private const val DB_PATH="database/kutudepom.db"
        private val LEAF=Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,199}")
        fun defaultName()="KutuDepom_Yedek_"+SimpleDateFormat("yyyy-MM-dd_HHmm",Locale.ROOT).format(Date())+".zip"
    }
    private val tables=linkedMapOf(
        "boxes" to listOf("id","name","room","cupboard","shelf"),
        "items" to listOf("id","name","qty","box_id","note","photo"),
        "product_documents" to listOf("id","item_id","display_name","stored_name","mime","size")
    )
    private fun work()=File(context.cacheDir,"backup-work/${UUID.randomUUID()}").apply { check(mkdirs()) { "Geçici yedek alanı oluşturulamadı." } }
    private fun safeLeaf(name:String):String {
        require(LEAF.matches(name) && name!="." && name!="..") { "Yedekte güvenli olmayan dosya adı var." }; return name
    }
    private fun media(folder:String,name:String)=File(context.filesDir,"$folder/${safeLeaf(name)}").also {
        require(it.canonicalFile.parentFile==File(context.filesDir,folder).canonicalFile) { "Güvenli olmayan medya yolu." }
    }
    private fun hash(file:File):String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream -> val buffer=ByteArray(8192); while(true) { val n=stream.read(buffer); if(n<0) break; digest.update(buffer,0,n) } }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun copyRows(source:SQLiteDatabase,target:SQLiteDatabase,photos:Map<String,String> = emptyMap(),manuals:Map<String,String> = emptyMap()) {
        tables.forEach { (table,columns) ->
            source.rawQuery("SELECT ${columns.joinToString(",")} FROM $table ORDER BY id",null).use { c ->
                while(c.moveToNext()) {
                    val values=ContentValues()
                    columns.forEachIndexed { index,column ->
                        when {
                            c.isNull(index) -> values.putNull(column)
                            column=="photo" -> values.put(column,photos[c.getString(index)] ?: c.getString(index))
                            column=="stored_name" -> values.put(column,manuals[c.getString(index)] ?: c.getString(index))
                            column in setOf("id","qty","box_id","item_id","size") -> values.put(column,c.getLong(index))
                            else -> values.put(column,c.getString(index))
                        }
                    }
                    target.insertOrThrow(table,null,values)
                }
            }
        }
        target.execSQL("DELETE FROM sqlite_sequence WHERE name IN ('boxes','items','product_documents')")
        source.rawQuery("SELECT name,seq FROM sqlite_sequence WHERE name IN ('boxes','items','product_documents')",null).use { c ->
            while(c.moveToNext()) target.insertOrThrow("sqlite_sequence",null,ContentValues().apply { put("name",c.getString(0)); put("seq",c.getLong(1)) })
        }
    }
    private fun snapshot(file:File) {
        Store(context).use { store ->
            val source=store.readableDatabase
            source.beginTransactionNonExclusive()
            try {
                SQLiteDatabase.openOrCreateDatabase(file,null).use { target ->
                    Store(context).onCreate(target); target.version=SCHEMA_VERSION
                    target.beginTransaction()
                    try { copyRows(source,target); target.setTransactionSuccessful() }
                    finally { target.endTransaction() }
                }
                source.setTransactionSuccessful()
            } finally { source.endTransaction() }
        }
    }
    private fun referenced(database:SQLiteDatabase):Set<String> {
        val result=linkedSetOf<String>()
        database.rawQuery("SELECT DISTINCT photo FROM items WHERE photo IS NOT NULL",null).use { c -> while(c.moveToNext()) result.add("photos/${safeLeaf(c.getString(0))}") }
        database.rawQuery("SELECT stored_name FROM product_documents",null).use { c -> while(c.moveToNext()) result.add("manuals/${safeLeaf(c.getString(0))}") }
        return result
    }
    fun export(uri:Uri,progress:(String)->Unit={}) {
        val directory=work()
        try {
            progress("Veritabanının tutarlı kopyası hazırlanıyor…")
            val dbFile=File(directory,DB_PATH).apply { parentFile?.mkdirs() }; snapshot(dbFile)
            val files=linkedMapOf(DB_PATH to dbFile)
            SQLiteDatabase.openDatabase(dbFile.path,null,SQLiteDatabase.OPEN_READONLY).use { db -> referenced(db).forEach { path ->
                val parts=path.split('/'); val file=media(parts[0],parts[1])
                require(file.isFile) { "Fotoğraf veya kılavuz eksik: ${file.name}. Yedek oluşturulmadı." }; files[path]=file
            } }
            require(files.size+1<=MAX_ENTRIES) { "Yedekte en fazla $MAX_ENTRIES dosya olabilir." }
            require(files.values.sumOf { it.length() }<=MAX_TOTAL) { "Yedek içeriği en fazla 512 MB olabilir." }
            val records=files.map { (path,file) ->
                require(file.length()<=limit(path)) { "Yedekteki dosya boyutu desteklenen sınırı aşıyor: $path" }
                Entry(path,file.length(),hash(file))
            }
            val manifest=JSONObject().put("applicationId",context.packageName).put("format","KutuDepom")
                .put("formatVersion",FORMAT_VERSION).put("schemaVersion",SCHEMA_VERSION)
                .put("createdAt",SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.ROOT).format(Date()))
                .put("entries",JSONArray().apply { records.forEach { put(JSONObject().put("path",it.path).put("size",it.size).put("sha256",it.sha256)) } })
            val archive=File(directory,"backup.zip")
            ZipOutputStream(FileOutputStream(archive)).use { zip ->
                files.entries.forEachIndexed { index,(path,file) ->
                    progress("Dosyalar yedekleniyor… (${index+1}/${files.size})")
                    zip.putNextEntry(ZipEntry(path)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            require(archive.length()<=MAX_TOTAL) { "ZIP yedeği en fazla 512 MB olabilir." }
            // Validate our complete archive before writing to the user-selected document provider.
            val checked=archive.inputStream().use { prepare(it,progress) }; discard(checked)
            progress("Yedek seçtiğiniz dosyaya kaydediliyor…")
            val output=context.contentResolver.openOutputStream(uri,"wt") ?: error("Yedek dosyası açılamadı.")
            output.use { archive.inputStream().use { source -> source.copyTo(it) }; it.flush() }
        } finally { directory.deleteRecursively() }
    }
    fun prepare(uri:Uri,progress:(String)->Unit={}):Prepared {
        val input=context.contentResolver.openInputStream(uri) ?: error("Seçilen dosya açılamadı.")
        return input.use { prepare(it,progress) }
    }
    fun prepare(input:InputStream,progress:(String)->Unit={}):Prepared {
        val directory=work()
        try {
            progress("Yedek dosyası okunuyor…")
            val archive=File(directory,"source.zip")
            FileOutputStream(archive).use { out -> copyLimited(input,out,MAX_TOTAL) }
            val entries=linkedMapOf<String,Entry>(); var total=0L
            ZipFile(archive).use { zip ->
                val enumeration=zip.entries()
                while(enumeration.hasMoreElements()) {
                    require(entries.size<MAX_ENTRIES) { "Yedekte çok fazla dosya var." }
                    val entry=enumeration.nextElement(); val path=entry.name
                    require(!entry.isDirectory && allowed(path) && path !in entries) { "Yedekte geçersiz, yinelenen veya güvenli olmayan dosya yolu var." }
                    require(entry.size in 0..limit(path)) { "Yedekte bir dosyanın boyutu geçersiz veya çok büyük." }
                    val destination=File(directory,"validated/$path")
                    require(destination.canonicalPath.startsWith(File(directory,"validated").canonicalPath+File.separator)) { "Güvenli olmayan ZIP yolu." }
                    destination.parentFile?.mkdirs()
                    val crc=CRC32()
                    val size=zip.getInputStream(entry).use { stream -> FileOutputStream(destination).use { out -> copyLimited(stream,out,minOf(limit(path),MAX_TOTAL-total),crc) } }
                    require(size==entry.size && crc.value==entry.crc) { "Yedek ZIP dosyasının bütünlüğü bozuk." }
                    total+=size
                    entries[path]=Entry(path,size,hash(destination))
                    progress("Yedek doğrulanıyor… (${entries.size} dosya)")
                }
            }
            val manifestFile=File(directory,"validated/manifest.json")
            require(manifestFile.isFile && DB_PATH in entries) { "Yedekte manifest veya veritabanı eksik." }
            val manifest=JSONObject(manifestFile.readText(Charsets.UTF_8))
            require(manifest.getString("format")=="KutuDepom" && manifest.getString("applicationId")==context.packageName) { "Bu dosya bir KutuDepom yedeği değil." }
            require(manifest.getInt("formatVersion")==FORMAT_VERSION && manifest.getInt("schemaVersion")==SCHEMA_VERSION) { "Bu yedek sürümü desteklenmiyor. Uyumlu KutuDepom sürümünü kullanın." }
            val declared=manifest.getJSONArray("entries"); val seen=mutableSetOf<String>()
            require(declared.length()==entries.size-1) { "Yedekte eksik veya beklenmeyen dosya var." }
            for(i in 0 until declared.length()) {
                val record=declared.getJSONObject(i); val path=record.getString("path")
                require(path!="manifest.json" && seen.add(path)) { "Yedek manifestinde yinelenen dosya var." }
                val actual=entries[path] ?: error("Yedekte dosya eksik: $path")
                require(record.getLong("size")==actual.size && record.getString("sha256")==actual.sha256) { "Yedek dosyası eksik veya değiştirilmiş: $path" }
            }
            val prepared=Prepared(directory,File(directory,"validated/$DB_PATH"))
            validateDatabase(prepared,entries.keys-setOf(DB_PATH,"manifest.json"))
            archive.delete(); return prepared
        } catch(ex:Exception) { directory.deleteRecursively(); throw ex }
    }
    private fun allowed(path:String):Boolean {
        if(path==DB_PATH || path=="manifest.json") return true
        val parts=path.split('/')
        return parts.size==2 && parts[0] in setOf("photos","manuals") && LEAF.matches(parts[1]) && parts[1]!="." && parts[1]!=".."
    }
    private fun limit(path:String)=when {
        path=="manifest.json" -> MAX_MANIFEST
        path==DB_PATH -> MAX_DATABASE
        path.startsWith("photos/") -> 15L*1024*1024
        else -> 50L*1024*1024
    }
    private fun copyLimited(input:InputStream,output:FileOutputStream,limit:Long,crc:CRC32?=null):Long {
        val buffer=ByteArray(8192); var total=0L
        while(true) { val n=input.read(buffer); if(n<0) break; total+=n
            require(total<=limit) { "Yedek çok büyük; ZIP boyutu veya açılmış içerik sınırı aşıldı." }
            crc?.update(buffer,0,n); output.write(buffer,0,n)
        }
        output.fd.sync(); return total
    }
    private fun validateDatabase(prepared:Prepared,mediaPaths:Set<String>) {
        SQLiteDatabase.openDatabase(prepared.database.path,null,SQLiteDatabase.OPEN_READONLY).use { db ->
            require(db.version==SCHEMA_VERSION) { "Veritabanı sürümü uyumlu değil." }
            db.rawQuery("PRAGMA integrity_check",null).use { c -> require(c.moveToFirst() && c.getString(0)=="ok" && !c.moveToNext()) { "Yedek veritabanı bozuk." } }
            db.rawQuery("PRAGMA foreign_key_check",null).use { require(it.count==0) { "Kılavuz ilişkileri geçersiz." } }
            tables.forEach { (table,columns) ->
                db.rawQuery("SELECT type,sql FROM sqlite_master WHERE name=?",arrayOf(table)).use { c ->
                    require(c.moveToFirst() && c.getString(0)=="table" && c.getString(1).trim().startsWith("CREATE TABLE",true)) { "Yedek veritabanı yapısı geçersiz." }
                }
                db.rawQuery("PRAGMA table_info($table)",null).use { c ->
                    val actual=mutableSetOf<String>(); while(c.moveToNext()) actual.add(c.getString(1))
                    require(actual==columns.toSet()) { "Yedek tablo alanları uyumlu değil: $table" }
                }
                db.rawQuery("SELECT COUNT(*) FROM $table",null).use { c -> c.moveToFirst(); require(c.getLong(0)<=100000) { "Yedekte çok fazla kayıt var." } }
            }
            val invalid=listOf(
                "SELECT COUNT(*) FROM boxes WHERE id<=0 OR name IS NULL OR length(trim(name))=0 OR room IS NULL OR cupboard IS NULL OR shelf IS NULL",
                "SELECT COUNT(*) FROM items WHERE id<=0 OR name IS NULL OR length(trim(name))=0 OR note IS NULL OR qty IS NULL OR qty<0 OR qty>2147483647 OR box_id NOT IN (SELECT id FROM boxes) OR box_id IS NULL",
                "SELECT COUNT(*) FROM product_documents WHERE id<=0 OR display_name IS NULL OR length(display_name)=0 OR mime IS NULL OR length(mime)=0 OR stored_name IS NULL OR size IS NULL OR size<0 OR size>52428800 OR item_id NOT IN (SELECT id FROM items) OR item_id IS NULL"
            )
            invalid.forEach { sql -> db.rawQuery(sql,null).use { c -> c.moveToFirst(); require(c.getLong(0)==0L) { "Yedekte kutu, ürün veya kılavuz kaydı geçersiz." } } }
            val references=referenced(db)
            require(references==mediaPaths) { "Yedekte fotoğraf veya kılavuz eksik ya da ilişkisiz." }
            db.rawQuery("SELECT stored_name,size FROM product_documents",null).use { c -> while(c.moveToNext()) {
                require(File(prepared.directory,"validated/manuals/${safeLeaf(c.getString(0))}").length()==c.getLong(1)) { "Kılavuz dosyası eksik veya boyutu yanlış." }
            } }
            references.filter { it.startsWith("photos/") }.forEach { path ->
                val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                BitmapFactory.decodeFile(File(prepared.directory,"validated/$path").path,bounds)
                require(bounds.outWidth>0 && bounds.outHeight>0) { "Yedekteki fotoğraf okunamıyor." }
            }
            db.rawQuery("SELECT name,seq FROM sqlite_sequence",null).use { c -> while(c.moveToNext()) {
                val name=c.getString(0); require(name in tables && c.getLong(1)>=0 && c.getLong(1)<Long.MAX_VALUE) { "Yedekte kimlik sırası geçersiz." }
            } }
        }
    }
    /** Existing rows and files are untouched until this explicit, confirmed operation. */
    fun restore(prepared:Prepared,progress:(String)->Unit={},beforeCommit:()->Unit={}) {
        val created=mutableListOf<File>(); var committed=false
        try {
            SQLiteDatabase.openDatabase(prepared.database.path,null,SQLiteDatabase.OPEN_READONLY).use { source ->
                val photos=mutableMapOf<String,String>(); val manuals=mutableMapOf<String,String>()
                val refs=referenced(source)
                refs.forEachIndexed { index,path ->
                    progress("Dosyalar geri yükleniyor… (${index+1}/${refs.size})")
                    val parts=path.split('/'); val folder=File(context.filesDir,parts[0]).apply { mkdirs() }
                    val destination=File.createTempFile("restore-${UUID.randomUUID()}-",".bin",folder); created.add(destination)
                    File(prepared.directory,"validated/$path").inputStream().use { input -> FileOutputStream(destination).use { output -> copyLimited(input,output,limit(path)) } }
                    (if(parts[0]=="photos") photos else manuals)[parts[1]]=destination.name
                }
                created.mapNotNull { it.parentFile }.distinct().forEach { dir ->
                    val fd=Os.open(dir.path,OsConstants.O_RDONLY,0)
                    try { Os.fsync(fd) } finally { Os.close(fd) }
                }
                Store(context).use { targetStore ->
                    val target=targetStore.writableDatabase
                    val old=referenced(target)
                    target.execSQL("PRAGMA synchronous=FULL")
                    progress("Kutu ve ürünler güvenli işlemle geri yükleniyor…")
                    target.beginTransaction()
                    try {
                        target.delete("product_documents",null,null); target.delete("items",null,null); target.delete("boxes",null,null)
                        copyRows(source,target,photos,manuals)
                        beforeCommit(); target.setTransactionSuccessful()
                    } finally { target.endTransaction() }
                    committed=true
                    // A failed cleanup must never turn a committed restore into a reported failure.
                    old.forEach { path -> runCatching { val parts=path.split('/'); media(parts[0],parts[1]).delete() } }
                }
            }
        } finally { if(!committed) created.forEach { it.delete() }; discard(prepared) }
    }
    fun discard(prepared:Prepared) { prepared.directory.deleteRecursively() }
    fun message(ex:Exception):String = when(ex) {
        is java.util.zip.ZipException -> "Geçerli bir ZIP yedeği seçin. Dosya bozuk veya tamamlanmamış."
        is org.json.JSONException -> "Yedek manifesti eksik veya geçersiz."
        is android.database.sqlite.SQLiteException -> "Veritabanı okunamadı veya işlem tamamlanamadı. Mevcut kayıtlarınız korunuyor."
        is java.io.IOException -> "Dosya okunamadı veya kaydedilemedi. Boş alanı ve dosya erişimini kontrol edin."
        is SecurityException -> "Dosyaya erişim izni alınamadı. Dosyayı sistem seçicisinden yeniden seçin."
        is IllegalArgumentException, is IllegalStateException -> ex.message ?: "Yedek geçersiz. Mevcut kayıtlarınız korunuyor."
        else -> "İşlem tamamlanamadı. Mevcut kayıtlarınız korunuyor."
    }
}
