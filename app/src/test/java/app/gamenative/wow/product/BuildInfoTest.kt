package app.gamenative.wow.product

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuildInfoTest {

    private val header =
        "Branch!STRING:0|Active!DEC:1|Build Key!HEX:16|CDN Key!HEX:16|Install Key!HEX:16|IM Size!DEC:4|CDN Path!STRING:0|" +
            "CDN Hosts!STRING:0|CDN Servers!STRING:0|Tags!STRING:0|Armadillo!STRING:0|Last Activated!STRING:0|Version!STRING:0|" +
            "KeyRing!HEX:16|Product!STRING:0"
    private val row =
        "us|1|84bea4233331e390fda8724c6f91ffa5|f065eade5276a5ac05606e60a4c5a1f7|aaaa|1234|tpr/wow|" +
            "level3.blizzard.com us.cdn.blizzard.com||Windows arm64 enUS||2026-10-17T00:00:00Z|1.60.1.70450||wow_classic_beta"
    private val text = "$header\n$row\n"

    @Test
    fun roundTripsExactly() {
        val info = BuildInfo.parse(text)
        assertEquals(text, info.serialize())
        assertEquals(info, BuildInfo.parse(info.serialize()))
    }

    @Test
    fun activeRowForProduct() {
        val info = BuildInfo.parse(text)
        assertEquals("1.60.1.70450", info.active(WowProduct.BETA)!!["Version"])
        assertNull(info.active(WowProduct.FOREVER))
        assertEquals("wow_classic_beta", info.active()!!["Product"])
    }

    @Test
    fun withUpdatedActiveChangesOnlyNamedColumns() {
        val info = BuildInfo.parse(text)
        val updated = info.withUpdatedActive(WowProduct.BETA, mapOf("Version" to "1.60.1.99999", "Missing Column" to "x"))
        val before = info.active(WowProduct.BETA)!!
        val after = updated.active(WowProduct.BETA)!!
        assertEquals("1.60.1.99999", after["Version"])
        assertEquals(before - "Version", after - "Version")
        assertEquals(info.header, updated.header)
    }

    @Test
    fun withProductAddsActiveRowAndKeepsOld() {
        val converted = BuildInfo.parse(text).withProduct(WowProduct.BETA, WowProduct.FOREVER)
        assertEquals("wowf", converted.active(WowProduct.FOREVER)!!["Product"])
        assertEquals("1", converted.active(WowProduct.FOREVER)!!["Active"])
        assertEquals("1", converted.active(WowProduct.BETA)!!["Active"])
        assertEquals(listOf(WowProduct.FOREVER, WowProduct.BETA), converted.products(ForeverTrack.LAUNCH))
    }

    @Test
    fun detectPrefersSupportedProductInSharedInstall() {
        val dir = Files.createTempDirectory("bi").toFile()
        val retail = row.replace("wow_classic_beta", "wow")
        File(dir, ".build.info").writeText("$header\n$retail\n$row\n")
        assertEquals(WowProduct.BETA, detectProduct(dir))
    }

    @Test
    fun foreverTrackDecidesBetweenLaunchAndBeta() {
        val both = BuildInfo.parse(text).withProduct(WowProduct.BETA, WowProduct.FOREVER)
        assertEquals(listOf(WowProduct.FOREVER, WowProduct.BETA), both.products(ForeverTrack.LAUNCH))
        assertEquals(listOf(WowProduct.BETA, WowProduct.FOREVER), both.products(ForeverTrack.BETA))
    }

    @Test
    fun withProductIsIdempotent() {
        val once = BuildInfo.parse(text).withProduct(WowProduct.BETA, WowProduct.FOREVER)
        assertEquals(once, once.withProduct(WowProduct.BETA, WowProduct.FOREVER))
    }

    @Test
    fun readAndDetect() {
        val dir = Files.createTempDirectory("bi").toFile()
        assertNull(BuildInfo.read(dir))
        assertNull(detectProduct(dir))
        File(dir, ".build.info").writeText(text)
        assertEquals(WowProduct.BETA, detectProduct(dir))
    }

    @Test
    fun unknownProductIsNotDetected() {
        val dir = Files.createTempDirectory("bi").toFile()
        File(dir, ".build.info").writeText(text.replace("wow_classic_beta", "wow_classic_era"))
        assertNull(detectProduct(dir))
    }
}
