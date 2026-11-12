package app.gamenative.wow.product

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameRegionTest {

    @After
    fun reset() {
        GameRegions.chosen = null
    }

    @Test
    fun codesMapToRegions() {
        assertEquals(GameRegion.EU, GameRegion.fromCode("eu"))
        assertEquals(GameRegion.TW, GameRegion.fromCode("TW"))
        assertNull(GameRegion.fromCode("cn"))
        assertNull(GameRegion.fromCode(null))
    }

    @Test
    fun effectiveRegionFollowsTheInstallUntilOneIsChosen() {
        assertEquals(GameRegion.US, GameRegions.effective(null))
        assertEquals(GameRegion.EU, GameRegions.effective("eu"))
        GameRegions.chosen = GameRegion.KR
        assertEquals(GameRegion.KR, GameRegions.effective("eu"))
    }

    @Test
    fun settingsShowsTheRegionTheGameLogsInto() {
        // a US install whose Config.wtf still logs into Europe shows Europe, not Americas (#46)
        assertEquals(GameRegion.EU, GameRegions.shown("us", "EU"))
        assertEquals(GameRegion.KR, GameRegions.shown("tw", "KR"))
        // no portal yet, or one that isn't a region (the beta's "test"): the install's region
        assertEquals(GameRegion.US, GameRegions.shown("us", null))
        assertEquals(GameRegion.EU, GameRegions.shown("eu", "test"))
        // a picked region is what the game uses
        GameRegions.chosen = GameRegion.US
        assertEquals(GameRegion.US, GameRegions.shown("us", "EU"))
    }

    @Test
    fun taiwanLogsInThroughTheKoreanPortal() {
        assertEquals("KR", GameRegion.TW.portal)
        assertEquals("tw", GameRegion.TW.code)
        assertEquals("US", GameRegion.US.portal)
    }
}
