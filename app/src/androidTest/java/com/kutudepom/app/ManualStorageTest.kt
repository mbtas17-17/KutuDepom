package com.kutudepom.app

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile

@RunWith(AndroidJUnit4::class)
class ManualStorageTest {
    @Test fun rejectsEmptyUnsupportedOversizedAndEscapingDocumentsWithoutLeavingCopies() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val storage=ManualStorage(context)
        val folder=File(context.filesDir,"manuals").apply { mkdirs() }
        for((name,size) in listOf("empty.pdf" to 0L,"oversized.pdf" to ManualStorage.MAX_BYTES+1,"unsupported.apk" to 16L)) {
            val source=File(folder,name)
            RandomAccessFile(source,"rw").use { it.setLength(size) }
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",source)
            val before=folder.listFiles()!!.map { it.name }.toSet()
            try { storage.importUri(uri); fail("Belge reddedilmeliydi") } catch(ex:Exception) { }
            assertEquals(before,folder.listFiles()!!.map { it.name }.toSet())
            source.delete()
        }
        try { storage.file(Manual(name="X",storedName="../outside.pdf",mime="application/pdf",size=1)); fail("Yol reddedilmeliydi") }
        catch(ex:IllegalArgumentException) { }
    }
}
