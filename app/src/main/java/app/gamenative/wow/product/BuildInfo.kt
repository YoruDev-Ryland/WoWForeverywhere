package app.gamenative.wow.product

import java.io.File

/**
 * The pipe-separated `.build.info` file at the root of a CASC install. [header] keeps the raw column
 * declarations (e.g. `Build Key!HEX:16`) so [serialize] reproduces the file byte for byte.
 */
data class BuildInfo(val header: List<String>, val rows: List<Map<String, String>>) {

    private val columns get() = header.map { it.substringBefore("!") }

    fun active(product: WowProduct? = null): Map<String, String>? {
        val candidates = if (product == null) rows else rows.filter { it["Product"] == product.code }
        return candidates.firstOrNull { it["Active"] == "1" } ?: candidates.firstOrNull()
    }

    fun withUpdatedActive(product: WowProduct, values: Map<String, String>): BuildInfo {
        val target = active(product) ?: return this
        val known = values.filterKeys { it in columns }
        return copy(rows = rows.map { if (it === target) it + known else it })
    }

    /** Supported products present in this file, launch product first. A shared install can hold several. */
    fun products(): List<WowProduct> = WowProduct.entries.filter { p -> rows.any { it["Product"] == p.code } }

    /**
     * Adds an active row for [to], cloned from [from]'s row, when [to] has none yet. [from] stays active:
     * products in one install share `Data/`, and each product's row carries its own Active flag.
     */
    fun withProduct(from: WowProduct, to: WowProduct): BuildInfo {
        if (rows.any { it["Product"] == to.code }) return this
        val source = active(from) ?: return this
        return copy(rows = rows + source.plus(listOf("Product" to to.code, "Active" to "1")))
    }

    fun serialize(): String =
        (listOf(header.joinToString("|")) + rows.map { row -> columns.joinToString("|") { row[it].orEmpty() } })
            .joinToString("\n", postfix = "\n")

    companion object {
        const val FILE_NAME = ".build.info"

        fun parse(text: String): BuildInfo {
            val lines = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }
            val header = lines.firstOrNull()?.split("|") ?: return BuildInfo(emptyList(), emptyList())
            val columns = header.map { it.substringBefore("!") }
            return BuildInfo(header, lines.drop(1).map { columns.zip(it.split("|")).toMap() })
        }

        fun read(root: File): BuildInfo? =
            File(root, FILE_NAME).takeIf { it.isFile }?.let { runCatching { parse(it.readText()) }.getOrNull() }
    }
}
