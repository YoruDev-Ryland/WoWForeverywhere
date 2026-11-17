package com.winlator.xserver

import android.view.KeyEvent
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyboardShiftTest {
    private fun down(keyCode: Int, meta: Int) = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, meta)

    @Test
    fun leftShiftStaysHeldWhenPressed() {
        val xServer = mockk<XServer>(relaxed = true)
        val keyboard = Keyboard(xServer)

        keyboard.onKeyEvent(down(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON))

        verify(exactly = 1) { xServer.injectKeyPress(XKeycode.KEY_SHIFT_L, any(), false) }
        verify(exactly = 0) { xServer.injectKeyRelease(XKeycode.KEY_SHIFT_L) }
    }

    @Test
    fun rightShiftGetsNoExtraLeftShift() {
        val xServer = mockk<XServer>(relaxed = true)
        val keyboard = Keyboard(xServer)

        keyboard.onKeyEvent(down(KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_RIGHT_ON))

        verify(exactly = 1) { xServer.injectKeyPress(XKeycode.KEY_SHIFT_R, any(), false) }
        verify(exactly = 0) { xServer.injectKeyPress(XKeycode.KEY_SHIFT_L, any(), any()) }
    }

    @Test
    fun aShiftedSymbolStillGetsShiftTappedAroundIt() {
        val xServer = mockk<XServer>(relaxed = true)
        val keyboard = Keyboard(xServer)

        keyboard.onKeyEvent(down(KeyEvent.KEYCODE_2, KeyEvent.META_SHIFT_ON))

        verify(exactly = 1) { xServer.injectKeyPress(XKeycode.KEY_SHIFT_L, 0, false) }
        verify(exactly = 1) { xServer.injectKeyRelease(XKeycode.KEY_SHIFT_L) }
    }
}
