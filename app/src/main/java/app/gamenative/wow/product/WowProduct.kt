package app.gamenative.wow.product

import java.io.File
import java.time.LocalDate

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
    /** When the servers open. The patch service lists the launch product earlier than this. */
    val releaseDate: LocalDate? = null,
) {
    FOREVER("wowf", "_forever_", "Wow-arm64.exe", "Wow.exe", "us", "WoW Forever", LocalDate.of(2026, 11, 4)),
    BETA("wow_classic_beta", "_classic_beta_", "WowB-ARM64.exe", "WowB.exe", "test", "WoW Forever Beta"),
    ;

    companion object {
        fun fromCode(code: String?): WowProduct? = entries.firstOrNull { it.code == code }
    }
}

/** The preferred supported product installed in [root] (launch product before beta), or null if none. */
fun detectProduct(root: File): WowProduct? = BuildInfo.read(root)?.products()?.firstOrNull()
