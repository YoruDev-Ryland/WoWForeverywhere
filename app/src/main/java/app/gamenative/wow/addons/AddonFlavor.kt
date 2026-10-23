package app.gamenative.wow.addons

import app.gamenative.wow.product.GameVersion
import java.io.File
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The `## Interface` number of WoW Forever (patch 1.60.1). Unverified guess, see spec §5.7:
 * confirm it from a live client install.
 */
const val FOREVER_INTERFACE = 16001

/** Interface numbers treated as Forever: the whole 1.60.x patch line of [FOREVER_INTERFACE]. */
val FOREVER_INTERFACE_RANGE = (FOREVER_INTERFACE / 100 * 100)..(FOREVER_INTERFACE / 100 * 100 + 99)

/**
 * Which game version an addon file is for, relative to the selected [AddonTarget]: [MAIN] is made for
 * it, [FALLBACK] for its fallback (Classic Era, for WoW Forever only). Stored under the names v0.3.0
 * used, when WoW Forever was the only target.
 */
@Serializable
enum class AddonFlavor {
    @SerialName("FOREVER")
    MAIN,

    @SerialName("CLASSIC")
    FALLBACK,
    OTHER,
}

/**
 * How one game version is labelled across addon sources. [names] are the words it goes by in TOC
 * suffixes (`Foo_Mainline.toc`), release metadata and zip names (`foo-1.0-bcc.zip`).
 */
class FlavorSpec(
    val curseForgeTypeId: Int,
    val wagoGameVersion: String,
    val catalogKey: String,
    val names: List<String>,
    val interfaces: IntRange,
    /** Generic TOC suffixes the client also loads, after its own (`_Classic` on every Classic client but Era, which names it already). */
    val extraTocNames: List<String> = emptyList(),
    /** Whether a WoWInterface compatibility entry (`12.1.0`, "Curse of Ulatek") is this version. */
    val wowInterface: (major: Int, minor: Int, name: String) -> Boolean,
) {
    fun hasTocSuffix(base: String, includeExtra: Boolean = true) =
        (if (includeExtra) names + extraTocNames else names).any { base.endsWith("-$it", ignoreCase = true) || base.endsWith("_$it", ignoreCase = true) }

    companion object {
        private fun isForeverName(name: String) = "forever" in name || "camelot" in name

        val FOREVER = FlavorSpec(88568, "forever", "forever", listOf("forever", "camelot"), FOREVER_INTERFACE_RANGE) { major, minor, name ->
            isForeverName(name) || (major == 1 && minor >= 60)
        }
        val CLASSIC_ERA = FlavorSpec(67408, "classic", "classic", listOf("classic", "vanilla"), 10000..15999) { major, minor, name ->
            major == 1 && minor < 60 && !isForeverName(name)
        }
        val ANNIVERSARY = FlavorSpec(73246, "bc", "bcc", listOf("bcc", "tbc"), 20000..29999, listOf("classic")) { major, _, _ -> major == 2 }
        val MISTS = FlavorSpec(79434, "mop", "mists", listOf("mists", "mop"), 50000..59999, listOf("classic")) { major, _, _ -> major == 5 }
        val RETAIL = FlavorSpec(517, "retail", "mainline", listOf("mainline", "retail"), 100000..999999) { major, _, _ -> major >= 10 }
    }
}

/** What the addon browser looks for: the selected version's addons, plus a fallback version's (Forever only). */
data class AddonTarget(val main: FlavorSpec, val fallback: FlavorSpec?) {
    /** [AddonFlavor.MAIN] then [AddonFlavor.FALLBACK], each with its spec. */
    val flavors: List<Pair<AddonFlavor, FlavorSpec>> get() = listOfNotNull(AddonFlavor.MAIN to main, fallback?.let { AddonFlavor.FALLBACK to it })

    companion object {
        val FOREVER = AddonTarget(FlavorSpec.FOREVER, FlavorSpec.CLASSIC_ERA)
    }
}

fun GameVersion.addonTarget(): AddonTarget = when (this) {
    GameVersion.FOREVER -> AddonTarget.FOREVER
    GameVersion.CLASSIC_ERA -> AddonTarget(FlavorSpec.CLASSIC_ERA, null)
    GameVersion.ANNIVERSARY -> AddonTarget(FlavorSpec.ANNIVERSARY, null)
    GameVersion.MISTS -> AddonTarget(FlavorSpec.MISTS, null)
    GameVersion.RETAIL -> AddonTarget(FlavorSpec.RETAIL, null)
}

object FlavorRules {
    /** Flavor implied by a TOC file name suffix, or null when it has none of [target]'s suffixes. */
    fun ofTocFileName(name: String, target: AddonTarget = AddonTarget.FOREVER): AddonFlavor? {
        val base = name.removeSuffix(".toc").removeSuffix(".TOC")
        return target.flavors.firstOrNull { (_, spec) -> spec.hasTocSuffix(base) }?.first
    }

    /** Every `.toc` in [folder] that belongs to the folder (`<Folder>.toc` or `<Folder><suffix>.toc`). */
    fun tocFiles(folder: File): List<File> {
        val name = folder.name
        return folder.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(".toc", ignoreCase = true) && it.name.startsWith(name, ignoreCase = true) }
            .sortedBy { it.name }
    }

    /** Parses every TOC of [folder] (see [tocFiles]). */
    fun readTocs(folder: File): List<Toc> = tocFiles(folder).mapNotNull { file ->
        runCatching { TocParser.parse(folder.name, file.name, file.readText()) }.getOrNull()
    }

    /**
     * The TOC the game would load: one named for [target]'s version, else a generic one it also loads
     * (`_Classic`), else plain `<Folder>.toc`, else a fallback TOC.
     */
    fun pickToc(folder: File, target: AddonTarget = AddonTarget.FOREVER): Toc? {
        val files = tocFiles(folder)
        val plainName = "${folder.name}.toc"
        val chosen = files.firstOrNull { target.main.hasTocSuffix(it.name.removeSuffix(".toc").removeSuffix(".TOC"), includeExtra = false) }
            ?: files.firstOrNull { ofTocFileName(it.name, target) == AddonFlavor.MAIN }
            ?: files.firstOrNull { it.name.equals(plainName, ignoreCase = true) }
            ?: files.firstOrNull { ofTocFileName(it.name, target) == AddonFlavor.FALLBACK }
            ?: return null
        return runCatching { TocParser.parse(folder.name, chosen.name, chosen.readText()) }.getOrNull()
    }

    /**
     * Flavor of an addon from all of its TOCs. Evidence for [target]'s version (suffix or interface)
     * wins, then for its fallback, else [AddonFlavor.OTHER].
     */
    fun classify(tocs: List<Toc>, target: AddonTarget = AddonTarget.FOREVER): AddonFlavor {
        val suffixes = tocs.map { ofTocFileName(it.fileName, target) }
        val interfaces = tocs.filter { ofTocFileName(it.fileName, target) == null }.flatMap { it.interfaces }
        return target.flavors.firstOrNull { (flavor, spec) -> flavor in suffixes || interfaces.any { it in spec.interfaces } }?.first
            ?: AddonFlavor.OTHER
    }
}
