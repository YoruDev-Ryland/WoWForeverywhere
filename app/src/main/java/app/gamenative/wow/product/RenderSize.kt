package app.gamenative.wow.product

import android.content.Context

/** The game's resolution, chosen in Settings. [AUTO] keeps the device profile's size. */
enum class RenderSize(val key: String, val label: String, val size: String?) {
    AUTO("auto", "Automatic", null),
    P720("720", "720p", "1280x720"),
    P900("900", "900p", "1600x900"),
    P1080("1080", "1080p", "1920x1080"),
    NATIVE("native", "Native", null),
    ;

    companion object {
        fun fromKey(key: String?): RenderSize = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

/** The chosen [RenderSize], saved in SharedPreferences. */
object RenderSizes {
    private const val PREFS = "wow_settings"
    private const val KEY = "render_size"

    @Volatile
    var selected: RenderSize = RenderSize.AUTO

    fun load(context: Context) {
        selected = RenderSize.fromKey(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
    }

    fun save(context: Context, size: RenderSize) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, size.key).apply()
        selected = size
    }
}
