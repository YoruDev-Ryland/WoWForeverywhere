package app.gamenative.wow.product

import android.content.Context

/**
 * Which WoW game account to play when one Battle.net login holds several. The game keeps them in Config.wtf as
 * `SET accountList "!WoW1|WoW2|"`, where `!` marks the one it logs into, and with auto-login it never shows its
 * account picker. Most are named WoW1, WoW2 and so on, but an account can have its own name, so the player can
 * type one. Until one is picked ([chosen] null) the game's own choice stays.
 */
object GameAccounts {
    const val ACCOUNT_LIST = "SET accountList "

    private const val PREFS = "wow_settings"
    private const val KEY_NAME = "game_account_name"

    /** Versions up to 1.0.3 saved the account's number. */
    private const val KEY_NUMBER = "game_account"
    private const val MAX_NAME_LENGTH = 64

    private val USUAL_NAMES = listOf("WoW1", "WoW2")

    @Volatile
    var chosen: String? = null

    fun load(context: Context) {
        val prefs = prefs(context)
        chosen = prefs.getString(KEY_NAME, null) ?: prefs.getInt(KEY_NUMBER, 0).takeIf { it > 0 }?.let { "WoW$it" }
    }

    /** Saves [name], or null to leave the choice to the game. */
    fun save(context: Context, name: String?) {
        prefs(context).edit().apply {
            remove(KEY_NUMBER)
            if (name == null) remove(KEY_NAME) else putString(KEY_NAME, name)
        }.apply()
        chosen = name
    }

    /** [input] as an account name, or null if it's empty or would break the `accountList` line. */
    fun clean(input: String): String? = input.trim().takeIf { name ->
        name.isNotEmpty() && name.length <= MAX_NAME_LENGTH && !name.startsWith("!") &&
            name.none { it == '|' || it == '"' || it.isISOControl() }
    }

    /** The names in an `accountList` line, without the `!` mark. */
    fun namesInLine(line: String?): List<String> =
        line?.substringAfter('"', "")?.substringBeforeLast('"', "")
            ?.split('|')?.map { it.trim().removePrefix("!") }?.filter { it.isNotEmpty() }.orEmpty()

    /** The account names the game has in its Config.wtf [lines]. */
    fun namesIn(lines: List<String>): List<String> =
        namesInLine(lines.lastOrNull { it.startsWith(ACCOUNT_LIST, ignoreCase = true) })

    /** The names to offer: the [known] ones from the game, the usual ones, and the [current] pick if it's neither. */
    fun suggestions(known: List<String>, current: String?): List<String> =
        (known + USUAL_NAMES + listOfNotNull(current)).distinctBy { it.lowercase() }

    /** Config.wtf [lines] with no `accountList`, so the game goes back to choosing for itself. */
    fun withoutAccountList(lines: List<String>): List<String> =
        lines.filterNot { it.startsWith(ACCOUNT_LIST, ignoreCase = true) }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
