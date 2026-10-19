package app.gamenative.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import app.gamenative.wow.ui.components.ArtImage
import app.gamenative.wow.ui.components.ForeverArt
import app.gamenative.wow.ui.components.FwProgress
import app.gamenative.wow.ui.theme.Fw

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.PluviaApp
import app.gamenative.R
import app.gamenative.events.AndroidEvent
import app.gamenative.ui.theme.BrandGradient
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.ui.theme.WowBackgroundGradient
import app.gamenative.ui.theme.WowGold

@Composable
fun BootingSplash(
    visible: Boolean = true,
    text: String = "Initializing...",
    progress: Float = -1f,
    onAbort: (() -> Unit)? = null,
) {
    if (visible && onAbort != null) {
        DisposableEffect(visible) {
            var start = false
            var select = false
            var l2 = false
            var r2 = false
            var aborted = false

            fun check(): Boolean = (!aborted && start && select && l2 && r2).also {
                if (it) {
                    aborted = true
                    onAbort()
                }
            }

            val keyHandler: (AndroidEvent.KeyEvent) -> Boolean = keyHandler@ { event ->
                val down = event.event.action == KeyEvent.ACTION_DOWN
                when (event.event.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_START -> start = down
                    KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BACK -> select = down
                    KeyEvent.KEYCODE_BUTTON_L2 -> l2 = down
                    KeyEvent.KEYCODE_BUTTON_R2 -> r2 = down
                    else -> return@keyHandler false
                }
                check() || (down && (start || select || l2 || r2))
            }

            val motionHandler: (AndroidEvent.MotionEvent) -> Boolean = { event ->
                event.event?.let { me ->
                    l2 = maxOf(me.getAxisValue(MotionEvent.AXIS_LTRIGGER), me.getAxisValue(MotionEvent.AXIS_BRAKE)) >= 0.3f
                    r2 = maxOf(me.getAxisValue(MotionEvent.AXIS_RTRIGGER), me.getAxisValue(MotionEvent.AXIS_GAS)) >= 0.3f
                    check()
                } ?: false
            }

            PluviaApp.events.on<AndroidEvent.KeyEvent, Boolean>(keyHandler)
            PluviaApp.events.on<AndroidEvent.MotionEvent, Boolean>(motionHandler)
            onDispose {
                PluviaApp.events.off<AndroidEvent.KeyEvent, Boolean>(keyHandler)
                PluviaApp.events.off<AndroidEvent.MotionEvent, Boolean>(motionHandler)
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 400)),
        exit = fadeOut(animationSpec = tween(durationMillis = 300)),
    ) {
        // Foreverywhere look: full-bleed WoW Forever key art, status on a flat slate band.
        Box(modifier = Modifier.fillMaxSize().background(Fw.Ground)) {
            ArtImage(ForeverArt.KEY_ART, Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Fw.Scrim)
                    .padding(horizontal = 40.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(text = "Starting World of Warcraft", style = Fw.Title.copy(fontSize = 28.sp, lineHeight = 32.sp))
                FwProgress(fraction = progress.takeIf { it >= 0f })
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = text, style = Fw.Text.copy(color = Fw.Bone), modifier = Modifier.weight(1f))
                    Text(text = "Start + Select + L2 + R2 returns to setup", style = Fw.Secondary)
                }
            }
            if (onAbort != null) {
                IconButton(
                    onClick = onAbort,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .background(Fw.Scrim, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Fw.Bone,
                    )
                }
            }
        }
    }
}
