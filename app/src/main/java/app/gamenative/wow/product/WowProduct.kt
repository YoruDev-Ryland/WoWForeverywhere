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
    FOREVER("wowf", "_forever_", "Wow-arm64.exe", "Wow.exe", "us", "WoW Forever"),
    BETA("wow_classic_beta", "_classic_beta_", "WowB-ARM64.exe", "WowB.exe", "test", "WoW Forever Beta"),
    ;

    companion object {
        fun fromCode(code: String?): WowProduct? = entries.firstOrNull { it.code == code }
    }
}

/** The product of the active build in [root]'s `.build.info`, or null if missing or not a product we support. */
fun detectProduct(root: File): WowProduct? = WowProduct.fromCode(BuildInfo.read(root)?.active()?.get("Product"))
