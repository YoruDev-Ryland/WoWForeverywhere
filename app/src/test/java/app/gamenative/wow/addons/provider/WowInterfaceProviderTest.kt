package app.gamenative.wow.addons.provider

import app.gamenative.wow.addons.AddonCategory
import app.gamenative.wow.addons.AddonFlavor
import app.gamenative.wow.addons.provider.Fixtures.base
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WowInterfaceProviderTest {
    private val server = MockWebServer()
    private var now = 1_000_000L

    private fun start(filelist: String): WowInterfaceProvider {
        server.dispatcher = Fixtures.dispatcher { req ->
            when (req.url.encodedPath) {
                "/filelist.json" -> Fixtures.json(Fixtures.load(filelist))
                "/filedetails/3896.json" -> Fixtures.json(Fixtures.load("wowi-filedetails-3896.json"))
                else -> null
            }
        }
        server.start()
        return WowInterfaceProvider(OkHttpClient(), apiBase = server.base(), clock = { now })
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun searchMapsRealFilelist() = runBlocking {
        val provider = start("wowi-filelist.json")
        assertTrue(provider.enabled)

        val results = provider.search("atlas", 0)

        val atlas = results.single()
        assertEquals("wowinterface", atlas.provider)
        assertEquals("3896", atlas.projectId)
        assertEquals("Atlas", atlas.name)
        assertEquals("arith", atlas.author)
        assertEquals(4_727_561L, atlas.downloads)
        assertEquals("https://www.wowinterface.com/downloads/info3896-Atlas.html", atlas.websiteUrl)
        assertEquals("https://cdn-wow.mmoui.com/preview/tiny/pvw51580.png", atlas.iconUrl)
        // Atlas lists Classic 1.15.7 (plus retail versions that are ignored).
        assertEquals(setOf(AddonFlavor.FALLBACK), atlas.latest.keys)
        assertEquals("v1.54.03", atlas.latest.getValue(AddonFlavor.FALLBACK).version)
    }

    @Test
    fun popularSortsByDownloadsAndDropsRetailOnly() = runBlocking {
        val provider = start("wowi-filelist.json")
        val names = provider.popular(0).map { it.name }
        // Carbonite (most downloads) is retail-only and is dropped.
        assertEquals(listOf("Atlas", "TomTom", "Deadly Boss Mods"), names)
        assertTrue(provider.popular(1).isEmpty())
    }

    @Test
    fun filelistIsCachedForAnHour() = runBlocking {
        val provider = start("wowi-filelist.json")
        provider.search("atlas", 0)
        provider.popular(0)
        assertEquals(1, server.requestCount)
        now += 61 * 60 * 1000
        provider.popular(0)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun foreverCompatibilityWinsOverClassic() = runBlocking {
        val provider = start("wowi-filelist-forever.schema.json")
        val results = provider.popular(0)
        assertEquals(listOf("Classic Only Thing", "Forever Questing"), results.map { it.name })
        val forever = results.last()
        assertEquals(setOf(AddonFlavor.MAIN, AddonFlavor.FALLBACK), forever.latest.keys)
        assertEquals(setOf(AddonFlavor.FALLBACK), results.first().latest.keys)
    }

    @Test
    fun getUsesFileDetailsForDownload() = runBlocking {
        val provider = start("wowi-filelist.json")
        val atlas = provider.get("3896")!!
        val file = atlas.latest.getValue(AddonFlavor.FALLBACK)
        assertEquals("https://cdn.wowinterface.com/downloads/getfile.php?id=3896&d=1792311003&minion", file.downloadUrl)
        assertEquals("v1.54.03", file.version)
        assertNull(provider.get("424242"))
    }

    @Test
    fun listsCategoriesAndScreenshots() = runBlocking {
        val provider = start("wowi-filelist.json")
        val atlas = provider.search("Atlas", 0).first { it.projectId == "3896" }
        assertEquals(setOf(AddonCategory.MAP), atlas.categories)
        assertTrue(atlas.screenshots.isNotEmpty())
    }

    @Test
    fun popularFiltersByCategory() = runBlocking {
        val provider = start("wowi-filelist.json")
        val maps = provider.popular(0, AddonCategory.MAP)
        assertTrue(maps.isNotEmpty())
        assertTrue(maps.all { AddonCategory.MAP in it.categories })
        assertTrue(provider.popular(0, AddonCategory.RAIDS).none { it.projectId == "3896" })
    }

    @Test
    fun descriptionIsPlainText() = runBlocking {
        val provider = start("wowi-filelist.json")
        val text = provider.description("3896")!!
        assertTrue(text.startsWith("Make a Donation via Paypal"))
        assertTrue("[COLOR" !in text && "[URL" !in text && "\r" !in text)
        assertTrue("About Atlas" in text)
    }
}
