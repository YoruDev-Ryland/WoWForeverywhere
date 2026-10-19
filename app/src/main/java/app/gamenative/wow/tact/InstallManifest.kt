package app.gamenative.wow.tact

import java.io.IOException
import java.nio.ByteBuffer

data class InstallEntry(val name: String, val ckey: String, val size: Long, val tags: Set<String>) {
    val relativePath get() = name.replace('\\', '/')
}

/** The decoded `install` manifest: loose files (binaries, launcher bits) and the tags that select them. */
object InstallManifest {
    fun parse(data: ByteArray): List<InstallEntry> {
        if (data.size < 10 || data[0] != 'I'.code.toByte() || data[1] != 'N'.code.toByte()) throw IOException("Bad install manifest")
        val buffer = ByteBuffer.wrap(data)
        val hashSize = data[3].toInt()
        val tagCount = buffer.getShort(4).toInt() and 0xFFFF
        val entryCount = buffer.getInt(6)
        val maskSize = (entryCount + 7) / 8
        var pos = 10

        fun readString(): String {
            val end = (pos until data.size).first { data[it].toInt() == 0 }
            return String(data, pos, end - pos).also { pos = end + 1 }
        }

        val tags = (0 until tagCount).map {
            val name = readString()
            pos += 2
            (name to data.copyOfRange(pos, pos + maskSize)).also { pos += maskSize }
        }
        return (0 until entryCount).map { i ->
            val name = readString()
            val ckey = hex(data, pos, hashSize)
            val size = buffer.getInt(pos + hashSize).toLong() and 0xFFFFFFFFL
            pos += hashSize + 4
            val entryTags = tags.filter { (_, mask) -> mask[i / 8].toInt() and (0x80 ushr (i % 8)) != 0 }.map { it.first }.toSet()
            InstallEntry(name, ckey, size, entryTags)
        }
    }
}
