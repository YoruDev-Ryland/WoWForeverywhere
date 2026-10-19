package app.gamenative.wow.product

import android.content.Context
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The installs this app knows about and which one is active. On first use it adopts the single path
 * that upstream's launcher stored (`wow_forever/game_path`), so existing users keep their install.
 */
class InstallRegistry(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)

    fun all(): List<GameInstall> {
        val stored = prefs.getString(KEY_INSTALLS, null)
        if (stored != null) return runCatching { json.decodeFromString<List<GameInstall>>(stored) }.getOrDefault(emptyList())
        val legacy = legacyPrefs.getString(LEGACY_KEY_PATH, null)?.let(::File)
        val migrated = legacy?.let { InstallScanner.scan(listOf(it), maxDepth = 0) }.orEmpty()
        save(migrated, migrated.firstOrNull()?.root)
        return migrated
    }

    fun active(): GameInstall? {
        val installs = all()
        val root = prefs.getString(KEY_ACTIVE, null)
        return installs.firstOrNull { it.root == root } ?: installs.firstOrNull()
    }

    fun setActive(root: String) {
        prefs.edit().putString(KEY_ACTIVE, root).apply()
        legacyPrefs.edit().putString(LEGACY_KEY_PATH, root).apply()
    }

    fun add(install: GameInstall) {
        val installs = all().filterNot { it.root == install.root } + install
        save(installs, prefs.getString(KEY_ACTIVE, null) ?: install.root)
    }

    fun remove(root: String) {
        val installs = all().filterNot { it.root == root }
        val active = prefs.getString(KEY_ACTIVE, null).takeIf { it != root } ?: installs.firstOrNull()?.root
        save(installs, active)
    }

    private fun save(installs: List<GameInstall>, active: String?) {
        prefs.edit().putString(KEY_INSTALLS, json.encodeToString(installs)).putString(KEY_ACTIVE, active).apply()
        active?.let { legacyPrefs.edit().putString(LEGACY_KEY_PATH, it).apply() }
    }

    private companion object {
        const val PREFS = "wow_installs"
        const val KEY_INSTALLS = "installs"
        const val KEY_ACTIVE = "active"
        const val LEGACY_PREFS = "wow_forever"
        const val LEGACY_KEY_PATH = "game_path"
        val json = Json { ignoreUnknownKeys = true }
    }
}
