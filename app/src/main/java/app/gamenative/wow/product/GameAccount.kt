package app.gamenative.wow.product

import android.content.Context

/**
 * Which WoW game account to play when one Battle.net login holds several. The game keeps them in Config.wtf
 * as `SET accountList "!WoW1|WoW2|"`, where `!` marks the one it logs into, and with auto-login it never shows
 * its account picker. Until one is picked ([chosen] null) the game's own choice stays.
 *
 * Most accounts are named WoW1, WoW2, ..., but older ones can carry a custom name (e.g. `MyAccount`, shown in the
 * Battle.net app's account menu as "MyAccount (EU)"), so a name can also be typed in Account.
 */
object GameAccounts {
    private const val PREFS = "wow_settings"
    private const val KEY_NAME = "game_account_name"

    /** Older versions stored only the number of a WoWn account. */
    private const val LEGACY_KEY_NUMBER = "game_account"

    /** Longest name accepted, well above anything Blizzard hands out. */
    const val MAX_NAME_LENGTH = 32

    /** The numbers offered in Account. Blizzard numbers game accounts from WoW1. */
    val NUMBERS = 1..8

    @Volatile
    var chosen: String? = null

    fun name(number: Int) = "WoW$number"

    /** True when [name] is one of the WoWn names offered as chips, so it needs no custom entry. */
    fun isNumbered(name: String) = NUMBERS.any { name(it).equals(name, ignoreCase = true) }

    /**
     * [raw] cleaned up for Config.wtf, or null when it can't be a game account name. `"`, `|` and `!` are the
     * accountList syntax and a line break would end the line, so none of them can be part of a name.
     */
    fun normalize(raw: String): String? {
        val name = raw.trim()
        if (name.isEmpty() || name.length > MAX_NAME_LENGTH) return null
        if (name.any { it == '"' || it == '|' || it == '!' || it.isISOControl() }) return null
        return name
    }

    fun load(context: Context) {
        val prefs = prefs(context)
        chosen = prefs.getString(KEY_NAME, null)?.let(::normalize)
            ?: prefs.getInt(LEGACY_KEY_NUMBER, 0).takeIf { it > 0 }?.let(::name)
    }

    /** Saves [account] (a WoWn or custom name), or null to leave the choice to the game. Invalid names are ignored. */
    fun save(context: Context, account: String?) {
        val name = account?.let { normalize(it) ?: return }
        prefs(context).edit().apply {
            remove(LEGACY_KEY_NUMBER)
            if (name == null) remove(KEY_NAME) else putString(KEY_NAME, name)
        }.apply()
        chosen = name
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
