package app.gamenative.wow.addons

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TocTest {
    private lateinit var tmp: File

    @Before
    fun setUp() {
        tmp = Files.createTempDirectory("toc").toFile()
    }

    @After
    fun tearDown() {
        tmp.deleteRecursively()
    }

    @Test
    fun stripsTitleColorCodes() {
        val toc = TocParser.parse("Foo", "Foo.toc", "## Title: |cff00ff00Foo|r Bar |cFFFF0000Baz|r\n")
        assertEquals("Foo Bar Baz", toc.title)
    }

    @Test
    fun parsesMultipleInterfaces() {
        val toc = TocParser.parse("Foo", "Foo.toc", "## Interface: 11507, 16001\n## Version: 1.2.3\n")
        assertEquals(listOf(11507, 16001), toc.interfaces)
        assertEquals("1.2.3", toc.version)
    }

    @Test
    fun toleratesBomAndCrLf() {
        val text = "﻿## Interface: 11507\r\n## Title: Foo\r\n## X-Curse-Project-ID: 1234\r\n"
        val toc = TocParser.parse("Foo", "Foo.toc", text)
        assertEquals(listOf(11507), toc.interfaces)
        assertEquals("Foo", toc.title)
        assertEquals("1234", toc.curseId)
    }

    @Test
    fun parsesDependenciesAndIds() {
        val text = """
            ## Title: DBM StatusBarTimers
            ## Dependencies: DBM-Core, LibStub
            ## RequiredDeps: Ace3
            ## X-Wago-ID: abCD12xy
            ## X-WoWI-ID: 8814
            ## x-curse-project-id: 3358
            Core.lua
        """.trimIndent()
        val toc = TocParser.parse("DBM-StatusBarTimers", "DBM-StatusBarTimers.toc", text)
        assertEquals(listOf("DBM-Core", "LibStub", "Ace3"), toc.dependencies)
        assertEquals("abCD12xy", toc.wagoId)
        assertEquals("8814", toc.wowiId)
        assertEquals("3358", toc.curseId)
    }

    @Test
    fun suffixFlavors() {
        assertEquals(AddonFlavor.FOREVER, FlavorRules.ofTocFileName("Foo_Forever.toc"))
        assertEquals(AddonFlavor.FOREVER, FlavorRules.ofTocFileName("Foo-Camelot.toc"))
        assertEquals(AddonFlavor.CLASSIC, FlavorRules.ofTocFileName("Foo-Vanilla.toc"))
        assertEquals(AddonFlavor.CLASSIC, FlavorRules.ofTocFileName("Foo_Classic.toc"))
        assertNull(FlavorRules.ofTocFileName("Foo.toc"))
        assertNull(FlavorRules.ofTocFileName("Foo_Mainline.toc"))
    }

    @Test
    fun pickTocPrefersForeverThenPlainThenClassic() {
        val dir = File(tmp, "Foo").apply { mkdirs() }
        File(dir, "Foo_Vanilla.toc").writeText("## Title: vanilla\n")
        File(dir, "Foo_Mainline.toc").writeText("## Title: mainline\n")
        assertEquals("Foo_Vanilla.toc", FlavorRules.pickToc(dir)!!.fileName)
        File(dir, "Foo.toc").writeText("## Title: plain\n")
        assertEquals("Foo.toc", FlavorRules.pickToc(dir)!!.fileName)
        File(dir, "Foo_Forever.toc").writeText("## Title: forever\n")
        val picked = FlavorRules.pickToc(dir)!!
        assertEquals("Foo_Forever.toc", picked.fileName)
        assertEquals("forever", picked.title)
        assertEquals("Foo", picked.folder)
    }

    @Test
    fun pickTocReturnsNullWithoutUsableToc() {
        val dir = File(tmp, "Bar").apply { mkdirs() }
        File(dir, "Bar_Mainline.toc").writeText("## Title: x\n")
        assertNull(FlavorRules.pickToc(dir))
    }

    @Test
    fun classifyPlainClassicInterface() {
        val toc = TocParser.parse("Foo", "Foo.toc", "## Interface: 11507\n")
        assertEquals(AddonFlavor.CLASSIC, FlavorRules.classify(listOf(toc)))
    }

    @Test
    fun classifyForeverInterfaceAndSuffix() {
        val plain = TocParser.parse("Foo", "Foo.toc", "## Interface: 11507\n")
        val forever = TocParser.parse("Foo", "Foo_Forever.toc", "## Interface: 11507\n")
        assertEquals(AddonFlavor.FOREVER, FlavorRules.classify(listOf(plain, forever)))
        val iface = TocParser.parse("Foo", "Foo.toc", "## Interface: 11507, $FOREVER_INTERFACE\n")
        assertEquals(AddonFlavor.FOREVER, FlavorRules.classify(listOf(iface)))
        val retail = TocParser.parse("Foo", "Foo.toc", "## Interface: 120100\n")
        assertEquals(AddonFlavor.OTHER, FlavorRules.classify(listOf(retail)))
    }
}
