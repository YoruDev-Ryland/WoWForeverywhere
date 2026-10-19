package app.gamenative.wow.addons.provider

import app.gamenative.wow.addons.AddonFlavor
import app.gamenative.wow.addons.provider.Fixtures.base
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitHubProviderTest {
    private val server = MockWebServer()
    private val routes = mutableMapOf<String, MockResponse>()
    private lateinit var provider: GitHubProvider

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                routes[request.url.encodedPath] ?: MockResponse.Builder().code(404).body("{\"message\":\"Not Found\"}").build()
        }
        server.start()
        provider = GitHubProvider(OkHttpClient(), apiBase = server.base(), catalogUrl = "${server.base()}/no-catalog.json")
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun releaseJsonFlavorMapping() = runBlocking {
        routes["/repos/BigWigsMods/BigWigs/releases/latest"] = Fixtures.json(Fixtures.load("github-release-metadata.json", server.base()))
        routes["/dl/release.json"] = Fixtures.json(Fixtures.load("github-release.json"))

        val addon = provider.get("BigWigsMods/BigWigs")!!

        assertEquals("github", addon.provider)
        assertEquals("BigWigsMods/BigWigs", addon.projectId)
        assertEquals("BigWigs", addon.name)
        assertEquals("BigWigsMods", addon.author)
        assertEquals("https://github.com/BigWigsMods/BigWigs", addon.websiteUrl)
        assertEquals(setOf(AddonFlavor.FOREVER, AddonFlavor.CLASSIC), addon.latest.keys)
        val forever = addon.latest.getValue(AddonFlavor.FOREVER)
        assertEquals("103", forever.fileId)
        assertEquals("v400", forever.version)
        assertEquals("${server.base()}/dl/BigWigs-v400-b.zip", forever.downloadUrl)
        assertEquals("102", addon.latest.getValue(AddonFlavor.CLASSIC).fileId)
    }

    @Test
    fun filenameFallbackMapping() = runBlocking {
        routes["/repos/someone/Foo/releases/latest"] = Fixtures.json(Fixtures.load("github-release-filenames.json", server.base()))

        val addon = provider.get("someone/Foo")!!

        assertEquals(setOf(AddonFlavor.FOREVER, AddonFlavor.CLASSIC), addon.latest.keys)
        assertEquals("203", addon.latest.getValue(AddonFlavor.FOREVER).fileId)
        assertEquals("202", addon.latest.getValue(AddonFlavor.CLASSIC).fileId)
    }

    @Test
    fun singleGenericZipIsOther() = runBlocking {
        val release = """
            {"tag_name":"2.0","name":"Bar","assets":[
              {"id":301,"name":"Bar-2.0.zip","content_type":"application/zip","download_count":7,
               "browser_download_url":"${server.base()}/dl/Bar-2.0.zip"}]}
        """.trimIndent()
        routes["/repos/me/Bar/releases/latest"] = Fixtures.json(release)

        val addon = provider.get("me/Bar")!!

        assertEquals(setOf(AddonFlavor.OTHER), addon.latest.keys)
        assertEquals("301", addon.latest.getValue(AddonFlavor.OTHER).fileId)
        assertEquals(7L, addon.downloads)
    }

    @Test
    fun notFoundIsNull() = runBlocking {
        assertNull(provider.get("nobody/nothing"))
    }

    @Test
    fun searchAndPopularAreEmptyWithoutCatalog() = runBlocking {
        assertTrue(provider.search("bigwigs", 0).isEmpty())
        assertTrue(provider.popular(0).isEmpty())
    }

    @Test
    fun fromUrlVariants() {
        assertEquals("BigWigsMods/BigWigs", GitHubProvider.fromUrl("https://github.com/BigWigsMods/BigWigs"))
        assertEquals("BigWigsMods/BigWigs", GitHubProvider.fromUrl("https://github.com/BigWigsMods/BigWigs/"))
        assertEquals("BigWigsMods/BigWigs", GitHubProvider.fromUrl("https://github.com/BigWigsMods/BigWigs.git"))
        assertEquals("BigWigsMods/BigWigs", GitHubProvider.fromUrl("https://github.com/BigWigsMods/BigWigs/releases"))
        assertEquals("BigWigsMods/BigWigs", GitHubProvider.fromUrl("https://github.com/BigWigsMods/BigWigs/releases/tag/v400"))
        assertEquals("BigWigsMods/BigWigs", GitHubProvider.fromUrl("github.com/BigWigsMods/BigWigs"))
        assertEquals("BigWigsMods/BigWigs", GitHubProvider.fromUrl("http://www.github.com/BigWigsMods/BigWigs?tab=readme"))
        assertNull(GitHubProvider.fromUrl("https://github.com/BigWigsMods"))
        assertNull(GitHubProvider.fromUrl("https://gitlab.com/a/b"))
        assertNull(GitHubProvider.fromUrl("https://example.com/github.com/a/b"))
    }
}
