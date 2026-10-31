package app.gamenative.wow.debug

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import timber.log.Timber

/**
 * Settings, Debug logging: while on, the app's full log goes to `debug/app.log` (kept across restarts,
 * so a crash's lead-up survives) and the game's Wine output to `wine_logs/wine_debug.log`.
 */
object DebugLog {
    private const val PREFS = "wow_settings"
    private const val KEY_ENABLED = "debug_logging"
    private const val MAX_BYTES = 2_000_000L

    private var tree: FileTree? = null

    fun dir(context: Context) = File(context.getExternalFilesDir(null) ?: context.filesDir, "debug")

    fun isEnabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
        apply(context)
    }

    /** Plants or removes the file log to match the setting. Called at start-up and on every change. */
    @Synchronized
    fun apply(context: Context) {
        val enabled = isEnabled(context)
        if (enabled && tree == null) {
            tree = FileTree(File(dir(context), "app.log")).also { Timber.plant(it) }
            Timber.i("Debug logging on")
        } else if (!enabled) {
            tree?.let { Timber.uproot(it) }
            tree = null
        }
    }

    private class FileTree(private val file: File) : Timber.Tree() {
        private val writer = Executors.newSingleThreadExecutor { Thread(it, "debug-log").apply { isDaemon = true } }
        private val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            val level = when (priority) {
                Log.VERBOSE -> 'V'
                Log.DEBUG -> 'D'
                Log.INFO -> 'I'
                Log.WARN -> 'W'
                Log.ERROR -> 'E'
                else -> 'A'
            }
            val line = "${time.format(Date())} $level ${tag.orEmpty()}: $message" +
                (t?.let { "\n" + Log.getStackTraceString(it) } ?: "") + "\n"
            writer.execute {
                runCatching {
                    file.parentFile?.mkdirs()
                    if (file.length() > MAX_BYTES) file.renameTo(File(file.parentFile, "app.log.1"))
                    file.appendText(line)
                }
            }
        }
    }
}
