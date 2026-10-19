package app.gamenative.wow.addons

import java.io.File
import kotlinx.serialization.Serializable

/**
 * The `## Interface` number of WoW Forever (patch 1.60.1). Unverified guess, see spec §5.7:
 * confirm it from a live client install.
 */
const val FOREVER_INTERFACE = 16001

/** Interface numbers treated as Forever: the whole 1.60.x patch line of [FOREVER_INTERFACE]. */
val FOREVER_INTERFACE_RANGE = (FOREVER_INTERFACE / 100 * 100)..(FOREVER_INTERFACE / 100 * 100 + 99)

@Serializable
enum class AddonFlavor { FOREVER, CLASSIC, OTHER }

object FlavorRules {
    private val foreverSuffixes = listOf("-Forever", "_Forever", "-Camelot", "_Camelot")
    private val classicSuffixes = listOf("-Classic", "_Classic", "-Vanilla", "_Vanilla")

    /** Flavor implied by a TOC file name suffix, or null when it has none of the known suffixes. */
    fun ofTocFileName(name: String): AddonFlavor? {
        val base = name.removeSuffix(".toc").removeSuffix(".TOC")
        return when {
            foreverSuffixes.any { base.endsWith(it, ignoreCase = true) } -> AddonFlavor.FOREVER
            classicSuffixes.any { base.endsWith(it, ignoreCase = true) } -> AddonFlavor.CLASSIC
            else -> null
        }
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

    /** The TOC the Forever client would load: a Forever TOC, else plain `<Folder>.toc`, else a Classic TOC. */
    fun pickToc(folder: File): Toc? {
        val files = tocFiles(folder)
        val plainName = "${folder.name}.toc"
        val chosen = files.firstOrNull { ofTocFileName(it.name) == AddonFlavor.FOREVER }
            ?: files.firstOrNull { it.name.equals(plainName, ignoreCase = true) }
            ?: files.firstOrNull { ofTocFileName(it.name) == AddonFlavor.CLASSIC }
            ?: return null
        return runCatching { TocParser.parse(folder.name, chosen.name, chosen.readText()) }.getOrNull()
    }

    /**
     * Flavor of an addon from all of its TOCs. Forever evidence (suffix or interface) wins, then
     * Classic evidence (suffix or an `11xxx` interface), else [AddonFlavor.OTHER].
     */
    fun classify(tocs: List<Toc>): AddonFlavor {
        val suffixes = tocs.map { ofTocFileName(it.fileName) }
        val unsuffixed = tocs.filter { ofTocFileName(it.fileName) == null }
        val interfaces = unsuffixed.flatMap { it.interfaces }
        return when {
            AddonFlavor.FOREVER in suffixes || interfaces.any { it in FOREVER_INTERFACE_RANGE } -> AddonFlavor.FOREVER
            AddonFlavor.CLASSIC in suffixes || interfaces.any { it in 10000..15999 } -> AddonFlavor.CLASSIC
            else -> AddonFlavor.OTHER
        }
    }
}
