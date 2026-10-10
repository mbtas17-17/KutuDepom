package com.kutudepom.app

import android.content.ContentValues
import android.database.DatabaseUtils
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val repository get()=BackupRepository(context)
    private fun seed() {
        context.deleteDatabase("kutudepom.db")
        val photo=File(context.filesDir,"photos/backup-test.img").apply { parentFile?.mkdirs() }
        val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        val manual=File(context.filesDir,"manuals/backup-test.pdf").apply { parentFile?.mkdirs(); writeText("%PDF-1.4\nKutuDepom kullanım kılavuzu\n%%EOF") }
        Store(context).use { s ->
            s.writableDatabase.execSQL("INSERT INTO boxes(id,name,room,cupboard,shelf) VALUES(17,'1','Çalışma odası','Mavi dolap','Üst raf')")
            s.writableDatabase.execSQL("INSERT INTO boxes(id,name,room,cupboard,shelf) VALUES(18,'İkinci kutu','','','')")
            s.writableDatabase.execSQL("INSERT INTO boxes(id,name,room,cupboard,shelf) VALUES(99,'Silinmiş kimlik','','','')")
            s.writableDatabase.delete("boxes","id=99",null)
            s.writableDatabase.execSQL("INSERT INTO items(id,name,qty,box_id,note,photo) VALUES(23,'Matkap',7,17,?,'backup-test.img')",arrayOf("Çığ, öğütücü, şişe, İğne.\n".repeat(30)))
            s.writableDatabase.execSQL("INSERT INTO product_documents(id,item_id,display_name,stored_name,mime,size) VALUES(31,23,'Kılavuz.pdf','backup-test.pdf','application/pdf',?)",arrayOf(manual.length()))
        }
    }
    private fun archive():File {
        val file=File(context.cacheDir,"labels/backup-test.zip").apply { parentFile?.mkdirs() }
        repository.export(FileProvider.getUriForFile(context,"${context.packageName}.files",file))
        return file
    }
    private fun currentNote():String=Store(context).use { s -> s.readableDatabase.rawQuery("SELECT note FROM items WHERE id=23",null).use { c -> assertTrue(c.moveToFirst()); c.getString(0) } }
    private fun zip(records:Map<String,ByteArray>):ByteArray {
        val bytes=ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out -> records.forEach { (name,value) -> out.putNextEntry(ZipEntry(name)); out.write(value); out.closeEntry() } }
        return bytes.toByteArray()
    }
    private fun entries(file:File):Map<String,ByteArray> = ZipFile(file).use { z -> z.entries().asSequence().associate { entry -> entry.name to z.getInputStream(entry).use { it.readBytes() } } }
    private fun reject(bytes:ByteArray) {
        val note=currentNote(); val photo=File(context.filesDir,"photos/backup-test.img").readBytes(); val manual=File(context.filesDir,"manuals/backup-test.pdf").readBytes()
        try { val prepared=repository.prepare(ByteArrayInputStream(bytes)); repository.discard(prepared); fail("Invalid archive must be rejected") }
        catch(expected:Exception) { assertTrue(repository.message(expected).isNotBlank()) }
        assertEquals(note,currentNote()); assertArrayEquals(photo,File(context.filesDir,"photos/backup-test.img").readBytes()); assertArrayEquals(manual,File(context.filesDir,"manuals/backup-test.pdf").readBytes())
    }
    @Test fun roundTripRestoresIDsLocationsNotesPhotosManualsAndSequence() {
        seed()
        try {
            val photo=File(context.filesDir,"photos/backup-test.img").readBytes(); val manual=File(context.filesDir,"manuals/backup-test.pdf").readBytes(); val note=currentNote()
            val archive=archive(); val records=entries(archive)
            assertEquals(setOf("database/kutudepom.db","manifest.json","photos/backup-test.img","manuals/backup-test.pdf"),records.keys)
            assertEquals(1,JSONObject(String(records.getValue("manifest.json"))).getInt("formatVersion"))
            val prepared=repository.prepare(archive.inputStream())
            context.deleteDatabase("kutudepom.db")
            File(context.filesDir,"photos").deleteRecursively(); File(context.filesDir,"manuals").deleteRecursively()
            Store(context).use { assertTrue(it.boxes().isEmpty()) }
            repository.restore(prepared)
            Store(context).use { s ->
                assertEquals(2,s.boxes().size)
                assertEquals("Çalışma odası → Mavi dolap → Üst raf → 1",s.boxes().first { it.first==17L }.second)
                assertEquals(17L,BoxQr.decode(BoxQr.encode(17)))
                s.readableDatabase.rawQuery("SELECT name,qty,box_id,note,photo FROM items WHERE id=23",null).use { c ->
                    assertTrue(c.moveToFirst()); assertEquals("Matkap",c.getString(0)); assertEquals(7,c.getInt(1)); assertEquals(17,c.getInt(2)); assertEquals(note,c.getString(3))
                    assertArrayEquals(photo,File(context.filesDir,"photos/${c.getString(4)}").readBytes())
                }
                val document=s.manuals(23).single(); assertEquals(31L,document.id); assertEquals("Kılavuz.pdf",document.name)
                val open=ManualStorage(context).openIntent(document); assertEquals("application/pdf",open.type)
                assertArrayEquals(manual,context.contentResolver.openInputStream(open.data!!)!!.use { it.readBytes() })
                val next=s.writableDatabase.insertOrThrow("boxes",null,ContentValues().apply { put("name","Yeni");put("room","");put("cupboard","");put("shelf","") })
                assertTrue("Deleted QR identities must not be reused",next>99)
            }
        } finally { context.deleteDatabase("kutudepom.db") }
    }
    @Test fun snapshotIncludesCommittedWALRecordsWithoutChangingSource() {
        seed()
        try {
            Store(context).use { source ->
                assertTrue(source.writableDatabase.enableWriteAheadLogging())
                source.writableDatabase.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)",null).use { it.moveToFirst() }
                source.writableDatabase.execSQL("UPDATE items SET note='WAL içindeki son kayıt' WHERE id=23")
                assertTrue(File(context.getDatabasePath("kutudepom.db").path+"-wal").length()>0)
                val prepared=repository.prepare(archive().inputStream())
                android.database.sqlite.SQLiteDatabase.openDatabase(prepared.database.path,null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
                    assertEquals("WAL içindeki son kayıt",DatabaseUtils.stringForQuery(db,"SELECT note FROM items WHERE id=23",null))
                }
                repository.discard(prepared)
                assertEquals("WAL içindeki son kayıt",currentNote())
            }
        } finally { context.deleteDatabase("kutudepom.db") }
    }
    @Test fun malformedMissingTamperedIncompatibleTraversalAndBombLeaveCurrentDataIntact() {
        seed()
        try {
            val archive=archive(); val original=entries(archive)
            reject("ZIP olmayan dosya".toByteArray())
            reject(zip(original-setOf("photos/backup-test.img")))
            reject(zip(original+mapOf("manuals/backup-test.pdf" to "değiştirilmiş belge".toByteArray())))
            val manifest=JSONObject(String(original.getValue("manifest.json"))).put("formatVersion",999).toString().toByteArray()
            reject(zip(original+mapOf("manifest.json" to manifest)))
            listOf("../escape.txt","/absolute.txt","photos/../escape.txt","photos\\escape.txt").forEach { reject(zip(mapOf(it to "x".toByteArray()))) }
            reject(zip(mapOf("manifest.json" to ByteArray(BackupRepository.MAX_MANIFEST.toInt()+1))))
            reject(archive.readBytes().copyOf(archive.length().toInt()-100))
        } finally { context.deleteDatabase("kutudepom.db") }
    }
    @Test fun failedDatabaseCommitRollsBackRowsAndRemovesOnlyNewFiles() {
        seed()
        try {
            val note=currentNote(); val photo=File(context.filesDir,"photos/backup-test.img").readBytes(); val manual=File(context.filesDir,"manuals/backup-test.pdf").readBytes()
            val beforePhotos=File(context.filesDir,"photos").list()!!.toSet(); val beforeManuals=File(context.filesDir,"manuals").list()!!.toSet()
            val prepared=repository.prepare(archive().inputStream())
            try { repository.restore(prepared,beforeCommit={ throw java.io.IOException("Simulated commit failure") }); fail("Restore should roll back") }
            catch(expected:java.io.IOException) { }
            assertEquals(note,currentNote()); assertArrayEquals(photo,File(context.filesDir,"photos/backup-test.img").readBytes()); assertArrayEquals(manual,File(context.filesDir,"manuals/backup-test.pdf").readBytes())
            assertEquals(beforePhotos,File(context.filesDir,"photos").list()!!.toSet()); assertEquals(beforeManuals,File(context.filesDir,"manuals").list()!!.toSet())
            assertFalse(prepared.directory.exists())
        } finally { context.deleteDatabase("kutudepom.db") }
    }
}
