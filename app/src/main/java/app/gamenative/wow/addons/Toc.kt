package app.gamenative.wow.addons

/** Parsed metadata of a single addon `.toc` file. */
data class Toc(
    val folder: String,
    val fileName: String,
    val title: String?,
    val version: String?,
    val interfaces: List<Int>,
    val dependencies: List<String>,
    val curseId: String?,
    val wagoId: String?,
    val wowiId: String?,
    /** Every `## Key: Value` line, keyed by lowercase key. */
    val raw: Map<String, String>,
)

object TocParser {
    private val metaLine = Regex("""^##\s*([^:]+?)\s*:\s*(.*)$""")
    private val colorStart = Regex("""\|c[0-9a-fA-F]{8}""")
    private val textureTag = Regex("""\|T[^|]*\|t""")

    fun parse(folder: String, fileName: String, text: String): Toc {
        val raw = LinkedHashMap<String, String>()
        val dependencies = mutableListOf<String>()
        text.removePrefix("﻿").lineSequence().forEach { line ->
            val match = metaLine.find(line.trim()) ?: return@forEach
            val key = match.groupValues[1].trim().lowercase()
            val value = match.groupValues[2].trim()
            raw.putIfAbsent(key, value)
            if (key == "dependencies" || key == "requireddeps" || key == "dependancies" || key == "deps") {
                value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                    if (it !in dependencies) dependencies += it
                }
            }
        }
        return Toc(
            folder = folder,
            fileName = fileName,
            title = raw["title"]?.let(::stripFormatting)?.ifBlank { null },
            version = raw["version"]?.ifBlank { null },
            interfaces = raw["interface"].orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() },
            dependencies = dependencies,
            curseId = raw["x-curse-project-id"]?.ifBlank { null },
            wagoId = raw["x-wago-id"]?.ifBlank { null },
            wowiId = raw["x-wowi-id"]?.ifBlank { null },
            raw = raw,
        )
    }

    /** Removes WoW UI escape sequences (`|cAARRGGBB`, `|r`, `|T…|t`) and collapses whitespace. */
    fun stripFormatting(text: String): String = text
        .replace(textureTag, "")
        .replace(colorStart, "")
        .replace("|r", "")
        .replace(Regex("""\s+"""), " ")
        .trim()
}
