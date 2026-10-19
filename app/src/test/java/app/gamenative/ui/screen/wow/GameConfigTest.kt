package app.gamenative.ui.screen.wow

import app.gamenative.wow.product.WowProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameConfigTest {

    @Test
    fun newForeverConfigUsesLivePortalAndProduct() {
        val lines = buildConfigWtf(null, WowProduct.FOREVER, "1920x1080")!!
        assertTrue("SET portal \"US\"" in lines)
        assertTrue("SET agentUID \"wowf\"" in lines)
        assertTrue("SET gxApi \"D3D11\"" in lines)
        assertTrue("SET gxFullscreenResolution \"1920x1080\"" in lines)
    }

    @Test
    fun newBetaConfigUsesTestPortal() {
        val lines = buildConfigWtf(null, WowProduct.BETA, "1280x720")!!
        assertTrue("SET portal \"test\"" in lines)
        assertTrue("SET agentUID \"wow_classic_beta\"" in lines)
    }

    @Test
    fun existingConfigKeepsUserLinesAndForcesD3D11() {
        val existing = listOf(
            "SET portal \"US\"",
            "SET gxApi \"Metal\"",
            "SET graphicsQuality \"7\"",
            "SET gxFullscreenResolution \"1920x1080\"",
            "SET gxWindowedResolution \"1920x1080\"",
        )
        val lines = buildConfigWtf(existing, WowProduct.FOREVER, "1920x1080")!!
        assertTrue("SET graphicsQuality \"7\"" in lines)
        assertTrue("SET gxApi \"D3D11\"" in lines)
        assertTrue(lines.none { it == "SET gxApi \"Metal\"" })
    }

    @Test
    fun staleResolutionIsReset() {
        val existing = listOf(
            "SET portal \"US\"",
            "SET gxApi \"D3D11\"",
            "SET gxFullscreenResolution \"2560x1440\"",
            "SET gxWindowedResolution \"1280x720\"",
        )
        val lines = buildConfigWtf(existing, WowProduct.FOREVER, "1920x1080")!!
        assertTrue("SET gxFullscreenResolution \"1920x1080\"" in lines)
        assertTrue("SET gxWindowedResolution \"1280x720\"" in lines)
    }

    @Test
    fun carriedOverConfigGetsProductPortalAndAgent() {
        val existing = listOf(
            "SET agentUID \"wow_classic_beta\"",
            "SET gxApi \"D3D11\"",
            "SET gxFullscreenResolution \"1920x1080\"",
            "SET gxWindowedResolution \"1920x1080\"",
        )
        val lines = buildConfigWtf(existing, WowProduct.FOREVER, "1920x1080")!!
        assertTrue("SET portal \"US\"" in lines)
        assertTrue("SET agentUID \"wowf\"" in lines)
        assertTrue("SET agentUID \"wow_classic_beta\"" !in lines)
    }

    @Test
    fun userPortalIsKept() {
        val existing = listOf("SET portal \"eu\"", "SET gxApi \"Metal\"")
        val lines = buildConfigWtf(existing, WowProduct.FOREVER, "1920x1080")!!
        assertTrue("SET portal \"eu\"" in lines)
        assertTrue(lines.none { it == "SET portal \"US\"" })
    }

    @Test
    fun unchangedConfigReturnsNull() {
        val existing = listOf(
            "SET portal \"US\"",
            "SET gxApi \"D3D11\"",
            "SET gxFullscreenResolution \"1920x1080\"",
            "SET gxWindowedResolution \"1920x1080\"",
        )
        assertNull(buildConfigWtf(existing, WowProduct.FOREVER, "1920x1080"))
    }

    @Test
    fun flavorInfoNamesProduct() {
        assertEquals("Product Flavor!STRING:0\nwowf\n", flavorInfoText(WowProduct.FOREVER))
    }
}
