package app.gamenative.wow.debug

import android.view.KeyEvent
import androidx.compose.runtime.mutableStateListOf

/** The controller test's presses, newest first. Kept for the session so a debug report can include them. */
object ControllerTestLog {
    private const val MAX_PRESSES = 40

    data class Press(val key: String, val device: String, val result: InputRouting.Result)

    val presses = mutableStateListOf<Press>()

    fun add(event: KeyEvent) {
        val device = event.device?.let { "${it.name} (id ${it.id})" } ?: "an unknown device (id ${event.deviceId})"
        val key = "${KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")} (scan code ${event.scanCode})"
        presses.add(0, Press(key, device, InputRouting.route(event.device, event.keyCode)))
        while (presses.size > MAX_PRESSES) presses.removeAt(presses.lastIndex)
    }

    fun clear() = presses.clear()

    fun report(devices: List<String>): String = buildString {
        appendLine("Controller test")
        presses.forEach { appendLine("${it.key} from ${it.device}: ${it.result.route}, ${it.result.detail}") }
        appendLine()
        appendLine("Input devices")
        devices.forEach { appendLine(it) }
    }
}
