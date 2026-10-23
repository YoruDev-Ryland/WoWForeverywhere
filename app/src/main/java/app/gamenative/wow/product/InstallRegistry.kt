package app.gamenative.wow.product

import android.content.Context
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The installs this app knows about and which one is active, per [GameVersion]: [all] and [active] only
 * see the selected version's installs, and every version remembers its own active install. On first use
 * it adopts the single path that upstream's launcher stored (`wow_forever/game_path`), so existing users
 * keep their install.
 */
class InstallRegistry(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)

    /** The selected version's installs. */
    fun all(): List<GameInstall> = stored().filter { GameVersion.of(it.product) == GameVersions.selected }

    fun active(): GameInstall? {
        val installs = all()
        val root = prefs.getString(activeKey(GameVersions.selected), null)
        return installs.firstOrNull { it.root == root } ?: installs.firstOrNull()
    }

    fun setActive(root: String, version: GameVersion = GameVersions.selected) = saveActive(version, root)

    /**
     * Adds or replaces [install]; it becomes its version's active install if that version had none.
     * One folder can hold several versions (they share `Data/`), so installs are keyed by root and version.
     */
    fun add(install: GameInstall) {
        val version = GameVersion.of(install.product)
        saveInstalls(stored().filterNot { it.root == install.root && GameVersion.of(it.product) == version } + install)
        if (prefs.getString(activeKey(version), null) == null) saveActive(version, install.root)
    }

    /** Forgets the selected version's install at [root]; other versions in the same folder stay. */
    fun remove(root: String) {
        val version = GameVersions.selected
        val installs = stored().filterNot { it.root == root && GameVersion.of(it.product) == version }
        saveInstalls(installs)
        if (prefs.getString(activeKey(version), null) == root) {
            saveActive(version, installs.firstOrNull { GameVersion.of(it.product) == version }?.root)
        }
    }

    /** Every version's installs. */
    private fun stored(): List<GameInstall> {
        val stored = prefs.getString(KEY_INSTALLS, null)
        if (stored != null) return runCatching { json.decodeFromString<List<GameInstall>>(stored) }.getOrDefault(emptyList())
        val legacy = legacyPrefs.getString(LEGACY_KEY_PATH, null)?.let(::File)
        val migrated = legacy?.let { InstallScanner.scan(listOf(it), maxDepth = 0) }.orEmpty()
        saveInstalls(migrated)
        migrated.firstOrNull()?.let { saveActive(GameVersion.FOREVER, it.root) }
        return migrated
    }

    private fun saveInstalls(installs: List<GameInstall>) {
        prefs.edit().putString(KEY_INSTALLS, json.encodeToString(installs)).apply()
    }

    /** Forever keeps the original keys, including upstream's legacy path, so older builds still find it. */
    private fun saveActive(version: GameVersion, root: String?) {
        prefs.edit().putString(activeKey(version), root).apply()
        if (version == GameVersion.FOREVER && root != null) legacyPrefs.edit().putString(LEGACY_KEY_PATH, root).apply()
    }

    private fun activeKey(version: GameVersion) = if (version == GameVersion.FOREVER) KEY_ACTIVE else "${KEY_ACTIVE}_${version.key}"

    private companion object {
        const val PREFS = "wow_installs"
        const val KEY_INSTALLS = "installs"
        const val KEY_ACTIVE = "active"
        const val LEGACY_PREFS = "wow_forever"
        const val LEGACY_KEY_PATH = "game_path"
        val json = Json { ignoreUnknownKeys = true }
    }
}
