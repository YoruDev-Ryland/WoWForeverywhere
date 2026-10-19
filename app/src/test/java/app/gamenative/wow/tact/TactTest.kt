package app.gamenative.wow.tact

import app.gamenative.wow.tact.TactFixtures.FileSpec
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TactTest {

    private val servers = mutableListOf<MockWebServer>()

    @After
    fun tearDown() = servers.forEach { it.shutdown() }

    @Test
    fun blteRoundTrips() {
        val a = "hello world".toByteArray()
        val b = ByteArray(10_000) { (it % 7).toByte() }
        assertArrayEquals(a, Blte.decode(TactFixtures.blte(listOf(a), 'N', headerless = true)))
        assertArrayEquals(a + b, Blte.decode(TactFixtures.blte(listOf(a, b), 'N')))
        assertArrayEquals(a + b, Blte.decode(TactFixtures.blte(listOf(a, b), 'Z')))
    }

    @Test(expected = IOException::class)
    fun blteRejectsUnknownMode() {
        Blte.decode("BLTE".toByteArray() + ByteArray(4) + byteArrayOf('X'.code.toByte(), 1, 2))
    }

    @Test(expected = IOException::class)
    fun blteRejectsNonBlte() {
        Blte.decode("nope".toByteArray())
    }

    @Test
    fun installManifestParsesNamesSizesAndTags() {
        val entries = InstallManifest.parse(
            TactFixtures.installManifest(
                listOf(
                    FileSpec("Wow-arm64.exe", ByteArray(100), setOf("Windows", "arm64")),
                    FileSpec("Wow.exe", ByteArray(200), setOf("Windows", "x86_64")),
                    FileSpec("Utils\\Foo.dll", ByteArray(3), setOf("Windows")),
                ),
            ),
        )
        assertEquals(listOf("Wow-arm64.exe", "Wow.exe", "Utils\\Foo.dll"), entries.map { it.name })
        assertEquals(listOf(100L, 200L, 3L), entries.map { it.size })
        assertEquals(setOf("Windows", "arm64"), entries[0].tags)
        assertEquals("Utils/Foo.dll", entries[2].relativePath)
        assertEquals(TactFixtures.md5(ByteArray(100)), entries[0].ckey)
    }

    @Test
    fun archiveIndexLocates() {
        val key = "0123456789abcdef0123456789abcdef"
        val index = ArchiveIndex(TactFixtures.archiveIndex(mapOf(key to (4096 to 77), "f".repeat(32) to (0 to 1))))
        assertEquals(4096L to 77L, index.locate(key))
        assertNull(index.locate("a".repeat(32)))
    }

    private fun server(dispatch: (RecordedRequest) -> MockResponse) = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = dispatch(request)
        }
        start()
        servers += this
    }

    private fun hostOf(s: MockWebServer) = "${s.hostName}:${s.port}"

    @Test
    fun cdnFallsBackToNextHostOnServerError() {
        val bad = server { MockResponse().setResponseCode(500) }
        val good = server { MockResponse().setBody("ok") }
        val cdn = Cdn(listOf(hostOf(bad), hostOf(good)), "tpr/wow", OkHttpClient())
        assertEquals("ok", String(cdn.fetch("config", "abcdef")!!))
        assertEquals("/tpr/wow/config/ab/cd/abcdef", good.takeRequest().path)
    }

    @Test
    fun cdnReturnsNullOnNotFound() {
        val s = server { MockResponse().setResponseCode(404) }
        assertNull(Cdn(listOf(hostOf(s)), "tpr/wow", OkHttpClient()).fetch("data", "abcdef"))
    }

    @Test
    fun cdnSendsRange() {
        val s = server { MockResponse().setBody("x") }
        Cdn(listOf(hostOf(s)), "tpr/wow", OkHttpClient()).fetch("data", "abcdef", 10L until 20L)
        assertEquals("bytes=10-19", s.takeRequest().getHeader("Range"))
    }

    @Test
    fun encodingLookupFindsKeys() {
        val c1 = "11".repeat(16)
        val e1 = "aa".repeat(16)
        val c2 = "22".repeat(16)
        val e2 = "bb".repeat(16)
        val blte = TactFixtures.blte(listOf(TactFixtures.encodingFile(mapOf(c1 to e1, c2 to e2))), 'N')
        val ekey = "ee".repeat(16)
        val s = server { request ->
            val range = request.getHeader("Range")!!.removePrefix("bytes=").split("-").map { it.toInt() }
            MockResponse().setBody(okio.Buffer().write(blte.copyOfRange(range[0], range[1] + 1)))
        }
        val found = EncodingLookup(Cdn(listOf(hostOf(s)), "tpr/wow", OkHttpClient()), ekey).find(setOf(c1, c2, "33".repeat(16)))
        assertEquals(mapOf(c1 to e1, c2 to e2), found)
    }
}
