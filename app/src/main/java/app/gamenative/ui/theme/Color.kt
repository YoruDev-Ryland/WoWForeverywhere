package app.gamenative.ui.theme

import androidx.compose.ui.graphics.Color
import com.alorma.compose.settings.ui.base.internal.SettingsTileColors
import com.alorma.compose.settings.ui.base.internal.SettingsTileDefaults

// Foreverywhere: upstream tokens remapped to the WoW Forever palette (see wow/ui/theme/ForeverywhereTheme.kt)
// Your custom color scheme
val customBackground = Color(0xFF10171A)
val customForeground = Color(0xFFEFE6D6)
val customCard = Color(0xFF19232A)
val customCardForeground = Color(0xFFEFE6D6)
val customPrimary = Color(0xFFC9A06A)
val customPrimaryForeground = Color(0xFF1D150B)
val customSecondary = Color(0xFF24323A)
val customSecondaryForeground = Color(0xFFEFE6D6)
val customMuted = Color(0xFF24323A)
val customMutedForeground = Color(0xFFA8A08F)
val customAccent = Color(0xFF3FA7C2)
val customAccentForeground = Color(0xFF06222B)
val customDestructive = Color(0xFF7F1D1D)

val pluviaSeedColor = Color(0x284561FF)

/**
 * Raw color primitives for the Pluvia app.
 * These are the base colors used to construct theme palettes.
 */

// Brand
val PluviaPrimary = Color(0xFFC9A06A)
val PluviaSeed = Color(0x284561FF)

// Backgrounds
val PluviaBackground = Color(0xFF10171A)
val PluviaSurface = Color(0xFF19232A)
val PluviaSurfaceElevated = Color(0xFF24323A)
val PluviaCard = Color(0xFF19232A)

// Foregrounds
val PluviaForeground = Color(0xFFEFE6D6)
val PluviaForegroundMuted = Color(0xFFA8A08F)

// Secondary
val PluviaSecondary = Color(0xFF24323A)

// Accents
val PluviaCyan = Color(0xFF3FA7C2)
val PluviaPurple = Color(0xFFC9A06A)
val PluviaPink = Color(0xFFC9A06A)

// Semantic
val PluviaSuccess = Color(0xFF10B981)
val PluviaWarning = Color(0xFFF59E0B)
val PluviaDanger = Color(0xFFEF4444)
val PluviaDestructive = Color(0xFF7F1D1D)

// Border
val PluviaBorder = Color(0xFF30414A)

// Status - Installed/Download states
val StatusInstalled = Color(0xFF4CAF50)
val StatusDownloading = Color(0xFF00BCD4)
val StatusAvailable = Color(0xFF2196F3)
val StatusAway = Color(0xFFFF9800)
val StatusOffline = Color(0xFF9E9E9E)

// Friend states
val FriendOnline = Color(0xFF6DCFF6)
val FriendOffline = Color(0xFF7A7A7A)
val FriendInGame = Color(0xFF90BA3C)
val FriendAwayOrSnooze = Color(0x806DCFF6)
val FriendInGameAwayOrSnooze = Color(0x8090BA3C)
val FriendBlocked = Color(0xFF983D3D)

// WoW Forever
val WowBackgroundGradient = listOf(Color(0xFF10171A), Color(0xFF10171A)) // flat: Foreverywhere has no gradients
val WowGold = Color(0xFFC9A06A)
val WowBronze = Color(0xFFC9A06A)
val WowMuted = Color(0xFFA8A08F)
val WowSubtle = Color(0xFFA8A08F)
val WowError = Color(0xFFE8925A)

// Compatibility
val CompatibilityGood = Color(0xFF4CAF50)
val CompatibilityGoodBg = Color(0xFF1B5E20)
val CompatibilityPartial = Color(0xFF8BC34A)
val CompatibilityPartialBg = Color(0xFF33691E)
val CompatibilityUnknown = Color(0xFF9E9E9E)
val CompatibilityUnknownBg = Color(0xFF424242)
val CompatibilityBad = Color(0xFFEF5350)
val CompatibilityBadBg = Color(0xFFB71C1C)
