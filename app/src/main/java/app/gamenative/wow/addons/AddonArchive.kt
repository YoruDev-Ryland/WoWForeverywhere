package app.gamenative.wow.addons

import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/** Safely installs an addon zip into `Interface/AddOns`. */
object AddonArchive {
    private const val TEMP_PREFIX = ".foreverywhere-extract-"
    private val driveLetter = Regex("^[A-Za-z]:")
    private val flavorSuffix = Regex("[_-](Mainline|Classic|Vanilla|TBC|BCC|Wrath|WOTLKC|Cata|Mists|Standard)$", RegexOption.IGNORE_CASE)

    /**
     * Extracts [zip] into [addonsDir] and returns the top-level addon folder names written, sorted.
     *
     * - The zip is fully extracted into a hidden staging dir inside [addonsDir] first (same
     *   filesystem, so the swap is a rename). A corrupt zip leaves existing folders untouched.
     * - Each existing folder is moved aside, the new one renamed in, then the old one deleted. A
     *   failed rename rolls every folder back.
     * - A single wrapper folder with no `.toc` whose children have TOCs is unwrapped.
     * - WoW only loads an addon from a folder named like its `.toc`, so a folder whose name matches none of its TOCs
     *   (GitHub's "Download ZIP" gives "<repo>-main") is installed under the TOC's name, and TOCs at the zip's root
     *   make the whole zip one addon named after them.
     * - Mac metadata (`__MACOSX/`, `._` files) is skipped.
     * - Entries that are absolute or contain `..` throw `IOException("unsafe path")`.
     */
    fun install(zip: File, addonsDir: File): List<String> {
        addonsDir.mkdirs()
        val staging = File(addonsDir, "$TEMP_PREFIX${System.nanoTime()}")
        if (!staging.mkdirs()) throw IOException("cannot create ${staging.path}")
        try {
            val content = File(staging, "content")
            extract(zip, content)
            val root = unwrap(content)
            val folders = if (hasToc(root)) listOf(root to tocName(root)!!) else childDirs(root).map { it to (tocName(it) ?: it.name) }
            if (folders.isEmpty()) throw IOException("no addon folders in ${zip.name}")
            if (folders.map { it.second.lowercase() }.distinct().size != folders.size) throw IOException("two addons named alike in ${zip.name}")
            swapIn(folders, addonsDir)
            return folders.map { it.second }.sorted()
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun extract(zip: File, dest: File) {
        val destPath = dest.canonicalFile.toPath()
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val name = safeName(entry.name)
                if (name.split('/').let { parts -> parts.first() == "__MACOSX" || parts.last().startsWith("._") }) continue
                val target = File(dest, name)
                if (!target.canonicalFile.toPath().startsWith(destPath)) throw IOException("unsafe path")
                if (entry.isDirectory || entry.name.endsWith("/") || entry.name.endsWith("\\")) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { input.copyTo(it) }
                }
            }
        }
    }

    /** Normalizes separators and rejects absolute paths and `..` segments. */
    private fun safeName(raw: String): String {
        val name = raw.replace('\\', '/')
        val segments = name.split('/')
        if (name.startsWith("/") || driveLetter.containsMatchIn(name) || segments.any { it == ".." }) {
            throw IOException("unsafe path")
        }
        return segments.filter { it.isNotEmpty() && it != "." }.joinToString("/")
    }

    private fun childDirs(dir: File): List<File> = dir.listFiles()
        .orEmpty()
        .filter { it.isDirectory && !it.name.startsWith(".") && it.name != "__MACOSX" }

    private fun hasToc(dir: File): Boolean = dir.listFiles().orEmpty().any { it.isFile && it.name.endsWith(".toc", ignoreCase = true) }

    private fun unwrap(staging: File): File {
        val top = childDirs(staging)
        val only = top.singleOrNull() ?: return staging
        return if (!hasToc(only) && childDirs(only).any(::hasToc)) only else staging
    }

    /**
     * The folder name WoW needs for the addon in [dir]: [dir]'s own name when one of its TOCs is named after it
     * ("Details" holding Details_Mainline.toc), otherwise its TOC's name without a flavor suffix. Null without a TOC.
     */
    private fun tocName(dir: File): String? {
        val tocs = dir.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".toc", ignoreCase = true) && !it.name.startsWith(".") }
            .map { it.name.substringBeforeLast('.') }
            .sorted()
        if (tocs.isEmpty()) return null
        val bases = tocs.map { it.replace(flavorSuffix, "") }
        if (bases.any { it.equals(dir.name, ignoreCase = true) }) return dir.name
        return bases.groupingBy { it }.eachCount().maxBy { it.value }.key.takeIf(::plainName)
            ?: throw IOException("unsafe path")
    }

    private fun plainName(name: String) = name.isNotBlank() && name != "." && name != ".." && '/' !in name && '\\' !in name

    private fun swapIn(folders: List<Pair<File, String>>, addonsDir: File) {
        val nonce = System.nanoTime()
        val done = mutableListOf<Pair<File, File?>>() // target to its backup
        try {
            for ((folder, name) in folders) {
                val target = File(addonsDir, name)
                var backup: File? = null
                if (target.exists()) {
                    backup = File(addonsDir, ".$name.old-$nonce")
                    if (!target.renameTo(backup)) throw IOException("cannot move aside ${target.name}")
                }
                done += target to backup
                if (!folder.renameTo(target)) throw IOException("cannot move $name into place")
            }
        } catch (e: IOException) {
            for ((target, backup) in done.asReversed()) {
                if (backup != null) {
                    target.deleteRecursively()
                    backup.renameTo(target)
                } else {
                    target.deleteRecursively()
                }
            }
            throw e
        }
        done.forEach { (_, backup) -> backup?.deleteRecursively() }
    }
}
