package app.gamenative.wow.debug

import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import com.winlator.inputcontrols.ExternalController
import com.winlator.xserver.Keyboard

/**
 * Where a button press goes once the game is running. Mirrors the in-game key path (XServerScreen's
 * key handler, then MainActivity): controllers first, then the keyboard, then Back opens the game menu.
 */
object InputRouting {
    enum class Route { CONTROLLER, KEYBOARD, GAME_MENU, IGNORED }

    data class Result(val route: Route, val detail: String)

    private val CONTROLLER_BUTTONS = mapOf(
        KeyEvent.KEYCODE_BUTTON_A to "A",
        KeyEvent.KEYCODE_BUTTON_B to "B",
        KeyEvent.KEYCODE_BUTTON_X to "X",
        KeyEvent.KEYCODE_BUTTON_Y to "Y",
        KeyEvent.KEYCODE_BUTTON_L1 to "L1",
        KeyEvent.KEYCODE_BUTTON_R1 to "R1",
        KeyEvent.KEYCODE_BUTTON_L2 to "L2",
        KeyEvent.KEYCODE_BUTTON_R2 to "R2",
        KeyEvent.KEYCODE_BUTTON_THUMBL to "L3",
        KeyEvent.KEYCODE_BUTTON_THUMBR to "R3",
        KeyEvent.KEYCODE_BUTTON_START to "Start",
        KeyEvent.KEYCODE_BUTTON_SELECT to "Select",
        KeyEvent.KEYCODE_DPAD_UP to "D-pad up",
        KeyEvent.KEYCODE_DPAD_DOWN to "D-pad down",
        KeyEvent.KEYCODE_DPAD_LEFT to "D-pad left",
        KeyEvent.KEYCODE_DPAD_RIGHT to "D-pad right",
    )

    fun route(isController: Boolean, isKeyboard: Boolean, keyCode: Int, hasKeyMapping: (Int) -> Boolean = Keyboard::hasKeyMapping): Result {
        if (isController) CONTROLLER_BUTTONS[keyCode]?.let { return Result(Route.CONTROLLER, "the controller's $it button") }
        if (isKeyboard && hasKeyMapping(keyCode)) return Result(Route.KEYBOARD, "a keyboard key")
        if (keyCode == KeyEvent.KEYCODE_BACK) return Result(Route.GAME_MENU, "opens the in-game menu")
        return Result(Route.IGNORED, "nothing. This press doesn't reach the game.")
    }

    fun route(device: InputDevice?, keyCode: Int): Result =
        route(ExternalController.isGameController(device), Keyboard.isKeyboardDevice(device), keyCode)

    /** One line per input device, for the test screen and the debug report. */
    fun describe(device: InputDevice): String {
        val kinds = buildList {
            if (ExternalController.isGameController(device)) add("controller")
            if (Keyboard.isKeyboardDevice(device)) add("keyboard")
            if (device.isVirtual) add("virtual")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && device.isExternal) add("external")
        }.ifEmpty { listOf("not used by the game") }
        return "${device.name} (id ${device.id}, %04x:%04x, sources 0x%x): %s".format(
            device.vendorId, device.productId, device.sources, kinds.joinToString(", "),
        )
    }

    fun devices(): List<InputDevice> = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
}
