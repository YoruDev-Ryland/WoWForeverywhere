package app.gamenative.wow.product

import org.junit.Assert.assertEquals
import org.junit.Test

class GameVersionTest {

    private val header = "Branch!STRING:0|Active!DEC:1|Version!STRING:0|Product!STRING:0"

    private fun info(vararg products: String) =
        BuildInfo.parse((listOf(header) + products.map { "us|1|1.0.0.1|$it" }).joinToString("\n", postfix = "\n"))

    @Test
    fun foreverTrackDefaultsToLaunch() {
        assertEquals(ForeverTrack.LAUNCH, ForeverTrack.fromKey(null))
        assertEquals(ForeverTrack.BETA, ForeverTrack.fromKey("beta"))
        assertEquals(ForeverTrack.LAUNCH, ForeverTrack.fromKey("nonsense"))
    }

    @Test
    fun fromKeyDefaultsToForever() {
        assertEquals(GameVersion.FOREVER, GameVersion.fromKey(null))
        assertEquals(GameVersion.FOREVER, GameVersion.fromKey("no-such-version"))
        assertEquals(GameVersion.RETAIL, GameVersion.fromKey("retail"))
    }

    @Test
    fun sharedInstallFollowsSelectedVersion() {
        val shared = info("wowf", "wow_classic_era")
        assertEquals(listOf(WowProduct.FOREVER), shared.products(version = GameVersion.FOREVER))
        assertEquals(listOf(WowProduct.CLASSIC_ERA), shared.products(version = GameVersion.CLASSIC_ERA))
        assertEquals(emptyList<WowProduct>(), shared.products(version = GameVersion.RETAIL))
    }

    @Test
    fun foreverOrderingUnchanged() {
        val both = info("wowf", "wow_classic_beta")
        assertEquals(listOf(WowProduct.BETA, WowProduct.FOREVER), both.products(ForeverTrack.BETA, GameVersion.FOREVER))
        assertEquals(listOf(WowProduct.FOREVER, WowProduct.BETA), both.products(ForeverTrack.LAUNCH, GameVersion.FOREVER))
    }

    @Test
    fun newProductsHaveVerifiedValues() {
        fun check(p: WowProduct, code: String, dir: String, exe: String, version: GameVersion) {
            assertEquals(code, p.code)
            assertEquals(dir, p.flavorDir)
            assertEquals(exe, p.armExe)
            assertEquals(version, GameVersion.of(p))
        }
        check(WowProduct.CLASSIC_ERA, "wow_classic_era", "_classic_era_", "WowClassic-arm64.exe", GameVersion.CLASSIC_ERA)
        check(WowProduct.ANNIVERSARY, "wow_anniversary", "_anniversary_", "WowClassic-arm64.exe", GameVersion.ANNIVERSARY)
        check(WowProduct.MISTS, "wow_classic", "_classic_", "WowClassic-arm64.exe", GameVersion.MISTS)
        check(WowProduct.RETAIL, "wow", "_retail_", "Wow-ARM64.exe", GameVersion.RETAIL)
        assertEquals(GameVersion.FOREVER, GameVersion.of(WowProduct.BETA))
        assertEquals("WoW Forever", GameVersion.FOREVER.defaultFolder)
        assertEquals("World of Warcraft", GameVersion.RETAIL.defaultFolder)
    }
}
