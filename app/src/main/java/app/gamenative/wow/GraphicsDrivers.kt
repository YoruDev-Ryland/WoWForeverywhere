package app.gamenative.wow

import android.content.Context
import app.gamenative.ui.screen.wow.ADRENO_8XX_DRIVER
import app.gamenative.ui.screen.wow.DEFAULT_DRIVER
import app.gamenative.ui.screen.wow.GpuProfile
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.json.JSONObject
import timber.log.Timber

/**
 * Settings, Graphics driver (#68): the Vulkan driver the game runs on. Automatic keeps the device profile's pick
 * ([GpuProfile.driver]); the player can also pick one of the drivers that ship with the app, the device's own
 * ("System"), or an adrenotools driver zip they added. Added drivers live next to the bundled ones in
 * contents/adrenotools/<name>, where the graphics wrapper loads them from.
 */
object GraphicsDrivers {
    const val SYSTEM = "System"

    /** The drivers the app ships or the device has, in the order Settings lists them. */
    val BUILT_IN = listOf(DEFAULT_DRIVER, ADRENO_8XX_DRIVER, SYSTEM)

    enum class ImportResult { OK, NOT_A_DRIVER, NAME_TAKEN, TOO_LARGE, FAILED }

    data class Driver(val name: String, val version: String, val added: Boolean)

    private const val PREFS = "wow_settings"
    private const val KEY = "graphics_driver"

    /** Real driver zips are 10 to 60 MB unpacked; anything far bigger isn't one. */
    internal const val MAX_UNPACKED_BYTES = 512L * 1024 * 1024
    private const val MAX_ENTRIES = 256
    private val SAFE_NAME = Regex("""[A-Za-z0-9][A-Za-z0-9._+\- ]{0,79}""")

    fun driversDir(context: Context) = File(context.filesDir, "contents/adrenotools")

    /** The picked driver, or null for Automatic. */
    fun chosen(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun choose(context: Context, name: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (name == null) remove(KEY) else putString(KEY, name)
        }.apply()
    }

    /** [gpu] with the picked driver, if one is picked and still installed; otherwise [gpu] as detected. */
    internal fun applyTo(context: Context, gpu: GpuProfile): GpuProfile {
        val name = chosen(context) ?: return gpu
        if (name != SYSTEM && !isInstalled(driversDir(context), name)) {
            Timber.w("Picked graphics driver $name isn't installed, using ${gpu.driver}")
            return gpu
        }
        return gpu.copy(driver = name)
    }

    /** Every driver Settings offers: the built-in ones, then the added ones by name. */
    fun list(context: Context): List<Driver> = list(driversDir(context))

    internal fun list(dir: File): List<Driver> {
        val builtIn = BUILT_IN.map { Driver(it, if (it == SYSTEM) "" else meta(File(dir, it))?.optString("driverVersion").orEmpty(), added = false) }
        val added = dir.listFiles().orEmpty()
            .filter { it.isDirectory && it.name !in BUILT_IN && SAFE_NAME.matches(it.name) && isInstalled(dir, it.name) }
            .sortedBy { it.name.lowercase() }
            .map { Driver(it.name, meta(it)?.optString("driverVersion").orEmpty(), added = true) }
        return builtIn + added
    }

    /** Unpacks an adrenotools driver zip from [input] into [dir] under the name in its meta.json. */
    internal fun import(dir: File, input: InputStream): Pair<ImportResult, String?> {
        val temp = File(dir, ".import")
        temp.deleteRecursively()
        try {
            temp.mkdirs()
            val unpacked = unpack(input, temp) ?: return ImportResult.TOO_LARGE to null
            if (!unpacked) return ImportResult.NOT_A_DRIVER to null
            // Some zips put everything in one folder.
            val root = if (File(temp, "meta.json").isFile) temp else
                temp.listFiles().orEmpty().singleOrNull { it.isDirectory && File(it, "meta.json").isFile } ?: return ImportResult.NOT_A_DRIVER to null
            val meta = meta(root) ?: return ImportResult.NOT_A_DRIVER to null
            val name = meta.optString("name").trim()
            val library = meta.optString("libraryName")
            if (!SAFE_NAME.matches(name) || library.isEmpty() || '/' in library || !File(root, library).isFile) {
                return ImportResult.NOT_A_DRIVER to null
            }
            if (name in BUILT_IN) return ImportResult.NAME_TAKEN to name
            val target = File(dir, name)
            // Adding a driver again replaces it; it can only be one of the added ones here.
            if (target.exists()) target.deleteRecursively()
            if (!root.renameTo(target)) return ImportResult.FAILED to null
            return ImportResult.OK to name
        } catch (e: Exception) {
            Timber.w(e, "Couldn't add the graphics driver")
            return ImportResult.FAILED to null
        } finally {
            temp.deleteRecursively()
        }
    }

    fun import(context: Context, input: InputStream) = import(driversDir(context), input)

    /** Removes an added driver; a built-in one can't be. Picking it falls back to Automatic. */
    fun remove(context: Context, name: String): Boolean {
        if (!remove(driversDir(context), name)) return false
        if (chosen(context) == name) choose(context, null)
        return true
    }

    internal fun remove(dir: File, name: String): Boolean {
        if (name in BUILT_IN || !SAFE_NAME.matches(name)) return false
        val target = File(dir, name)
        if (target.parentFile?.canonicalFile != dir.canonicalFile || !target.isDirectory) return false
        return target.deleteRecursively()
    }

    private fun isInstalled(dir: File, name: String): Boolean {
        val driver = File(dir, name)
        val library = meta(driver)?.optString("libraryName").orEmpty()
        return library.isNotEmpty() && File(driver, library).isFile
    }

    private fun meta(driver: File): JSONObject? =
        runCatching { JSONObject(File(driver, "meta.json").readText()) }.getOrNull()

    /** True when unpacked, false when an entry escapes [into], null when it's too big. */
    private fun unpack(input: InputStream, into: File): Boolean? {
        val base = into.canonicalPath + File.separator
        var total = 0L
        var entries = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > MAX_ENTRIES) return null
                val out = File(into, entry.name)
                if (!out.canonicalPath.startsWith(base)) return false
                if (entry.isDirectory) {
                    out.mkdirs()
                    continue
                }
                out.parentFile?.mkdirs()
                out.outputStream().use { sink ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_UNPACKED_BYTES) return null
                        sink.write(buffer, 0, read)
                    }
                }
            }
        }
        return entries > 0
    }
}
