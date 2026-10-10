package com.kutudepom.app

import org.junit.Assert.*
import org.junit.Test

class ManualTypeTest {
    @Test fun commonTypesAndProviderFallbacks() {
        assertEquals("application/pdf",ManualStorage.mimeFor("Kılavuz.PDF","application/octet-stream"))
        assertEquals("application/pdf",ManualStorage.mimeFor("Belge", "application/pdf"))
        assertEquals("application/rtf",ManualStorage.mimeFor("Belge", "text/rtf"))
        listOf("doc","docx","xls","xlsx","ppt","pptx","odt","ods","odp","txt","csv","rtf","epub","md").forEach {
            assertNotNull(ManualStorage.mimeFor("kılavuz.$it",null))
        }
        assertNull(ManualStorage.mimeFor("app.apk","application/vnd.android.package-archive"))
        assertNull(ManualStorage.mimeFor("bilinmeyen.bin",null))
    }
}
