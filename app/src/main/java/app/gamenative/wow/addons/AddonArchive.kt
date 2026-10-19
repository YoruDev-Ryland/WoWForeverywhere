package app.gamenative.wow.addons

import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/** Safely installs an addon zip into `Interface/AddOns`. */
object AddonArchive {
    private const val TEMP_PREFIX = ".foreverywhere-extract-"
    private val driveLetter = Regex("^[A-Za-z]:")

    /**
     * Extracts [zip] into [addonsDir] and returns the top-level addon folder names written, sorted.
     *
     * - The zip is fully extracted into a hidden staging dir inside [addonsDir] first (same
     *   filesystem, so the swap is a rename). A corrupt zip leaves existing folders untouched.
     * - Each existing folder is moved aside, the new one renamed in, then the old one deleted. A
     *   failed rename rolls every folder back.
     * - A single wrapper folder with no `.toc` whose children have TOCs is unwrapped.
     * - Entries that are absolute or contain `..` throw `IOException("unsafe path")`.
     */
    fun install(zip: File, addonsDir: File): List<String> {
        addonsDir.mkdirs()
        val staging = File(addonsDir, "$TEMP_PREFIX${System.nanoTime()}")
        if (!staging.mkdirs()) throw IOException("cannot create ${staging.path}")
        try {
            extract(zip, staging)
            val root = unwrap(staging)
            val folders = addonFolders(root)
            if (folders.isEmpty()) throw IOException("no addon folders in ${zip.name}")
            swapIn(folders, addonsDir)
            return folders.map { it.name }.sorted()
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

    private fun addonFolders(root: File): List<File> = childDirs(root)

    private fun swapIn(folders: List<File>, addonsDir: File) {
        val nonce = System.nanoTime()
        val done = mutableListOf<Pair<File, File?>>() // target to its backup
        try {
            for (folder in folders) {
                val target = File(addonsDir, folder.name)
                var backup: File? = null
                if (target.exists()) {
                    backup = File(addonsDir, ".${folder.name}.old-$nonce")
                    if (!target.renameTo(backup)) throw IOException("cannot move aside ${target.name}")
                }
                done += target to backup
                if (!folder.renameTo(target)) throw IOException("cannot move ${folder.name} into place")
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
