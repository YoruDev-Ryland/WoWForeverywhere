package app.gamenative.wow.product

import java.io.File

/**
 * The Blizzard products this app can run. Each product installs into its own flavor folder inside the
 * game root, while `Data/` is shared between them (both use the same CDN archives).
 */
enum class WowProduct(
    val code: String,
    val flavorDir: String,
    val armExe: String,
    val x64Exe: String,
    val portal: String,
    val displayName: String,
) {
    FOREVER("wowf", "_forever_", "Wow-arm64.exe", "Wow.exe", "US", "WoW Forever"),
    BETA("wow_classic_beta", "_classic_beta_", "WowB-ARM64.exe", "WowB.exe", "test", "WoW Forever Beta"),

    // Other official versions, selectable in Settings (see GameVersion). Experimental.
    CLASSIC_ERA("wow_classic_era", "_classic_era_", "WowClassic-arm64.exe", "WowClassic.exe", "US", "Classic Era"),
    ANNIVERSARY("wow_anniversary", "_anniversary_", "WowClassic-arm64.exe", "WowClassic.exe", "US", "Burning Crusade Anniversary"),
    MISTS("wow_classic", "_classic_", "WowClassic-arm64.exe", "WowClassic.exe", "US", "Mists of Pandaria Classic"),
    RETAIL("wow", "_retail_", "Wow-ARM64.exe", "Wow.exe", "US", "Retail"),
    ;

    companion object {
        fun fromCode(code: String?): WowProduct? = entries.firstOrNull { it.code == code }
    }
}

/**
 * The preferred product of the selected [GameVersion] installed in [root] (see [BuildInfo.products]),
 * or null if none.
 */
fun detectProduct(root: File, track: ForeverTrack = ForeverTracks.selected): WowProduct? = BuildInfo.read(root)?.products(track)?.firstOrNull()
