package app.gamenative.wow.tact

import java.io.File
import java.security.MessageDigest

private val HEX = "0123456789abcdef".toCharArray()

fun hex(data: ByteArray, offset: Int = 0, size: Int = data.size - offset): String {
    val chars = CharArray(size * 2)
    for (i in 0 until size) {
        val v = data[offset + i].toInt() and 0xFF
        chars[i * 2] = HEX[v ushr 4]
        chars[i * 2 + 1] = HEX[v and 0xF]
    }
    return String(chars)
}

fun md5Hex(file: File): String? {
    if (!file.isFile) return null
    val digest = MessageDigest.getInstance("MD5")
    file.inputStream().use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }
    return hex(digest.digest())
}

/** Pipe-separated tables used by the patch service (`versions`, `cdns`). Column types after `!` are dropped. */
object Psv {
    fun parse(text: String): List<Map<String, String>> {
        val lines = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        val header = lines.firstOrNull()?.split("|")?.map { it.substringBefore("!") } ?: return emptyList()
        return lines.drop(1).map { header.zip(it.split("|")).toMap() }
    }
}

/** `key = value value ...` configs (build config, CDN config). */
object TactConfig {
    fun parse(text: String): Map<String, List<String>> =
        text.lines().filter { "=" in it && !it.startsWith("#") }.associate { line ->
            line.substringBefore("=").trim() to line.substringAfter("=").trim().split(" ").filter { it.isNotEmpty() }
        }
}
