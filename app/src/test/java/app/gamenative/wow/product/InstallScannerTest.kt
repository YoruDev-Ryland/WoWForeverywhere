package app.gamenative.wow.product

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InstallScannerTest {

    private lateinit var internal: File
    private lateinit var sd: File

    private val header = "Branch!STRING:0|Active!DEC:1|Version!STRING:0|Product!STRING:0"

    private fun install(root: File, product: String, withData: Boolean = true): File {
        root.mkdirs()
        File(root, ".build.info").writeText("$header\nus|1|1.60.1.70450|$product\n")
        File(root, "Data").mkdirs()
        if (withData) File(root, "Data/config").mkdirs()
        return root
    }

    @Before
    fun setUp() {
        internal = Files.createTempDirectory("internal").toFile()
        sd = Files.createTempDirectory("sd").toFile()
    }

    @Test
    fun findsInstallsAtDifferentDepths() {
        install(File(sd, "WoW Forever"), "wow_classic_beta")
        install(File(internal, "Games/WoW"), "wowf")
        val found = InstallScanner.scan(listOf(internal, sd)).associateBy { File(it.root).name }
        assertEquals(WowProduct.BETA, found.getValue("WoW Forever").product)
        assertEquals(WowProduct.FOREVER, found.getValue("WoW").product)
        assertEquals("1.60.1.70450", found.getValue("WoW").version)
    }

    @Test
    fun ignoresUnknownProduct() {
        install(File(sd, "Retail"), "wow")
        assertTrue(InstallScanner.scan(listOf(sd)).isEmpty())
    }

    @Test
    fun ignoresEmptyData() {
        install(File(sd, "Partial"), "wowf", withData = false)
        assertTrue(InstallScanner.scan(listOf(sd)).isEmpty())
    }

    @Test
    fun skipsAndroidAndHiddenDirs() {
        install(File(sd, "Android/data/x"), "wowf")
        install(File(sd, ".hidden/WoW"), "wowf")
        assertTrue(InstallScanner.scan(listOf(sd)).isEmpty())
    }

    @Test
    fun respectsMaxDepth() {
        install(File(sd, "a/b/c/d/WoW"), "wowf")
        assertTrue(InstallScanner.scan(listOf(sd), maxDepth = 3).isEmpty())
        assertEquals(1, InstallScanner.scan(listOf(sd), maxDepth = 5).size)
    }

    @Test
    fun doesNotDescendIntoAnInstall() {
        val root = install(File(sd, "WoW Forever"), "wowf")
        install(File(root, "backup"), "wow_classic_beta")
        assertEquals(listOf(root.absolutePath), InstallScanner.scan(listOf(sd)).map { it.root })
    }

    @Test
    fun survivesSymlinkLoop() {
        val dir = File(sd, "loop").apply { mkdirs() }
        Files.createSymbolicLink(File(dir, "self").toPath(), dir.toPath())
        install(File(sd, "WoW"), "wowf")
        assertEquals(1, InstallScanner.scan(listOf(sd), maxDepth = 6).size)
    }
}
