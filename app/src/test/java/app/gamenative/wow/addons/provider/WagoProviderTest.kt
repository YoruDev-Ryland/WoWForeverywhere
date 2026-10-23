package app.gamenative.wow.addons.provider

import app.gamenative.wow.addons.AddonFlavor
import app.gamenative.wow.addons.provider.Fixtures.base
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WagoProviderTest {
    private val server = MockWebServer()
    private val requests = mutableListOf<RecordedRequest>()
    private lateinit var provider: WagoProvider

    @Before
    fun setUp() {
        server.dispatcher = Fixtures.dispatcher { req ->
            synchronized(requests) { requests += req }
            val flavor = req.url.queryParameter("game_version")
            val base = server.base()
            when (req.url.encodedPath) {
                "/addons/_search", "/addons/popular" -> when (flavor) {
                    "forever" -> Fixtures.json(Fixtures.load("wago-search-forever.schema.json", base))
                    "classic" -> Fixtures.json(Fixtures.load("wago-search-classic.schema.json", base))
                    else -> null
                }
                "/addons/q1w2e3r4" -> Fixtures.json(
                    Fixtures.load("wago-addon.schema.json", base).replace("{{LABEL}}", "$flavor-1.0"),
                )
                "/addons/_recents" -> {
                    val body = req.body!!.utf8()
                    val gv = if ("\"forever\"" in body) "forever" else "classic"
                    Fixtures.json(Fixtures.load("wago-recents.schema.json", base).replace("{{LABEL}}", "$gv-2.0"))
                }
                else -> null
            }
        }
        server.start()
        provider = WagoProvider("secret-wago-key-0123456789", OkHttpClient(), apiBase = server.base())
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun searchMapsFieldsAndMergesFlavors() = runBlocking {
        val results = provider.search("questie", 0)

        assertEquals(listOf("q1w2e3r4", "oldtimer1"), results.map { it.projectId })
        val questie = results.first()
        assertEquals("wago", questie.provider)
        assertEquals("Questie", questie.name)
        assertEquals("AeroScripts, Logon", questie.author)
        assertEquals(120_000L, questie.downloads)
        assertEquals("https://addons.wago.io/addons/questie", questie.websiteUrl)

        val req = requests.first { it.url.encodedPath == "/addons/_search" }
        assertEquals("Bearer secret-wago-key-0123456789", req.headers["Authorization"])
        assertEquals("questie", req.url.queryParameter("query"))
        assertEquals(setOf("forever", "classic"), requests.mapNotNull { it.url.queryParameter("game_version") }.toSet())
    }

    @Test
    fun picksForeverFileAndStableRelease() = runBlocking {
        val questie = provider.search("questie", 0).first()
        assertEquals(setOf(AddonFlavor.MAIN, AddonFlavor.FALLBACK), questie.latest.keys)
        val forever = questie.latest.getValue(AddonFlavor.MAIN)
        assertEquals("10.0.1-forever", forever.version)
        assertEquals("10.0.1-forever", forever.fileId)
        assertEquals("${server.base()}/wago/q1w2e3r4/forever.zip", forever.downloadUrl)
        // Stable is preferred over a newer beta.
        assertEquals("10.0.0", questie.latest.getValue(AddonFlavor.FALLBACK).version)

        // A classic-only addon with only an alpha release still lists it.
        val old = provider.search("questie", 0).last()
        assertEquals(setOf(AddonFlavor.FALLBACK), old.latest.keys)
    }

    @Test
    fun getReadsDetailsPerFlavor() = runBlocking {
        val questie = provider.get("q1w2e3r4")!!
        assertEquals("forever-1.0", questie.latest.getValue(AddonFlavor.MAIN).version)
        assertEquals("classic-1.0", questie.latest.getValue(AddonFlavor.FALLBACK).version)
        assertEquals("${server.base()}/wago/q1w2e3r4/forever-1.0.zip", questie.latest.getValue(AddonFlavor.MAIN).downloadUrl)
        assertNull(provider.get("missing1"))
    }

    @Test
    fun getManyUsesRecents() = runBlocking {
        val results = provider.getMany(listOf("q1w2e3r4"))
        val questie = results.single()
        assertEquals("forever-2.0", questie.latest.getValue(AddonFlavor.MAIN).fileId)
        assertEquals("classic-2.0", questie.latest.getValue(AddonFlavor.FALLBACK).fileId)
        assertTrue(requests.all { it.method == "POST" })
    }

    @Test
    fun blankKeyDisablesWithoutHttp() = runBlocking {
        val disabled = WagoProvider("", OkHttpClient(), apiBase = server.base())
        assertFalse(disabled.enabled)
        assertTrue(disabled.search("questie", 0).isEmpty())
        assertTrue(disabled.popular(0).isEmpty())
        assertNull(disabled.get("q1w2e3r4"))
        assertTrue(disabled.getMany(listOf("q1w2e3r4")).isEmpty())
        assertEquals(0, server.requestCount)
    }
}
