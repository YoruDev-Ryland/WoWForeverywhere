package com.winlator.inputcontrols

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** L2/R2 for controllers whose triggers are analog, digital, or both (#44). */
@RunWith(RobolectricTestRunner::class)
class ExternalControllerTriggerTest {

    private fun key(code: Int, down: Boolean) = KeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, code)

    @Test
    fun aTriggerThatOnlySendsItsButtonWorks() {
        val c = ExternalController()
        c.updateStateFromKeyEvent(key(KeyEvent.KEYCODE_BUTTON_L2, true))
        assertEquals(1f, c.state.triggerL, 0f)
        assertTrue(c.state.isPressed(ExternalController.IDX_BUTTON_L2.toInt()))
        // a stick moving sends motion events with the trigger axis at 0; the held button stays held
        c.applyAnalogTriggers(0f, 0f)
        assertEquals(1f, c.state.triggerL, 0f)
        c.updateStateFromKeyEvent(key(KeyEvent.KEYCODE_BUTTON_L2, false))
        assertEquals(0f, c.state.triggerL, 0f)
        assertFalse(c.state.isPressed(ExternalController.IDX_BUTTON_L2.toInt()))
        assertFalse(c.hasAnalogTrigger(KeyEvent.KEYCODE_BUTTON_L2))
    }

    @Test
    fun anAnalogTriggerUsesItsAxisAndIgnoresItsButtonFromThenOn() {
        val c = ExternalController()
        c.applyAnalogTriggers(0f, 0.4f)
        assertTrue(c.hasAnalogTrigger(KeyEvent.KEYCODE_BUTTON_R2))
        assertEquals(0.4f, c.state.triggerR, 0.0001f)
        // the digital press some controllers send alongside the axis doesn't jump it to a full press
        c.updateStateFromKeyEvent(key(KeyEvent.KEYCODE_BUTTON_R2, true))
        assertEquals(0.4f, c.state.triggerR, 0.0001f)
        c.applyAnalogTriggers(0f, 0f)
        assertEquals(0f, c.state.triggerR, 0f)
        assertFalse(c.state.isPressed(ExternalController.IDX_BUTTON_R2.toInt()))
    }

    @Test
    fun eachTriggerIsDetectedOnItsOwn() {
        val c = ExternalController()
        c.applyAnalogTriggers(0f, 0.7f)
        assertFalse(c.hasAnalogTrigger(KeyEvent.KEYCODE_BUTTON_L2))
        c.updateStateFromKeyEvent(key(KeyEvent.KEYCODE_BUTTON_L2, true))
        assertEquals(1f, c.state.triggerL, 0f)
    }
}
