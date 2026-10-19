package app.gamenative.wow.addons.provider

import app.gamenative.wow.addons.AddonFlavor
import app.gamenative.wow.addons.SourceRef
import app.gamenative.wow.addons.provider.Fixtures.base
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CurseForgeProviderTest {
    private val server = MockWebServer()
    private lateinit var provider: CurseForgeProvider

    @Before
    fun setUp() {
        server.dispatcher = Fixtures.dispatcher { req ->
            val search = Fixtures.load("cf-search.schema.json", server.base())
            when {
                req.url.encodedPath == "/v1/mods/search" -> Fixtures.json(search)
                req.url.encodedPath == "/v1/mods/334372" -> {
                    val questie = JSONObject(search).getJSONArray("data").getJSONObject(0)
                    Fixtures.json(JSONObject().put("data", questie).toString())
                }
                req.url.encodedPath == "/v1/mods" && req.method == "POST" -> Fixtures.json(search)
                else -> null
            }
        }
        server.start()
        provider = CurseForgeProvider("test-key", OkHttpClient(), apiBase = server.base())
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun searchMapsFieldsAndSendsKey() = runBlocking {
        val results = provider.search("questie", 1)

        val req = server.takeRequest()
        assertEquals("test-key", req.headers["x-api-key"])
        assertEquals("1", req.url.queryParameter("gameId"))
        assertEquals("questie", req.url.queryParameter("searchFilter"))
        assertEquals("2", req.url.queryParameter("sortField"))
        assertEquals("desc", req.url.queryParameter("sortOrder"))
        assertEquals("50", req.url.queryParameter("index"))
        assertEquals("50", req.url.queryParameter("pageSize"))

        // The retail-only mod has no Forever or Classic file and is dropped.
        assertEquals(listOf("334372", "4444"), results.map { it.projectId })
        val questie = results.first()
        assertEquals("curseforge", questie.provider)
        assertEquals("Questie", questie.name)
        assertEquals("AeroScripts", questie.author)
        assertEquals(90_000_000L, questie.downloads)
        assertEquals("The ultimate WoW Classic quest helper", questie.summary)
        assertEquals("https://media.forgecdn.net/avatars/thumbnails/questie.png", questie.iconUrl)
        assertEquals("https://www.curseforge.com/wow/addons/questie", questie.websiteUrl)
    }

    @Test
    fun picksForeverReleaseFileOverClassic() = runBlocking {
        val questie = provider.get("334372")!!

        assertEquals(setOf(AddonFlavor.FOREVER, AddonFlavor.CLASSIC), questie.latest.keys)
        val forever = questie.latest.getValue(AddonFlavor.FOREVER)
        // The stable release wins over the newer beta.
        assertEquals("5001", forever.fileId)
        assertEquals("Questie 10.0.1 Forever", forever.version)
        assertEquals("${server.base()}/files/5001/Questie-10.0.1-forever.zip", forever.downloadUrl)
        // Only required dependencies (relationType 3).
        assertEquals(listOf(SourceRef("curseforge", "9999")), forever.dependencies)
        assertEquals("5000", questie.latest.getValue(AddonFlavor.CLASSIC).fileId)
    }

    @Test
    fun disabledDistributionIsNotInstallable() = runBlocking {
        val locked = provider.search("locked", 0).single { it.projectId == "4444" }
        val file = locked.latest.getValue(AddonFlavor.CLASSIC)
        assertEquals("", file.downloadUrl)
        assertFalse(file.installable)
        assertEquals("https://www.curseforge.com/wow/addons/locked-addon", locked.websiteUrl)
    }

    @Test
    fun getManyPostsIds() = runBlocking {
        val results = provider.getMany(listOf("334372", "4444"))
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.body!!.utf8().contains("334372"))
        // getMany keeps mods without a Forever/Classic file (with empty `latest`) so installed
        // addons can still be shown.
        assertEquals(listOf("334372", "4444", "5555"), results.map { it.projectId })
        assertTrue(results.last().latest.isEmpty())
    }

    @Test
    fun unknownModIsNull() = runBlocking {
        assertNull(provider.get("1"))
    }

    @Test
    fun blankKeyDisablesWithoutHttp() = runBlocking {
        val disabled = CurseForgeProvider(" ", OkHttpClient(), apiBase = server.base())
        assertFalse(disabled.enabled)
        assertTrue(disabled.search("questie", 0).isEmpty())
        assertTrue(disabled.popular(0).isEmpty())
        assertNull(disabled.get("334372"))
        assertTrue(disabled.getMany(listOf("334372")).isEmpty())
        assertEquals(0, server.requestCount)
    }
}
