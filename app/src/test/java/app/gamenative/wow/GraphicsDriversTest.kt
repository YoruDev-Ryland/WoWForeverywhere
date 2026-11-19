package app.gamenative.wow

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphicsDriversTest {
    private val dir: File = Files.createTempDirectory("drivers").toFile()

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArrayInputStream {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, body) ->
                z.putNextEntry(ZipEntry(name))
                z.write(body.toByteArray())
                z.closeEntry()
            }
        }
        return ByteArrayInputStream(out.toByteArray())
    }

    private fun meta(name: String, library: String = "libvulkan_freedreno.so") =
        """{"name":"$name","libraryName":"$library","driverVersion":"25.1"}"""

    @Test
    fun addsADriverUnderItsMetaName() {
        val (result, name) = GraphicsDrivers.import(dir, zip("meta.json" to meta("Turnip-v25"), "libvulkan_freedreno.so" to "elf"))
        assertEquals(GraphicsDrivers.ImportResult.OK, result)
        assertEquals("Turnip-v25", name)
        assertTrue(File(dir, "Turnip-v25/libvulkan_freedreno.so").isFile)
        val added = GraphicsDrivers.list(dir).single { it.added }
        assertEquals("Turnip-v25", added.name)
        assertEquals("25.1", added.version)
        assertFalse(File(dir, ".import").exists())
    }

    @Test
    fun acceptsADriverInsideOneFolder() {
        val (result, name) = GraphicsDrivers.import(dir, zip("pack/meta.json" to meta("Folder-driver"), "pack/libvulkan_freedreno.so" to "elf"))
        assertEquals(GraphicsDrivers.ImportResult.OK, result)
        assertTrue(File(dir, "$name/meta.json").isFile)
    }

    @Test
    fun refusesZipsThatAreNotDrivers() {
        listOf(
            zip("readme.txt" to "hi"),
            zip("meta.json" to meta("No-library")),
            zip("meta.json" to meta("../escape"), "libvulkan_freedreno.so" to "elf"),
            zip("meta.json" to meta("Sub", "../lib.so"), "libvulkan_freedreno.so" to "elf"),
            zip("../outside.so" to "elf", "meta.json" to meta("Slip"), "libvulkan_freedreno.so" to "elf"),
        ).forEach { assertEquals(GraphicsDrivers.ImportResult.NOT_A_DRIVER, GraphicsDrivers.import(dir, it).first) }
        assertFalse(File(dir.parentFile, "outside.so").exists())
        assertTrue(GraphicsDrivers.list(dir).none { it.added })
    }

    @Test
    fun keepsBuiltInDriversFromBeingReplacedOrRemoved() {
        val builtIn = GraphicsDrivers.BUILT_IN.first()
        assertEquals(
            GraphicsDrivers.ImportResult.NAME_TAKEN,
            GraphicsDrivers.import(dir, zip("meta.json" to meta(builtIn), "libvulkan_freedreno.so" to "elf")).first,
        )
        File(dir, builtIn).mkdirs()
        assertFalse(GraphicsDrivers.remove(dir, builtIn))
        assertTrue(File(dir, builtIn).isDirectory)
    }

    @Test
    fun removesOnlyAnAddedDriverFolder() {
        GraphicsDrivers.import(dir, zip("meta.json" to meta("Gone"), "libvulkan_freedreno.so" to "elf"))
        assertFalse(GraphicsDrivers.remove(dir, ".."))
        assertFalse(GraphicsDrivers.remove(dir, ""))
        assertTrue(GraphicsDrivers.remove(dir, "Gone"))
        assertFalse(File(dir, "Gone").exists())
        assertTrue(dir.isDirectory)
    }
}
