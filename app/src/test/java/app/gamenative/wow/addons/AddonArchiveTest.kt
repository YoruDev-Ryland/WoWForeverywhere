package app.gamenative.wow.addons

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.random.Random
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AddonArchiveTest {
    private lateinit var root: File
    private lateinit var addons: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("archive").toFile()
        addons = File(root, "Interface/AddOns").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun zip(bytes: ByteArray) = TestZips.write(File(root, "in.zip"), bytes)

    /** Everything under [root] except the input zip. */
    private fun allFiles(): Set<String> = root.walkTopDown()
        .filter { it.isFile && it.name != "in.zip" }
        .map { it.relativeTo(root).invariantSeparatorsPath }
        .toSet()

    private fun assertNoLeftovers() {
        assertTrue(addons.listFiles()!!.none { it.name.startsWith(".") })
    }

    @Test
    fun rejectsZipSlip() {
        for (evil in listOf("../evil.lua", "Foo/../../evil.lua", "/abs/evil.lua", "C:/evil.lua", "Foo\\..\\..\\evil.lua")) {
            val before = allFiles()
            val file = zip(TestZips.text("Foo/Foo.toc" to "## Title: Foo", evil to "pwned"))
            val error = assertThrows(IOException::class.java) { AddonArchive.install(file, addons) }
            assertEquals("unsafe path", error.message)
            assertEquals("for $evil", before, allFiles())
            assertFalse(File(root, "evil.lua").exists())
            assertNoLeftovers()
        }
    }

    @Test
    fun unwrapsSingleRootFolder() {
        val file = zip(
            TestZips.text(
                "Questie-10.0/" to "",
                "Questie-10.0/Questie/Questie.toc" to "## Title: Questie",
                "Questie-10.0/Questie/core.lua" to "x",
                "Questie-10.0/Questie-Data/Questie-Data.toc" to "## Title: Data",
                "Questie-10.0/README.md" to "readme",
            ),
        )
        val folders = AddonArchive.install(file, addons)
        assertEquals(listOf("Questie", "Questie-Data"), folders)
        assertTrue(File(addons, "Questie/Questie.toc").isFile)
        assertTrue(File(addons, "Questie-Data/Questie-Data.toc").isFile)
        assertFalse(File(addons, "Questie-10.0").exists())
        assertNoLeftovers()
    }

    @Test
    fun singleAddonFolderIsNotUnwrapped() {
        val folders = AddonArchive.install(zip(TestZips.addon("Foo")), addons)
        assertEquals(listOf("Foo"), folders)
        assertTrue(File(addons, "Foo/Foo.toc").isFile)
    }

    @Test
    fun multiFolderZipReturnsAllFolders() {
        val file = zip(
            TestZips.text(
                "DBM-Core/DBM-Core.toc" to "## Title: DBM",
                "DBM-StatusBarTimers/DBM-StatusBarTimers.toc" to "## Title: Bars",
                "DBM-GUI/DBM-GUI.toc" to "## Title: GUI",
                "__MACOSX/DBM-Core/._DBM-Core.toc" to "junk",
            ),
        )
        assertEquals(listOf("DBM-Core", "DBM-GUI", "DBM-StatusBarTimers"), AddonArchive.install(file, addons))
        assertFalse(File(addons, "__MACOSX").exists())
        assertNoLeftovers()
    }

    @Test
    fun replacesExistingFolderAtomically() {
        val old = File(addons, "Foo").apply { mkdirs() }
        File(old, "Foo.toc").writeText("## Version: old")
        File(old, "stale.lua").writeText("stale")

        // A zip that breaks mid-extract: the second entry is truncated.
        val big = Random(1).nextBytes(200_000)
        val whole = TestZips.bytes(mapOf("Foo/Foo.toc" to "## Version: new".toByteArray(), "Foo/big.bin" to big))
        val broken = zip(whole.copyOf(whole.size / 2))
        assertThrows(IOException::class.java) { AddonArchive.install(broken, addons) }
        assertEquals("## Version: old", File(old, "Foo.toc").readText())
        assertTrue(File(old, "stale.lua").exists())
        assertNoLeftovers()

        // A good zip replaces the whole folder, dropping stale files.
        AddonArchive.install(zip(TestZips.text("Foo/Foo.toc" to "## Version: new")), addons)
        assertEquals("## Version: new", File(old, "Foo.toc").readText())
        assertFalse(File(old, "stale.lua").exists())
        assertNoLeftovers()
    }

    @Test
    fun emptyZipFails() {
        assertThrows(IOException::class.java) { AddonArchive.install(zip(TestZips.text("readme.txt" to "hi")), addons) }
        assertNoLeftovers()
    }
}
