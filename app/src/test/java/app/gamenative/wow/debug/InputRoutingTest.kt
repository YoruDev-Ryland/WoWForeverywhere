package app.gamenative.wow.debug

import android.view.KeyEvent
import app.gamenative.wow.debug.InputRouting.Route
import org.junit.Assert.assertEquals
import org.junit.Test

class InputRoutingTest {

    private val noKeys: (Int) -> Boolean = { false }

    @Test
    fun controllerButtonsGoToTheController() {
        val result = InputRouting.route(isController = true, isKeyboard = false, keyCode = KeyEvent.KEYCODE_BUTTON_SELECT, hasKeyMapping = noKeys)
        assertEquals(Route.CONTROLLER, result.route)
        assertEquals("the controller's Select button", result.detail)
    }

    @Test
    fun aControllerButtonFromADeviceThatIsNotAControllerIsLost() {
        // Issue #6: Select arriving from a device Android doesn't list as a controller.
        assertEquals(Route.IGNORED, InputRouting.route(false, false, KeyEvent.KEYCODE_BUTTON_SELECT, noKeys).route)
        assertEquals(Route.IGNORED, InputRouting.route(false, true, KeyEvent.KEYCODE_BUTTON_SELECT, noKeys).route)
    }

    @Test
    fun keyboardKeysNeedAMapping() {
        assertEquals(Route.KEYBOARD, InputRouting.route(false, true, KeyEvent.KEYCODE_A) { it == KeyEvent.KEYCODE_A }.route)
        assertEquals(Route.IGNORED, InputRouting.route(false, false, KeyEvent.KEYCODE_A) { true }.route)
    }

    @Test
    fun backOpensTheGameMenu() {
        assertEquals(Route.GAME_MENU, InputRouting.route(true, false, KeyEvent.KEYCODE_BACK, noKeys).route)
    }
}
