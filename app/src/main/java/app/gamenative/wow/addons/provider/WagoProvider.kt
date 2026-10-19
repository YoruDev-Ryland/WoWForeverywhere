package app.gamenative.wow.addons.provider

import app.gamenative.utils.Net
import app.gamenative.wow.addons.AddonFlavor
import app.gamenative.wow.addons.ProviderIds
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * Wago Addons external API, following WowUp's `WagoAddonProvider`. Needs a bearer key; a blank
 * key disables the provider. Every call is made once per flavor (`game_version=forever` and
 * `classic`) and the results are merged.
 *
 * Release ids aren't present in search results, so the release `label` is used as the file id
 * everywhere to keep update checks consistent.
 */
class WagoProvider(
    private val apiKey: String,
    private val http: OkHttpClient = Net.http,
    private val apiBase: String = "https://addons.wago.io/api/external",
) : AddonProvider {
    override val id: String = ProviderIds.WAGO
    override val enabled: Boolean = apiKey.isNotBlank()

    private val headers by lazy { Headers.headersOf("Authorization", "Bearer $apiKey", "Accept", "application/json") }
    private val base get() = apiBase.trimEnd('/')

    override suspend fun search(query: String, page: Int): List<RemoteAddon> =
        listing("$base/addons/_search", page, "query" to query)

    override suspend fun popular(page: Int): List<RemoteAddon> = listing("$base/addons/popular", page)

    override suspend fun get(projectId: String): RemoteAddon? {
        if (!enabled || projectId.isBlank() || '/' in projectId) return null
        val perFlavor = forEachFlavor { gameVersion ->
            val url = "$base/addons/$projectId".toHttpUrl().newBuilder().addQueryParameter("game_version", gameVersion).build()
            http.fetchText(url.toString(), headers)?.let { ProviderJson.decodeFromString<WagoAddon>(it) }
        }
        val first = perFlavor.values.filterNotNull().firstOrNull() ?: return null
        val latest = perFlavor.mapNotNull { (flavor, addon) ->
            val release = addon?.let { pickRelease(it.recentReleases ?: it.recentRelease.orEmpty()) } ?: return@mapNotNull null
            flavor to release.toFile()
        }.toMap()
        return RemoteAddon(
            provider = id,
            projectId = first.id,
            name = first.displayName,
            summary = first.summary.orEmpty(),
            author = first.authors.joinToString(", "),
            iconUrl = first.thumbnailImage,
            downloads = first.downloadCount,
            latest = latest,
            websiteUrl = first.websiteUrl,
        )
    }

    override suspend fun getMany(ids: List<String>): List<RemoteAddon> {
        if (!enabled || ids.isEmpty()) return emptyList()
        val perFlavor = forEachFlavor { gameVersion ->
            val request = buildJsonObject {
                put("game_version", gameVersion)
                putJsonArray("addons") { ids.forEach { add(JsonPrimitive(it)) } }
            }
            http.fetchText("$base/addons/_recents", headers, postJson = request.toString())
                ?.let { ProviderJson.decodeFromString<WagoRecents>(it).addons }
                .orEmpty()
        }
        return ids.mapNotNull { addonId ->
            val matches = perFlavor.mapValues { it.value[addonId] }
            val first = matches.values.filterNotNull().firstOrNull() ?: return@mapNotNull null
            val latest = matches.mapNotNull { (flavor, addon) ->
                val release = addon?.let { pickRelease(it.recentReleases) } ?: return@mapNotNull null
                flavor to release.toFile()
            }.toMap()
            RemoteAddon(
                provider = id,
                projectId = first.id,
                name = first.name,
                summary = "",
                author = first.authors.joinToString(", "),
                iconUrl = first.thumbnail,
                downloads = 0,
                latest = latest,
                websiteUrl = first.websiteUrl,
            )
        }
    }

    private suspend fun listing(url: String, page: Int, vararg params: Pair<String, String>): List<RemoteAddon> {
        if (!enabled) return emptyList()
        val perFlavor = forEachFlavor { gameVersion ->
            val builder = url.toHttpUrl().newBuilder()
            params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
            builder.addQueryParameter("game_version", gameVersion).addQueryParameter("stability", "stable")
            if (page > 0) builder.addQueryParameter("page", (page + 1).toString())
            http.fetchText(builder.build().toString(), headers)
                ?.let { ProviderJson.decodeFromString<WagoSearch>(it).data }
                .orEmpty()
        }
        val merged = LinkedHashMap<String, RemoteAddon>()
        for ((flavor, items) in perFlavor) {
            for (item in items) {
                val file = pickRelease(item.releases)?.toFile()
                val existing = merged[item.id]
                val base = existing ?: RemoteAddon(
                    provider = id,
                    projectId = item.id,
                    name = item.displayName,
                    summary = item.summary.orEmpty(),
                    author = item.authors.joinToString(", "),
                    iconUrl = item.thumbnailImage,
                    downloads = item.downloadCount,
                    latest = emptyMap(),
                    websiteUrl = item.websiteUrl,
                )
                merged[item.id] = if (file == null) base else base.copy(latest = base.latest + (flavor to file))
            }
        }
        return merged.values.toList()
    }

    /** Runs [block] for the Forever and Classic game versions in parallel, in that order. */
    private suspend fun <T> forEachFlavor(block: suspend (String) -> T): Map<AddonFlavor, T> = coroutineScope {
        FLAVORS.map { (flavor, gameVersion) -> async { flavor to block(gameVersion) } }.awaitAll().toMap()
    }

    /** Stable first, then beta, then alpha. */
    private fun pickRelease(releases: Map<String, WagoRelease>): WagoRelease? =
        listOf("stable", "beta", "alpha").firstNotNullOfOrNull { releases[it] }
            ?.takeIf { !it.downloadUrl.isNullOrBlank() }

    private fun WagoRelease.toFile() = RemoteFile(
        fileId = label,
        version = label,
        downloadUrl = downloadUrl.orEmpty(),
        dependencies = emptyList(),
    )

    @Serializable
    private data class WagoSearch(val data: List<WagoSearchItem> = emptyList())

    @Serializable
    private data class WagoSearchItem(
        val id: String,
        @SerialName("display_name") val displayName: String,
        val summary: String? = null,
        @SerialName("thumbnail_image") val thumbnailImage: String? = null,
        val authors: List<String> = emptyList(),
        @SerialName("download_count") val downloadCount: Long = 0,
        @SerialName("website_url") val websiteUrl: String? = null,
        val releases: Map<String, WagoRelease> = emptyMap(),
    )

    @Serializable
    private data class WagoAddon(
        val id: String,
        @SerialName("display_name") val displayName: String,
        val summary: String? = null,
        @SerialName("thumbnail_image") val thumbnailImage: String? = null,
        val authors: List<String> = emptyList(),
        @SerialName("download_count") val downloadCount: Long = 0,
        @SerialName("website_url") val websiteUrl: String? = null,
        @SerialName("recent_releases") val recentReleases: Map<String, WagoRelease>? = null,
        @SerialName("recent_release") val recentRelease: Map<String, WagoRelease>? = null,
    )

    @Serializable
    private data class WagoRecents(val addons: Map<String, WagoScanAddon> = emptyMap())

    @Serializable
    private data class WagoScanAddon(
        val id: String,
        val name: String,
        val thumbnail: String? = null,
        @SerialName("website_url") val websiteUrl: String? = null,
        val authors: List<String> = emptyList(),
        @SerialName("recent_releases") val recentReleases: Map<String, WagoRelease> = emptyMap(),
    )

    /** Search and details releases carry `download_link`; scan/recents releases carry `link`. */
    @Serializable
    private data class WagoRelease(
        val label: String,
        @SerialName("download_link") val downloadLink: String? = null,
        val link: String? = null,
    ) {
        val downloadUrl: String? get() = downloadLink ?: link
    }

    companion object {
        private val FLAVORS = listOf(AddonFlavor.FOREVER to "forever", AddonFlavor.CLASSIC to "classic")
    }
}
