package app.gamenative.wow.tact

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.zip.Inflater

/** Decoder for BLTE, the container every file on Blizzard's CDN is wrapped in. */
object Blte {
    fun decode(data: ByteArray): ByteArray = ByteArrayOutputStream(data.size).also { decode(data, it) }.toByteArray()

    fun decode(data: ByteArray, out: OutputStream) {
        if (data.size < 8 || String(data, 0, 4) != "BLTE") throw IOException("Not BLTE data")
        val buffer = ByteBuffer.wrap(data)
        val headerSize = buffer.getInt(4)
        if (headerSize == 0) {
            out.write(decodeChunk(data.copyOfRange(8, data.size)))
            return
        }
        val count = buffer.getInt(8) and 0xFFFFFF
        var offset = headerSize
        for (i in 0 until count) {
            val compressedSize = buffer.getInt(12 + i * 24)
            out.write(decodeChunk(data.copyOfRange(offset, offset + compressedSize)))
            offset += compressedSize
        }
    }

    fun decodeChunk(chunk: ByteArray): ByteArray {
        val body = chunk.copyOfRange(1, chunk.size)
        return when (chunk[0].toInt().toChar()) {
            'N' -> body
            'Z' -> inflate(body)
            'F' -> decode(body)
            else -> throw IOException("Unsupported BLTE chunk mode ${chunk[0].toInt().toChar()}")
        }
    }

    private fun inflate(body: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(body)
        val out = ByteArrayOutputStream(body.size * 2)
        val buffer = ByteArray(64 * 1024)
        try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) throw IOException("Truncated zlib chunk")
                out.write(buffer, 0, n)
            }
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }
}
