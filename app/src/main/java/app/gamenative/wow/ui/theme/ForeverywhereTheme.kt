package app.gamenative.wow.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.gamenative.R

/**
 * "Brass on stone": flat olive-black surfaces, bone text, one brass accent for focus and primary actions.
 * Rules (spec §5.2a): no gradients, no blue/purple, no accent-bar cards, no emoji, corners 2dp at most.
 * Status colours ([Ok], [Warn]) carry state only and are never decorative.
 */
object Fw {
    val Ground = Color(0xFF14150F)
    val Stone = Color(0xFF1F2118)
    val Stone2 = Color(0xFF292C20)
    val Rule = Color(0xFF3A3D2E)
    val Bone = Color(0xFFE6E0CF)
    val Ash = Color(0xFFA39E8C)
    val Brass = Color(0xFFD39B3F)
    val BrassInk = Color(0xFF1A1405)
    val Ok = Color(0xFF8FB26A)
    val Warn = Color(0xFFD9774A)

    val Shape = RoundedCornerShape(2.dp)

    val Display = FontFamily(Font(R.font.fw_fell_sc))
    val Body = FontFamily(Font(R.font.fw_atkinson_regular), Font(R.font.fw_atkinson_bold, FontWeight.Bold))
    val Mono = FontFamily(Font(R.font.fw_plex_mono_regular), Font(R.font.fw_plex_mono_medium, FontWeight.Medium))

    val Title = TextStyle(fontFamily = Display, fontSize = 28.sp, lineHeight = 30.sp, color = Bone)
    val Hero = TextStyle(fontFamily = Display, fontSize = 52.sp, lineHeight = 52.sp, color = Bone)
    val Text = TextStyle(fontFamily = Body, fontSize = 15.sp, lineHeight = 22.sp, color = Bone)
    val Secondary = Text.copy(color = Ash, fontSize = 13.sp, lineHeight = 18.sp)
    val Data = TextStyle(fontFamily = Mono, fontSize = 12.sp, lineHeight = 16.sp, color = Ash)
    val Label = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.5.sp)
}

/** The app-wide focus state: a thick brass outline, visible at arm's length on a handheld. */
fun Modifier.focusRing(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .border(if (focused) 3.dp else 0.dp, if (focused) Fw.Brass else Color.Transparent, Fw.Shape)
}

@Composable
fun ForeverywhereTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Fw.Brass,
            onPrimary = Fw.BrassInk,
            secondary = Fw.Ash,
            background = Fw.Ground,
            onBackground = Fw.Bone,
            surface = Fw.Stone,
            onSurface = Fw.Bone,
            surfaceVariant = Fw.Stone2,
            onSurfaceVariant = Fw.Ash,
            outline = Fw.Rule,
            error = Fw.Warn,
        ),
        typography = Typography(
            bodyLarge = Fw.Text,
            bodyMedium = Fw.Text,
            bodySmall = Fw.Secondary,
            titleLarge = Fw.Title,
            labelLarge = Fw.Label,
        ),
        content = content,
    )
}
