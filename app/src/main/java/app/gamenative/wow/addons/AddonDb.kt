package app.gamenative.wow.addons

import java.io.File
import java.io.IOException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The installed-addon database: a JSON list stored next to `Interface/AddOns`, so it travels with
 * the install. A missing or unreadable file loads as an empty list.
 */
class AddonDb(val file: File) {
    @Synchronized
    fun load(): List<InstalledAddon> {
        if (!file.isFile) return emptyList()
        return runCatching { json.decodeFromString(serializer, file.readText()) }.getOrElse { emptyList() }
    }

    /** Writes atomically (temp file + rename) so a crash never leaves a truncated database. */
    @Synchronized
    fun save(list: List<InstalledAddon>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(serializer, list))
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("cannot write ${file.path}")
        }
    }

    companion object {
        const val FILE_NAME = ".foreverywhere-addons.json"

        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
        private val serializer = ListSerializer(InstalledAddon.serializer())

        /** `<flavorDir>/Interface/AddOns/../.foreverywhere-addons.json` */
        fun forAddonsDir(addonsDir: File): AddonDb = AddonDb(File(addonsDir.absoluteFile.parentFile, FILE_NAME))
    }
}
