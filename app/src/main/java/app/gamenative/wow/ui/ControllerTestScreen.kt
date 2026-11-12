package app.gamenative.wow.ui

import androidx.compose.ui.res.stringResource
import app.gamenative.R
import android.content.Intent
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.PluviaApp
import app.gamenative.events.AndroidEvent
import app.gamenative.wow.debug.ControllerTestLog
import app.gamenative.wow.debug.InputRouting
import app.gamenative.wow.ui.components.FwButton
import app.gamenative.wow.ui.components.FwButtonKind
import app.gamenative.wow.ui.theme.Fw
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HOLD_TO_LEAVE_MS = 2_000L

/**
 * Settings, Controller test: every button press, the device it came from, and what the game would do
 * with it. Takes all button input while open, so B can be tested too; holding B (or tapping Back) leaves.
 */
@Composable
fun ControllerTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val leave by rememberUpdatedState(onBack)
    var sticks by remember { mutableStateOf<String?>(null) }
    var devices by remember { mutableStateOf(InputRouting.devices().map(InputRouting::describe)) }

    DisposableEffect(Unit) {
        var holdJob: Job? = null
        val onKey: (AndroidEvent.KeyEvent) -> Boolean = { e ->
            val event = e.event
            val isLeaveKey = event.keyCode == KeyEvent.KEYCODE_BUTTON_B || event.keyCode == KeyEvent.KEYCODE_BACK
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                ControllerTestLog.add(event)
                devices = InputRouting.devices().map(InputRouting::describe)
                if (isLeaveKey) {
                    holdJob?.cancel()
                    holdJob = scope.launch {
                        delay(HOLD_TO_LEAVE_MS)
                        leave()
                    }
                }
            } else if (event.action == KeyEvent.ACTION_UP && isLeaveKey) {
                holdJob?.cancel()
            }
            true
        }
        val onMotion: (AndroidEvent.MotionEvent) -> Boolean = { e ->
            e.event?.let { sticks = describeAxes(it) }
            true
        }
        PluviaApp.events.on<AndroidEvent.KeyEvent, Boolean>(onKey)
        PluviaApp.events.on<AndroidEvent.MotionEvent, Boolean>(onMotion)
        onDispose {
            holdJob?.cancel()
            PluviaApp.events.off<AndroidEvent.KeyEvent, Boolean>(onKey)
            PluviaApp.events.off<AndroidEvent.MotionEvent, Boolean>(onMotion)
        }
    }

    FwPage(stringResource(R.string.fw_controller_test), onBack) {
        FwSection(stringResource(R.string.fw_press_any_button)) {
            Text(
                stringResource(R.string.fw_controller_test_explanation),
                style = Fw.Secondary,
            )
            Text(sticks ?: stringResource(R.string.fw_move_a_stick_or_trigger_to_see), style = Fw.Data)
        }
        FwSection(stringResource(R.string.fw_presses)) {
            val presses = ControllerTestLog.presses
            if (presses.isEmpty()) {
                Text(stringResource(R.string.fw_no_presses_yet), style = Fw.Secondary)
            } else {
                presses.forEach { PressRow(it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FwButton(stringResource(R.string.fw_share_results), {
                    val text = ControllerTestLog.report(devices)
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, "Share controller test"))
                }, kind = FwButtonKind.Ghost, enabled = presses.isNotEmpty())
                FwButton(stringResource(R.string.fw_clear), ControllerTestLog::clear, kind = FwButtonKind.Ghost, enabled = presses.isNotEmpty())
            }
        }
        FwSection(stringResource(R.string.fw_input_devices)) {
            devices.forEach { Text(it, style = Fw.Data) }
        }
    }
}

@Composable
private fun PressRow(press: ControllerTestLog.Press) {
    val color = when (press.result.route) {
        InputRouting.Route.CONTROLLER, InputRouting.Route.KEYBOARD -> Fw.Ok
        InputRouting.Route.GAME_MENU -> Fw.Brass
        InputRouting.Route.IGNORED -> Fw.Warn
    }
    Column(
        Modifier.fillMaxWidth().background(Fw.Stone, Fw.Shape).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(press.key, style = Fw.Label.copy(color = Fw.Bone, fontSize = 15.sp))
        Text(stringResource(R.string.fw_from_1_s, press.device), style = Fw.Secondary)
        Text(stringResource(R.string.fw_in_the_game_1_s, press.result.detail), style = Fw.Data.copy(color = color))
    }
}

private fun describeAxes(event: MotionEvent): String {
    fun f(axis: Int) = "%.2f".format(event.getAxisValue(axis))
    val lt = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
    val rt = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
    return "Left stick ${f(MotionEvent.AXIS_X)}, ${f(MotionEvent.AXIS_Y)} · Right stick ${f(MotionEvent.AXIS_Z)}, ${f(MotionEvent.AXIS_RZ)} · " +
        "L2 ${"%.2f".format(lt)} · R2 ${"%.2f".format(rt)} · D-pad ${f(MotionEvent.AXIS_HAT_X)}, ${f(MotionEvent.AXIS_HAT_Y)}"
}
