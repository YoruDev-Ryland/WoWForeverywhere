package app.gamenative.wow.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R

/**
 * Colours from the WoW Forever logo: the slate stone of the crest, parchment lettering, the bronze rim
 * and the teal gem. Bronze ([Brass]) marks focus and the action you can take; the gem ([Gem]) appears
 * only on the "made for Forever" mark. Art carries the colour; the chrome stays quiet.
 * Rules (spec §5.2a): no gradients, no blue/purple accents, no accent-bar cards, no emoji.
 * Status colours ([Ok], [Warn]) carry state only and are never decorative.
 */
object Fw {
    val Ground = Color(0xFF10171A)
    val Stone = Color(0xFF19232A)
    val Stone2 = Color(0xFF24323A)
    val Rule = Color(0xFF30414A)
    val Bone = Color(0xFFEFE6D6)
    val Ash = Color(0xFFA8A08F)
    val Brass = Color(0xFFC9A06A)
    val BrassInk = Color(0xFF1D150B)
    val Gem = Color(0xFF3FA7C2)
    val GemInk = Color(0xFF06222B)

    /** Flat translucent band under text that sits on art. */
    val Scrim = Color(0xBD0C1215)
    val Dim = Color(0x99050708)
    val Ok = Color(0xFF8CC08E)
    val Warn = Color(0xFFE8925A)

    val Shape = RoundedCornerShape(10.dp)
    val TileShape = RoundedCornerShape(16.dp)
    val Pill = RoundedCornerShape(50)

    @OptIn(ExperimentalTextApi::class)
    private fun variable(res: Int, weight: Int) =
        Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

    val Display = FontFamily(variable(R.font.fw_sora, 600), variable(R.font.fw_sora, 700))
    val Body = FontFamily(
        variable(R.font.fw_manrope, 400),
        variable(R.font.fw_manrope, 600),
        variable(R.font.fw_manrope, 700),
        variable(R.font.fw_manrope, 800),
    )

    val Hero = TextStyle(fontFamily = Display, fontWeight = FontWeight(700), fontSize = 44.sp, lineHeight = 46.sp, color = Bone)
    val Title = TextStyle(fontFamily = Display, fontWeight = FontWeight(600), fontSize = 24.sp, lineHeight = 28.sp, color = Bone)
    val Text = TextStyle(fontFamily = Body, fontSize = 15.sp, lineHeight = 22.sp, color = Bone)
    val Secondary = Text.copy(color = Ash, fontSize = 13.sp, lineHeight = 18.sp)

    /** Versions, sizes, paths: body face with tabular figures. */
    val Data = TextStyle(fontFamily = Body, fontWeight = FontWeight(600), fontSize = 12.sp, lineHeight = 16.sp, color = Ash, fontFeatureSettings = "tnum")
    val Label = TextStyle(fontFamily = Body, fontWeight = FontWeight(800), fontSize = 14.sp, letterSpacing = 0.6.sp)
}

@Composable
fun ForeverywhereTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ForeverywhereColors, typography = ForeverywhereTypography, content = content)
}

val ForeverywhereColors = darkColorScheme(
    primary = Fw.Brass,
    onPrimary = Fw.BrassInk,
    primaryContainer = Fw.Stone2,
    onPrimaryContainer = Fw.Bone,
    secondary = Fw.Ash,
    onSecondary = Fw.Ground,
    secondaryContainer = Fw.Stone2,
    onSecondaryContainer = Fw.Bone,
    tertiary = Fw.Gem,
    onTertiary = Fw.GemInk,
    background = Fw.Ground,
    onBackground = Fw.Bone,
    surface = Fw.Stone,
    onSurface = Fw.Bone,
    surfaceVariant = Fw.Stone2,
    onSurfaceVariant = Fw.Ash,
    surfaceContainer = Fw.Stone,
    surfaceContainerHigh = Fw.Stone2,
    surfaceContainerHighest = Fw.Stone2,
    surfaceContainerLow = Fw.Stone,
    surfaceContainerLowest = Fw.Ground,
    outline = Fw.Rule,
    outlineVariant = Fw.Rule,
    error = Fw.Warn,
    onError = Fw.BrassInk,
)

val ForeverywhereTypography = Typography(
    displayLarge = Fw.Hero,
    headlineLarge = Fw.Title.copy(fontSize = 30.sp),
    headlineMedium = Fw.Title,
    headlineSmall = Fw.Title.copy(fontSize = 20.sp),
    titleLarge = Fw.Title,
    titleMedium = Fw.Title.copy(fontSize = 18.sp),
    titleSmall = Fw.Title.copy(fontSize = 16.sp),
    bodyLarge = Fw.Text,
    bodyMedium = Fw.Text,
    bodySmall = Fw.Secondary,
    labelLarge = Fw.Label,
    labelMedium = Fw.Label.copy(fontSize = 12.sp),
    labelSmall = Fw.Label.copy(fontSize = 11.sp),
)
