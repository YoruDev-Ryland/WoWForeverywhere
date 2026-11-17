package app.gamenative.wow.product

import android.content.Context

/** A language WoW ships, by its locale code (the `.build.info` Tags language and Config.wtf textLocale). */
enum class GameLanguage(val code: String, val label: String) {
    EN_US("enUS", "English (US)"),
    EN_GB("enGB", "English (UK)"),
    DE_DE("deDE", "Deutsch"),
    ES_ES("esES", "Español (España)"),
    ES_MX("esMX", "Español (México)"),
    FR_FR("frFR", "Français"),
    IT_IT("itIT", "Italiano"),
    KO_KR("koKR", "한국어"),
    PT_BR("ptBR", "Português (Brasil)"),
    RU_RU("ruRU", "Русский"),
    ZH_CN("zhCN", "简体中文"),
    ZH_TW("zhTW", "繁體中文"),
    ;

    companion object {
        fun fromCode(code: String?): GameLanguage? = entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}

/**
 * The game language the player picked in Settings (#63). Until one is picked ([chosen] null) an install keeps the
 * language it was made with, which is the phone's language if WoW ships it. A pick is downloaded by a repair, which
 * also moves the install's language tag, and the game's text and audio follow once that's done.
 */
object GameLanguages {
    private const val PREFS = "wow_settings"
    private const val KEY = "game_language"

    @Volatile
    var chosen: GameLanguage? = null

    fun load(context: Context) {
        chosen = GameLanguage.fromCode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
    }

    fun save(context: Context, language: GameLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.code).apply()
        chosen = language
    }
}
