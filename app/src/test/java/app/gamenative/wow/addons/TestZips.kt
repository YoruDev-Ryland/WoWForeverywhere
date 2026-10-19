package app.gamenative.wow.addons

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object TestZips {
    /** Builds a zip from `entry name -> content`; names ending in `/` become directory entries. */
    fun bytes(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                if (!name.endsWith("/")) zip.write(content)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun text(vararg entries: Pair<String, String>): ByteArray = bytes(entries.associate { it.first to it.second.toByteArray() })

    fun write(file: File, bytes: ByteArray): File = file.apply { writeBytes(bytes) }

    /** A zip with one addon folder `<name>` holding a TOC. */
    fun addon(name: String, toc: String = "## Interface: 16001\n## Title: $name\n## Version: 1.0\n"): ByteArray =
        text("$name/$name.toc" to toc, "$name/core.lua" to "-- $name")
}
