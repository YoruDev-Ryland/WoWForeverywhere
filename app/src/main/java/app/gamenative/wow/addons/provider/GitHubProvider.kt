package app.gamenative.wow.addons.provider

import app.gamenative.utils.Net
import app.gamenative.wow.addons.AddonCategory
import app.gamenative.wow.addons.AddonFlavor
import app.gamenative.wow.addons.ProviderIds
import app.gamenative.wow.addons.SourceRef
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.OkHttpClient

/**
 * Addons from GitHub releases; `projectId` is `owner/repo`.
 *
 * Browsing uses the app's addon catalog: one JSON file, rebuilt nightly by the "Addon catalog"
 * workflow from `catalog/sources.json`, listing popular addons with their Forever/Classic files.
 * Catalog addons need no GitHub API calls, which matters because the API allows only 60 keyless
 * requests an hour per device. Repos outside the catalog (installed from a link) use the API.
 *
 * Asset selection follows WowUp's GitHub provider: when the release has a BigWigs packager
 * `release.json`, its `metadata[].flavor` decides; otherwise zip names ending in
 * `-forever`/`-camelot` or `-classic`/`-vanilla` decide; a lone generic zip maps to OTHER.
 */
class GitHubProvider(
    private val http: OkHttpClient = Net.http,
    private val apiBase: String = "https://api.github.com",
    private val catalogUrl: String = CATALOG_URL,
    private val clock: () -> Long = System::currentTimeMillis,
) : AddonProvider {
    override val id: String = ProviderIds.GITHUB
    override val enabled: Boolean = true

    private val catalogLock = Mutex()
    private var catalog: List<RemoteAddon>? = null
    private var catalogAt = 0L

    override suspend fun search(query: String, page: Int): List<RemoteAddon> {
        if (page > 0) return emptyList()
        val needle = query.trim().lowercase()
        return catalog().filter { needle.isEmpty() || needle in it.name.lowercase() || needle in it.projectId.lowercase() }
    }

    override suspend fun popular(page: Int): List<RemoteAddon> = if (page > 0) emptyList() else catalog()

    override suspend fun popular(page: Int, category: AddonCategory?): List<RemoteAddon> =
        popular(page).filter { category == null || category in it.categories }

    override suspend fun get(projectId: String): RemoteAddon? {
        catalog().firstOrNull { it.projectId.equals(projectId, ignoreCase = true) }?.let { return it }
        return fromApi(projectId)
    }

    /** The catalog, refreshed every [CATALOG_TTL_MS]; empty (not an error) when it can't be fetched. */
    private suspend fun catalog(): List<RemoteAddon> = catalogLock.withLock {
        val now = clock()
        catalog?.takeIf { now - catalogAt < CATALOG_TTL_MS }?.let { return@withLock it }
        val parsed = runCatching { http.fetchText(catalogUrl)?.let { ProviderJson.decodeFromString<Catalog>(it) } }.getOrNull()
            ?: return@withLock catalog.orEmpty()
        parsed.addons.map { it.toRemote() }.also {
            catalog = it
            catalogAt = now
        }
    }

    private fun CatalogAddon.toRemote(): RemoteAddon {
        val deps = requires.map { SourceRef(ProviderIds.GITHUB, it) }
        val latest = LinkedHashMap<AddonFlavor, RemoteFile>()
        for ((key, flavor) in listOf("forever" to AddonFlavor.MAIN, "classic" to AddonFlavor.FALLBACK)) {
            files[key]?.let { latest[flavor] = RemoteFile(it.fileId, it.version, it.url, deps) }
        }
        return RemoteAddon(
            provider = id,
            projectId = repo,
            name = name,
            summary = summary,
            author = author,
            iconUrl = icon,
            downloads = downloads,
            latest = latest,
            websiteUrl = website,
            categories = setOfNotNull(category?.let { c -> AddonCategory.entries.firstOrNull { it.name == c } }),
        )
    }

    private suspend fun fromApi(projectId: String): RemoteAddon? {
        val (owner, repo) = projectId.split('/').takeIf { it.size == 2 && it.all(String::isNotBlank) } ?: return null
        val body = http.fetchText("${apiBase.trimEnd('/')}/repos/$owner/$repo/releases/latest", headers) ?: return null
        val release = ProviderJson.decodeFromString<GhRelease>(body)
        val zips = release.assets.filter { it.isZip() && !it.name.contains("-nolib", ignoreCase = true) }
        val chosen = fromMetadata(release).ifEmpty { fromFileNames(zips) }
        val latest = chosen.mapValues { (_, asset) ->
            RemoteFile(
                fileId = asset.id.toString(),
                version = release.tagName ?: release.name ?: asset.name,
                downloadUrl = asset.browserDownloadUrl,
                dependencies = emptyList(),
            )
        }
        return RemoteAddon(
            provider = id,
            projectId = "$owner/$repo",
            name = repo,
            summary = release.name.orEmpty(),
            author = owner,
            iconUrl = "https://github.com/$owner.png",
            downloads = zips.sumOf { it.downloadCount },
            latest = latest,
            websiteUrl = "https://github.com/$owner/$repo",
        )
    }

    override suspend fun getMany(ids: List<String>): List<RemoteAddon> = ids.mapNotNull { get(it) }

    private suspend fun fromMetadata(release: GhRelease): Map<AddonFlavor, GhAsset> {
        val metaAsset = release.assets.firstOrNull { it.name == "release.json" } ?: return emptyMap()
        val meta = runCatching {
            http.fetchText(metaAsset.browserDownloadUrl)?.let { ProviderJson.decodeFromString<ReleaseMeta>(it) }
        }.getOrNull() ?: return emptyMap()
        val result = LinkedHashMap<AddonFlavor, GhAsset>()
        for (item in meta.releases.filter { !it.nolib }) {
            val asset = release.assets.firstOrNull { it.name == item.filename && it.isZip() } ?: continue
            for (flavor in item.metadata.mapNotNull { metadataFlavor(it.flavor) }) {
                result.putIfAbsent(flavor, asset)
            }
        }
        return result
    }

    private fun fromFileNames(zips: List<GhAsset>): Map<AddonFlavor, GhAsset> {
        val result = LinkedHashMap<AddonFlavor, GhAsset>()
        zips.firstOrNull { foreverName.containsMatchIn(it.name) }?.let { result[AddonFlavor.MAIN] = it }
        zips.firstOrNull { classicName.containsMatchIn(it.name) }?.let { result[AddonFlavor.FALLBACK] = it }
        if (result.isEmpty() && zips.size == 1) result[AddonFlavor.OTHER] = zips.single()
        return result
    }

    private fun GhAsset.isZip(): Boolean = contentType in zipTypes ||
        (contentType == "application/octet-stream" && name.endsWith(".zip", ignoreCase = true))

    @Serializable
    private data class Catalog(val addons: List<CatalogAddon> = emptyList())

    @Serializable
    private data class CatalogAddon(
        val repo: String,
        val name: String,
        val summary: String = "",
        val category: String? = null,
        val author: String = "",
        val icon: String? = null,
        val website: String? = null,
        val downloads: Long = 0,
        val requires: List<String> = emptyList(),
        val files: Map<String, CatalogFile> = emptyMap(),
    )

    @Serializable
    private data class CatalogFile(val fileId: String, val version: String, val url: String)

    @Serializable
    private data class GhRelease(
        @SerialName("tag_name") val tagName: String? = null,
        val name: String? = null,
        val assets: List<GhAsset> = emptyList(),
    )

    @Serializable
    private data class GhAsset(
        val id: Long,
        val name: String,
        @SerialName("content_type") val contentType: String = "",
        @SerialName("download_count") val downloadCount: Long = 0,
        @SerialName("browser_download_url") val browserDownloadUrl: String,
    )

    @Serializable
    private data class ReleaseMeta(val releases: List<ReleaseMetaItem> = emptyList())

    @Serializable
    private data class ReleaseMetaItem(
        val filename: String,
        val nolib: Boolean = false,
        val metadata: List<ReleaseMetaFlavor> = emptyList(),
    )

    @Serializable
    private data class ReleaseMetaFlavor(val flavor: String)

    companion object {
        const val CATALOG_URL = "https://raw.githubusercontent.com/YoruDev-Ryland/WoWForeverywhere/addon-catalog/catalog.json"
        private const val CATALOG_TTL_MS = 6 * 60 * 60 * 1000L
        private val headers = Headers.headersOf("Accept", "application/vnd.github+json")
        private val zipTypes = setOf("application/zip", "application/x-zip-compressed")
        private val foreverName = Regex("""[-_](forever|camelot)\.zip$""", RegexOption.IGNORE_CASE)
        private val classicName = Regex("""[-_](classic|vanilla)\.zip$""", RegexOption.IGNORE_CASE)
        private val repoUrl = Regex(
            """^(?:https?://)?(?:www\.)?github\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\.git)?(?:[/?#].*)?$""",
            RegexOption.IGNORE_CASE,
        )

        private fun metadataFlavor(flavor: String): AddonFlavor? = when (flavor.lowercase()) {
            "classic", "vanilla" -> AddonFlavor.FALLBACK
            "forever", "camelot" -> AddonFlavor.MAIN
            else -> null
        }

        /** `owner/repo` from a `github.com/owner/repo[...]` URL, or null when it isn't one. */
        fun fromUrl(url: String): String? {
            val match = repoUrl.find(url.trim()) ?: return null
            return "${match.groupValues[1]}/${match.groupValues[2]}"
        }
    }
}
