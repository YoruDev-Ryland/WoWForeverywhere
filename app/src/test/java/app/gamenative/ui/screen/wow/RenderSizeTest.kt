package app.gamenative.ui.screen.wow

import app.gamenative.wow.product.RenderSize
import org.junit.Assert.assertEquals
import org.junit.Test

class RenderSizeTest {

    @Test
    fun automaticKeepsTheDeviceProfile() {
        assertEquals("1280x720", renderSize(RenderSize.AUTO, "1280x720", 1920, 1080))
        assertEquals("1920x1080", renderSize(RenderSize.AUTO, "1920x1080", 1920, 1080))
    }

    @Test
    fun fixedSizesKeepTheirPixelCountAndFitTheScreen() {
        assertEquals("1920x1080", renderSize(RenderSize.P1080, "1280x720", 1920, 1080))
        assertEquals("1600x900", renderSize(RenderSize.P900, "1280x720", 1080, 1920))
    }

    @Test
    fun nativeUsesTheScreenInLandscape() {
        assertEquals("1920x1080", renderSize(RenderSize.NATIVE, "1280x720", 1080, 1920))
        assertEquals("2400x1080", renderSize(RenderSize.NATIVE, "1280x720", 2400, 1080))
    }
}
