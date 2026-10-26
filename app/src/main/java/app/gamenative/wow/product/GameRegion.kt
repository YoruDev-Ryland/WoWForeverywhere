package app.gamenative.wow.product

import android.content.Context

/** Battle.net regions the game can play in. The code is the `.build.info` Branch; the portal is it in capitals. */
enum class GameRegion(val code: String, val label: String) {
    US("us", "Americas"),
    EU("eu", "Europe"),
    KR("kr", "Korea"),
    TW("tw", "Taiwan"),
    ;

    val portal get() = code.uppercase()

    companion object {
        fun fromCode(code: String?): GameRegion? = entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}

/**
 * The region the player picked, saved in SharedPreferences. Until one is picked ([chosen] null) the
 * app keeps using each install's own region and leaves a hand-edited portal alone.
 */
object GameRegions {
    private const val PREFS = "wow_settings"
    private const val KEY = "region"

    @Volatile
    var chosen: GameRegion? = null

    /** The region in effect for an install whose `.build.info` Branch is [branch]. */
    fun effective(branch: String?): GameRegion = chosen ?: GameRegion.fromCode(branch) ?: GameRegion.US

    fun load(context: Context) {
        chosen = GameRegion.fromCode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
    }

    fun save(context: Context, region: GameRegion) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, region.code).apply()
        chosen = region
    }
}
