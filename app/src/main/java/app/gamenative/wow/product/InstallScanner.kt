package app.gamenative.wow.product

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class GameInstall(val root: String, val product: WowProduct, val version: String)

/** Finds existing WoW Forever installs on the device's storage volumes. */
object InstallScanner {

    fun scan(storageRoots: List<File>, maxDepth: Int = 3): List<GameInstall> {
        val found = mutableListOf<GameInstall>()
        val visited = mutableSetOf<String>()

        fun visit(dir: File, depth: Int) {
            val canonical = runCatching { dir.canonicalPath }.getOrNull() ?: return
            if (!visited.add(canonical)) return
            asInstall(dir)?.let {
                found += it
                return
            }
            if (depth >= maxDepth) return
            dir.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") && it.name != "Android" }
                ?.sortedBy { it.name }
                ?.forEach { visit(it, depth + 1) }
        }

        storageRoots.forEach { visit(it, 0) }
        return found
    }

    private fun asInstall(dir: File): GameInstall? {
        val info = BuildInfo.read(dir) ?: return null
        val product = info.products().firstOrNull() ?: return null
        if (File(dir, "Data").listFiles().isNullOrEmpty()) return null
        return GameInstall(dir.absolutePath, product, info.active(product)?.get("Version").orEmpty())
    }

    /** Internal shared storage plus every mounted removable volume (SD cards, USB drives). */
    fun storageRoots(context: Context): List<File> {
        val roots = linkedSetOf(Environment.getExternalStorageDirectory())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.getSystemService(StorageManager::class.java).storageVolumes
                .filter { it.state == Environment.MEDIA_MOUNTED }
                .mapNotNullTo(roots) { it.directory }
        } else {
            context.getExternalFilesDirs(null).filterNotNull().mapNotNullTo(roots) { appDir ->
                generateSequence(appDir) { it.parentFile }.firstOrNull { it.parentFile?.name == "storage" }
            }
        }
        return roots.toList()
    }
}
