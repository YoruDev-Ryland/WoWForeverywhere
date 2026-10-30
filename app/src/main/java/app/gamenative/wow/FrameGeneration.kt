package app.gamenative.wow

import android.content.Context
import app.gamenative.utils.LsfgVkManager
import java.io.File
import java.io.InputStream

/**
 * Frame generation (LSFG) from Lossless Scaling. Its shaders live in Lossless.dll, which can't ship with
 * the app: the player picks their own copy in Settings and it's kept in the app's private storage.
 * [LsfgVkManager] copies it into the game's container at launch, and the in-game menu's LSFG tab appears.
 */
object FrameGeneration {
    enum class ImportResult { OK, NOT_A_DLL, FAILED }

    private const val PREFS = "wow_settings"
    private const val KEY_ENABLED = "frame_generation"

    /** Windows DLLs start with "MZ"; anything this small isn't Lossless Scaling's. */
    private const val MIN_DLL_BYTES = 100_000L

    fun dllFile(context: Context) = File(context.filesDir, "lsfg/Lossless.dll")

    fun hasDll(context: Context) = dllFile(context).isFile

    fun isEnabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** True when frame generation should run at the next launch: a DLL is set and the switch is on. */
    fun isArmed(context: Context) = hasDll(context) && isEnabled(context)

    /** Copies the picked file in, after checking it's a Windows DLL of a plausible size. */
    fun import(context: Context, input: InputStream): ImportResult {
        val target = dllFile(context)
        val temp = File(target.parentFile, "Lossless.dll.part")
        return try {
            target.parentFile?.mkdirs()
            input.use { src -> temp.outputStream().use { src.copyTo(it) } }
            val header = temp.inputStream().use { ByteArray(2).also { b -> it.read(b) } }
            if (header[0] != 'M'.code.toByte() || header[1] != 'Z'.code.toByte() || temp.length() < MIN_DLL_BYTES) {
                temp.delete()
                return ImportResult.NOT_A_DLL
            }
            target.delete()
            if (!temp.renameTo(target)) return ImportResult.FAILED
            setEnabled(context, true)
            ImportResult.OK
        } catch (e: Exception) {
            temp.delete()
            ImportResult.FAILED
        }
    }

    fun remove(context: Context) {
        dllFile(context).delete()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ENABLED).apply()
    }

    /** Lets [LsfgVkManager] find the player's DLL; called once at app start. */
    fun init(context: Context) {
        LsfgVkManager.userDll = dllFile(context)
    }
}
