package app.gamenative.wow.product

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WowProductTest {

    @Test
    fun fromCodeKnownProducts() {
        assertEquals(WowProduct.FOREVER, WowProduct.fromCode("wowf"))
        assertEquals(WowProduct.BETA, WowProduct.fromCode("wow_classic_beta"))
    }

    @Test
    fun fromCodeUnknownOrNull() {
        assertNull(WowProduct.fromCode("nope"))
        assertNull(WowProduct.fromCode(null))
    }

    @Test
    fun flavorDirsAndExes() {
        assertEquals("_forever_", WowProduct.FOREVER.flavorDir)
        assertEquals("Wow-arm64.exe", WowProduct.FOREVER.armExe)
        assertEquals("_classic_beta_", WowProduct.BETA.flavorDir)
        assertEquals("WowB-ARM64.exe", WowProduct.BETA.armExe)
    }
}
