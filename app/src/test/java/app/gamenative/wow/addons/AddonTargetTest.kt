package app.gamenative.wow.addons

import app.gamenative.wow.addons.provider.CurseForgeProvider
import app.gamenative.wow.addons.provider.Fixtures
import app.gamenative.wow.addons.provider.Fixtures.base
import app.gamenative.wow.addons.provider.GitHubProvider
import app.gamenative.wow.addons.provider.WagoProvider
import app.gamenative.wow.addons.provider.WowInterfaceProvider
import app.gamenative.wow.product.GameVersion
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each game version's addon target picks that version's files, and Forever never shows another version's. */
class AddonTargetTest {
    private val server = MockWebServer()
    private val queries = CopyOnWriteArrayList<String>()

    private fun serve(vararg routes: Pair<String, String>) {
        server.dispatcher = Fixtures.dispatcher { req ->
            req.url.queryParameter("game_version")?.let { queries += it }
            routes.firstOrNull { it.first == req.url.encodedPath }?.let { Fixtures.json(Fixtures.load(it.second, server.base())) }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun curseForgePicksRetailFilesForRetail() = runBlocking {
        serve("/v1/mods/search" to "cf-search.schema.json")
        val retail = CurseForgeProvider("k", OkHttpClient(), apiBase = server.base(), target = GameVersion.RETAIL.addonTarget())
        val found = retail.search("", 0)
        assertEquals(listOf("5555"), found.map { it.projectId })
        assertEquals(setOf(AddonFlavor.MAIN), found.single().latest.keys)
    }

    @Test
    fun foreverTargetDropsOtherVersions() = runBlocking {
        serve("/v1/mods/search" to "cf-search.schema.json")
        val forever = CurseForgeProvider("k", OkHttpClient(), apiBase = server.base(), target = GameVersion.FOREVER.addonTarget())
        assertTrue(forever.search("", 0).none { it.projectId == "5555" })
    }

    @Test
    fun wowInterfaceMapsCompatibilityByVersion() = runBlocking {
        serve("/filelist.json" to "wowi-filelist.json")
        fun provider(v: GameVersion) = WowInterfaceProvider(OkHttpClient(), apiBase = server.base(), target = v.addonTarget())
        assertEquals(setOf(AddonFlavor.MAIN), provider(GameVersion.RETAIL).search("atlas", 0).single().latest.keys)
        assertEquals(setOf(AddonFlavor.MAIN), provider(GameVersion.ANNIVERSARY).search("atlas", 0).single().latest.keys)
        assertEquals(setOf(AddonFlavor.FALLBACK), provider(GameVersion.FOREVER).search("atlas", 0).single().latest.keys)
        assertTrue(provider(GameVersion.MISTS).search("atlas", 0).isEmpty())
    }

    @Test
    fun wagoAsksOnlyForTheSelectedVersion() = runBlocking {
        serve()
        WagoProvider("k", OkHttpClient(), apiBase = server.base(), target = GameVersion.RETAIL.addonTarget()).search("x", 0)
        assertEquals(listOf("retail"), queries.distinct())
    }

    @Test
    fun gitHubCatalogUsesTheVersionsKey() = runBlocking {
        serve("/catalog.json" to "github-catalog.json")
        val retail = GitHubProvider(OkHttpClient(), apiBase = server.base(), catalogUrl = "${server.base()}/catalog.json", target = GameVersion.RETAIL.addonTarget())
        val mapster = retail.popular(0).single()
        assertEquals("Nevcairiel/Mapster", mapster.projectId)
        assertTrue(mapster.latest.getValue(AddonFlavor.MAIN).downloadUrl.endsWith("-mainline.zip"))
    }

    @Test
    fun tocRulesFollowTheTarget() {
        val folder = File(Files.createTempDirectory("addon").toFile(), "Foo").apply { mkdirs() }
        File(folder, "Foo.toc").writeText("## Interface: 11507\n## Title: Foo\n")
        File(folder, "Foo_Mainline.toc").writeText("## Interface: 120105\n## Title: Foo\n")
        val retail = GameVersion.RETAIL.addonTarget()
        assertEquals("Foo_Mainline.toc", FlavorRules.pickToc(folder, retail)!!.fileName)
        assertEquals("Foo.toc", FlavorRules.pickToc(folder)!!.fileName)
        assertEquals(AddonFlavor.MAIN, FlavorRules.classify(FlavorRules.readTocs(folder), retail))
        assertEquals(AddonFlavor.FALLBACK, FlavorRules.classify(FlavorRules.readTocs(folder)))
    }

    @Test
    fun classicTocServesAnniversaryAndMists() {
        val folder = File(Files.createTempDirectory("addon").toFile(), "Bar").apply { mkdirs() }
        File(folder, "Bar_Mainline.toc").writeText("## Interface: 120105\n## Title: Bar\n")
        File(folder, "Bar_Classic.toc").writeText("## Interface: 20506\n## Title: Bar\n")
        for (v in listOf(GameVersion.ANNIVERSARY, GameVersion.MISTS)) {
            assertEquals("Bar_Classic.toc", FlavorRules.pickToc(folder, v.addonTarget())!!.fileName)
            assertEquals(AddonFlavor.MAIN, FlavorRules.classify(FlavorRules.readTocs(folder), v.addonTarget()))
        }
        File(folder, "Bar_TBC.toc").writeText("## Interface: 20506\n## Title: Bar\n")
        assertEquals("Bar_TBC.toc", FlavorRules.pickToc(folder, GameVersion.ANNIVERSARY.addonTarget())!!.fileName)
    }
}
