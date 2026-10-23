package app.gamenative.wow.addons.provider

import app.gamenative.utils.Net
import app.gamenative.wow.addons.AddonCategory
import app.gamenative.wow.addons.AddonFlavor
import app.gamenative.wow.addons.ProviderIds
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient

/**
 * WoWInterface (MMOUI v3 API, no key). Search and popular use the full `filelist.json`, cached for
 * an hour; [get] adds `filedetails/{id}.json` for the real download link.
 *
 * WoWInterface publishes one file per addon. Its `UICompatibility` decides the flavors: a "Forever"
 * entry (or a 1.60+ version) maps to FOREVER, a 1.x Classic entry to CLASSIC. A file compatible
 * with both is listed under both keys. Addons with neither are not listed.
 */
class WowInterfaceProvider(
    private val http: OkHttpClient = Net.http,
    private val apiBase: String = "https://api.mmoui.com/v3/game/WOW",
    private val clock: () -> Long = System::currentTimeMillis,
) : AddonProvider {
    override val id: String = ProviderIds.WOWINTERFACE
    override val enabled: Boolean = true

    private val cacheLock = Mutex()
    private var cache: List<WowiFile>? = null
    private var cachedAt = 0L

    override suspend fun search(query: String, page: Int): List<RemoteAddon> {
        val needle = query.trim().lowercase()
        return page(page) { needle.isEmpty() || it.name.lowercase().contains(needle) }
    }

    override suspend fun popular(page: Int): List<RemoteAddon> = page(page) { true }

    override suspend fun popular(page: Int, category: AddonCategory?): List<RemoteAddon> =
        page(page) { category == null || AddonCategory.fromWowInterfaceId(it.categoryId) == category }

    override suspend fun description(projectId: String): String? =
        http.fetchText("${apiBase.trimEnd('/')}/filedetails/$projectId.json")
            ?.let { ProviderJson.decodeFromString<List<WowiDetails>>(it).firstOrNull()?.description }
            ?.let(RichText::toPlain)
            ?.takeIf { it.isNotBlank() }

    override suspend fun get(projectId: String): RemoteAddon? {
        val entry = fileList().firstOrNull { it.uid == projectId } ?: return null
        val listed = toRemote(entry) ?: return null
        val details = http.fetchText("${apiBase.trimEnd('/')}/filedetails/$projectId.json")
            ?.let { ProviderJson.decodeFromString<List<WowiDetails>>(it).firstOrNull() }
            ?: return listed
        val file = RemoteFile(
            fileId = details.date?.toString() ?: entry.date.toString(),
            version = details.version ?: entry.version,
            downloadUrl = details.download.orEmpty(),
            dependencies = emptyList(),
        )
        return listed.copy(latest = listed.latest.mapValues { file })
    }

    override suspend fun getMany(ids: List<String>): List<RemoteAddon> = ids.mapNotNull { get(it) }

    private suspend fun page(page: Int, filter: (WowiFile) -> Boolean): List<RemoteAddon> =
        fileList()
            .asSequence()
            .filter(filter)
            .mapNotNull { toRemote(it) }
            .sortedByDescending { it.downloads }
            .drop(page * PAGE_SIZE)
            .take(PAGE_SIZE)
            .toList()

    private suspend fun fileList(): List<WowiFile> = cacheLock.withLock {
        val now = clock()
        cache?.takeIf { now - cachedAt < CACHE_MS }?.let { return@withLock it }
        val body = http.fetchText("${apiBase.trimEnd('/')}/filelist.json") ?: return@withLock emptyList()
        ProviderJson.decodeFromString<List<WowiFile>>(body).also {
            cache = it
            cachedAt = now
        }
    }

    private fun toRemote(entry: WowiFile): RemoteAddon? {
        val flavors = entry.compatibility.mapNotNull { flavorOf(it) }.toSet()
        if (flavors.isEmpty()) return null
        val file = RemoteFile(
            fileId = entry.date.toString(),
            version = entry.version,
            downloadUrl = "https://cdn.wowinterface.com/downloads/getfile.php?id=${entry.uid}",
            dependencies = emptyList(),
        )
        return RemoteAddon(
            provider = id,
            projectId = entry.uid,
            name = entry.name,
            summary = "",
            author = entry.author,
            iconUrl = entry.thumbs?.firstOrNull(),
            downloads = entry.downloads.toLongOrNull() ?: 0,
            latest = listOf(AddonFlavor.MAIN, AddonFlavor.FALLBACK).filter { it in flavors }.associateWith { file },
            websiteUrl = entry.infoUrl,
            categories = setOfNotNull(AddonCategory.fromWowInterfaceId(entry.categoryId)),
            screenshots = entry.images.orEmpty(),
        )
    }

    private fun flavorOf(compat: WowiCompat): AddonFlavor? {
        val name = compat.name.lowercase()
        val parts = compat.version.split('.').mapNotNull { it.trim().toIntOrNull() }
        val major = parts.getOrNull(0)
        val minor = parts.getOrNull(1) ?: 0
        return when {
            "forever" in name || "camelot" in name -> AddonFlavor.MAIN
            major == 1 && minor >= 60 -> AddonFlavor.MAIN
            major == 1 -> AddonFlavor.FALLBACK
            else -> null
        }
    }

    @Serializable
    private data class WowiFile(
        @SerialName("UID") val uid: String,
        @SerialName("UICATID") val categoryId: String = "",
        @SerialName("UIVersion") val version: String = "",
        @SerialName("UIDate") val date: Long = 0,
        @SerialName("UIName") val name: String,
        @SerialName("UIAuthorName") val author: String = "",
        @SerialName("UIFileInfoURL") val infoUrl: String? = null,
        @SerialName("UIDownloadTotal") val downloads: String = "0",
        @SerialName("UICompatibility") val compatibility: List<WowiCompat> = emptyList(),
        @SerialName("UIDir") val dirs: List<String> = emptyList(),
        @SerialName("UIIMG_Thumbs") val thumbs: List<String>? = null,
        @SerialName("UIIMGs") val images: List<String>? = null,
    )

    @Serializable
    private data class WowiCompat(val version: String = "", val name: String = "")

    @Serializable
    private data class WowiDetails(
        @SerialName("UID") val uid: String,
        @SerialName("UIVersion") val version: String? = null,
        @SerialName("UIDate") val date: Long? = null,
        @SerialName("UIDownload") val download: String? = null,
        @SerialName("UIMD5") val md5: String? = null,
        @SerialName("UIDescription") val description: String? = null,
    )

    companion object {
        private const val PAGE_SIZE = 50
        private const val CACHE_MS = 60 * 60 * 1000L
    }
}
