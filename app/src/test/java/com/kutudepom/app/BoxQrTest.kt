package com.kutudepom.app

import org.junit.Assert.*
import org.junit.Test

class BoxQrTest {
    @Test fun roundtripAndLegacyCompatibility() { assertEquals(17L,BoxQr.decode(BoxQr.encode(17))) }
    @Test fun rejectsOtherQrCodes() {
        listOf("17","https://example.com","KUTUDEPOM:-1","KUTUDEPOM:0","KUTUDEPOM:9223372036854775808").forEach { assertNull(BoxQr.decode(it)) }
    }
}
