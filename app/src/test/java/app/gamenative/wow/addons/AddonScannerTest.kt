package app.gamenative.wow.addons

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AddonScannerTest {
    private lateinit var root: File
    private lateinit var addons: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("scan").toFile()
        addons = File(root, "_forever_/Interface/AddOns").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun addon(folder: String, toc: String, tocName: String = "$folder.toc") {
        File(addons, folder).apply { mkdirs() }.resolve(tocName).writeText(toc.trimIndent() + "\n")
    }

    private fun standardTree() {
        addon(
            "Questie",
            """
            ## Interface: 11507
            ## Title: |cFFFFFFFFQuestie|r
            ## Version: 10.1.0
            ## X-Curse-Project-ID: 334372
            """,
            tocName = "Questie-Classic.toc",
        )
        addon(
            "DBM-Core",
            """
            ## Interface: 11507, 16001
            ## Title: DBM Core
            ## Version: 11.5.0
            ## X-Curse-Project-ID: 3358
            """,
        )
        addon(
            "DBM-StatusBarTimers",
            """
            ## Interface: 11507, 16001
            ## Title: DBM StatusBarTimers
            ## Dependencies: DBM-Core
            ## X-Curse-Project-ID: 3358
            """,
        )
        addon(
            "MyAddon",
            """
            ## Interface: 11507
            ## Title: My Addon
            """,
        )
    }

    @Test
    fun groupsFoldersIntoLogicalAddons() {
        standardTree()
        val result = AddonScanner.scan(addons, emptyList()).associateBy { it.name }

        assertEquals(setOf("Questie", "DBM Core", "My Addon"), result.keys)

        val questie = result.getValue("Questie")
        assertEquals(listOf("Questie"), questie.folders)
        assertEquals(SourceRef("curseforge", "334372"), questie.source)
        assertEquals(AddonFlavor.CLASSIC, questie.flavor)
        assertEquals("10.1.0", questie.version)

        val dbm = result.getValue("DBM Core")
        assertEquals(listOf("DBM-Core", "DBM-StatusBarTimers"), dbm.folders.sorted())
        assertEquals(SourceRef("curseforge", "3358"), dbm.source)
        assertEquals(AddonFlavor.FOREVER, dbm.flavor)

        val mine = result.getValue("My Addon")
        assertEquals(listOf("MyAddon"), mine.folders)
        assertNull(mine.source)
    }

    @Test
    fun groupsByDependencyWithSharedPrefix() {
        addon("Foo-Core", "## Title: Foo\n## Interface: 11507")
        addon("Foo-Options", "## Title: Foo Options\n## Interface: 11507\n## Dependencies: Foo-Core")
        addon("Bar", "## Title: Bar\n## Interface: 11507\n## Dependencies: Foo-Core")
        val result = AddonScanner.scan(addons, emptyList()).associateBy { it.name }
        assertEquals(listOf("Foo-Core", "Foo-Options"), result.getValue("Foo").folders.sorted())
        assertEquals(listOf("Bar"), result.getValue("Bar").folders)
    }

    @Test
    fun preservesKnownRecords() {
        standardTree()
        val known = InstalledAddon(
            id = "wago:abc",
            name = "Questie (Wago)",
            folders = listOf("Questie"),
            version = "9.9",
            flavor = AddonFlavor.CLASSIC,
            source = SourceRef("wago", "abc", "rel1"),
            installedAt = 42,
        )
        val result = AddonScanner.scan(addons, listOf(known))
        assertTrue(known in result)
        assertEquals(1, result.count { "Questie" in it.folders })
        assertEquals(3, result.size)
    }

    @Test
    fun deletedFoldersDropOut() {
        standardTree()
        val known = listOf(
            InstalledAddon("curseforge:3358", "DBM", listOf("DBM-Core", "DBM-StatusBarTimers", "DBM-Gone"), "1", AddonFlavor.FOREVER, SourceRef("curseforge", "3358"), 1),
            InstalledAddon("curseforge:999", "Removed", listOf("Removed"), "1", AddonFlavor.CLASSIC, SourceRef("curseforge", "999"), 1),
        )
        File(addons, "MyAddon").deleteRecursively()
        val result = AddonScanner.scan(addons, known).associateBy { it.id }
        assertEquals(setOf("curseforge:3358", "curseforge:334372"), result.keys)
        assertEquals(listOf("DBM-Core", "DBM-StatusBarTimers"), result.getValue("curseforge:3358").folders)
    }

    @Test
    fun ignoresHiddenAndTocLessFolders() {
        standardTree()
        File(addons, ".foreverywhere-extract-1/Foo").mkdirs()
        File(addons, "NoToc").mkdirs()
        val result = AddonScanner.scan(addons, emptyList())
        assertEquals(3, result.size)
    }

    @Test
    fun dbRoundTrip() {
        val db = AddonDb.forAddonsDir(addons)
        assertEquals(File(root, "_forever_/Interface/.foreverywhere-addons.json"), db.file)
        assertEquals(emptyList<InstalledAddon>(), db.load())
        val list = listOf(
            InstalledAddon("curseforge:1", "A", listOf("A", "A_Lib"), "1.0", AddonFlavor.FOREVER, SourceRef("curseforge", "1", "55"), 7),
            InstalledAddon("local:B", "B", listOf("B"), null, AddonFlavor.OTHER, null, 8),
        )
        db.save(list)
        assertEquals(list, db.load())
        db.file.writeText("{not json")
        assertEquals(emptyList<InstalledAddon>(), db.load())
    }
}
