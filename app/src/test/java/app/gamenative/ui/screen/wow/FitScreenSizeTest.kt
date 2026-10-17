package app.gamenative.ui.screen.wow

import org.junit.Assert.assertEquals
import org.junit.Test

class FitScreenSizeTest {

    @Test
    fun sixteenByNineWindow_keepsBaseSize() {
        assertEquals("1920x1080", fitScreenSize("1920x1080", 2400, 1350))
    }

    @Test
    fun foldInnerScreen_narrowsWidth() {
        assertEquals("1198x1080", fitScreenSize("1920x1080", 2184, 1968))
    }

    @Test
    fun portraitWindow_usesLandscapeAspect() {
        assertEquals("1198x1080", fitScreenSize("1920x1080", 1968, 2184))
    }

    @Test
    fun widePhone_shortensHeight() {
        assertEquals("1920x864", fitScreenSize("1920x1080", 2400, 1080))
    }

    @Test
    fun adreno8xxBase_scalesFromSmallerBudget() {
        assertEquals("800x720", fitScreenSize("1280x720", 2184, 1968))
    }

    @Test
    fun unknownWindow_keepsBaseSize() {
        assertEquals("1280x720", fitScreenSize("1280x720", 0, 0))
    }
}
