package com.kutudepom.app

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Copies documents instead of retaining access to a provider or a cloud URI. */
class ManualStorage(private val context:Context) {
    private val folder=File(context.filesDir,"manuals").apply { mkdirs() }
    fun importUri(uri:Uri):Manual {
        var name="Belge"
        var reportedSize:Long?=null
        context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use { c ->
            if(c.moveToFirst()) {
                val index=c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if(index>=0 && !c.isNull(index)) name=c.getString(index).take(255)
                val sizeIndex=c.getColumnIndex(OpenableColumns.SIZE)
                if(sizeIndex>=0 && !c.isNull(sizeIndex)) reportedSize=c.getLong(sizeIndex)
            }
        }
        require((reportedSize ?: 0)<=MAX_BYTES) { "Belge en fazla 50 MB olabilir." }
        val mime=mimeFor(name,context.contentResolver.getType(uri))
            ?: error("PDF, Office, OpenDocument, metin, RTF veya EPUB belgesi seçin.")
        val extension=types.entries.first { it.value==mime }.key
        val target=File(folder,"${UUID.randomUUID()}.$extension")
        val temporary=File(folder,target.name+".part")
        try {
            var total=0L
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Belge okunamadı." }
                temporary.outputStream().use { output ->
                    val buffer=ByteArray(16384)
                    while(true) {
                        if(Thread.currentThread().isInterrupted) error("Belge kopyalama iptal edildi.")
                        val count=input.read(buffer); if(count<0) break
                        total+=count; require(total<=MAX_BYTES) { "Belge en fazla 50 MB olabilir." }
                        output.write(buffer,0,count)
                    }
                }
            }
            require(total>0) { "Boş belge eklenemez." }
            check(temporary.renameTo(target)) { "Belge kaydedilemedi." }
            return Manual(name=name,storedName=target.name,mime=mime,size=total)
        } catch(ex:Exception) { temporary.delete(); target.delete(); throw ex }
    }
    fun file(manual:Manual):File = File(folder,manual.storedName).also {
        require(it.canonicalFile.parentFile==folder.canonicalFile) { "Geçersiz belge yolu." }
    }
    fun remove(manual:Manual) { file(manual).delete() }
    fun openIntent(manual:Manual):Intent {
        val file=file(manual); check(file.isFile) { "Belge cihazda bulunamadı." }
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri,manual.mime)
            clipData=ClipData.newRawUri(manual.name,uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    fun cleanOrphans(retained:Set<String>) { folder.listFiles()?.filter { it.name !in retained }?.forEach { it.delete() } }
    companion object {
        const val MAX_BYTES=50L*1024*1024
        private val types=linkedMapOf(
            "pdf" to "application/pdf", "doc" to "application/msword",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xls" to "application/vnd.ms-excel", "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "ppt" to "application/vnd.ms-powerpoint", "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "odt" to "application/vnd.oasis.opendocument.text", "ods" to "application/vnd.oasis.opendocument.spreadsheet",
            "odp" to "application/vnd.oasis.opendocument.presentation", "txt" to "text/plain",
            "csv" to "text/csv", "rtf" to "application/rtf", "epub" to "application/epub+zip", "md" to "text/markdown"
        )
        val pickerTypes:Array<String> get()=types.values.distinct().toTypedArray()+"text/rtf"
        fun mimeFor(name:String,reported:String?):String? = types[name.substringAfterLast('.',"").lowercase(java.util.Locale.ROOT)]
            ?: reported?.lowercase(java.util.Locale.ROOT)?.let { if(it=="text/rtf") "application/rtf" else it.takeIf { value -> value in types.values } }
    }
}
