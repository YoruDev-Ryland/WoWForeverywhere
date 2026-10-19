package app.gamenative.wow.addons.provider

import app.gamenative.utils.Net
import app.gamenative.wow.addons.AddonCategory
import app.gamenative.wow.addons.AddonFlavor
import app.gamenative.wow.addons.ProviderIds
import app.gamenative.wow.addons.SourceRef
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * CurseForge v1 API (https://docs.curseforge.com). Needs an API key; a blank key disables the
 * provider. Files are picked per flavor through `latestFilesIndexes[].gameVersionTypeId`.
 */
class CurseForgeProvider(
    private val apiKey: String,
    private val http: OkHttpClient = Net.http,
    private val apiBase: String = "https://api.curseforge.com",
) : AddonProvider {
    override val id: String = ProviderIds.CURSEFORGE
    override val enabled: Boolean = apiKey.isNotBlank()

    private val headers by lazy { Headers.headersOf("x-api-key", apiKey, "Accept", "application/json") }

    override suspend fun search(query: String, page: Int): List<RemoteAddon> = searchIn(query, page, null)

    override suspend fun popular(page: Int, category: AddonCategory?): List<RemoteAddon> = searchIn("", page, category)

    override suspend fun description(projectId: String): String? {
        if (!enabled || projectId.toLongOrNull() == null) return null
        val body = http.fetchText("${apiBase.trimEnd('/')}/v1/mods/$projectId/description", headers) ?: return null
        return RichText.toPlain(ProviderJson.decodeFromString<CfText>(body).data).takeIf { it.isNotBlank() }
    }

    /** CurseForge's own category ids that map onto [category], looked up once per session. */
    private var categoryIds: Map<AddonCategory, List<Long>>? = null

    private suspend fun idsFor(category: AddonCategory): List<Long> {
        val all = categoryIds ?: run {
            val body = http.fetchText("${apiBase.trimEnd('/')}/v1/categories?gameId=$GAME_ID", headers)
            val list = body?.let { ProviderJson.decodeFromString<CfCategories>(it).data }.orEmpty()
            list.filter { it.isClass != true }
                .mapNotNull { c -> AddonCategory.fromName(c.name)?.let { it to c.id } }
                .groupBy({ it.first }, { it.second })
                .also { if (body != null) categoryIds = it }
        }
        return all[category].orEmpty()
    }

    private suspend fun searchIn(query: String, page: Int, category: AddonCategory?): List<RemoteAddon> {
        if (!enabled) return emptyList()
        val ids = category?.let { idsFor(it) }
        if (category != null && ids.isNullOrEmpty()) return emptyList()
        val url = "${apiBase.trimEnd('/')}/v1/mods/search".toHttpUrl().newBuilder()
            .apply { if (!ids.isNullOrEmpty()) addQueryParameter("categoryIds", ids.take(10).joinToString(",", "[", "]")) }
            .addQueryParameter("gameId", GAME_ID.toString())
            .addQueryParameter("searchFilter", query)
            .addQueryParameter("sortField", SORT_POPULARITY.toString())
            .addQueryParameter("sortOrder", "desc")
            .addQueryParameter("index", (page * PAGE_SIZE).toString())
            .addQueryParameter("pageSize", PAGE_SIZE.toString())
            .build()
        val body = http.fetchText(url.toString(), headers) ?: return emptyList()
        return ProviderJson.decodeFromString<CfList>(body).data.mapNotNull(::toRemote)
    }

    override suspend fun popular(page: Int): List<RemoteAddon> = search("", page)

    override suspend fun get(projectId: String): RemoteAddon? {
        if (!enabled || projectId.toLongOrNull() == null) return null
        val body = http.fetchText("${apiBase.trimEnd('/')}/v1/mods/$projectId", headers) ?: return null
        return toRemote(ProviderJson.decodeFromString<CfSingle>(body).data, keepEmpty = true)
    }

    override suspend fun getMany(ids: List<String>): List<RemoteAddon> {
        val numeric = ids.mapNotNull { it.toLongOrNull() }
        if (!enabled || numeric.isEmpty()) return emptyList()
        val request = buildJsonObject { putJsonArray("modIds") { numeric.forEach { add(JsonPrimitive(it)) } } }
        val body = http.fetchText("${apiBase.trimEnd('/')}/v1/mods", headers, postJson = request.toString()) ?: return emptyList()
        return ProviderJson.decodeFromString<CfList>(body).data.mapNotNull { toRemote(it, keepEmpty = true) }
    }

    /** Maps a mod; mods without a Forever or Classic file are dropped unless [keepEmpty]. */
    private fun toRemote(mod: CfMod, keepEmpty: Boolean = false): RemoteAddon? {
        val latest = LinkedHashMap<AddonFlavor, RemoteFile>()
        for ((flavor, typeId) in listOf(AddonFlavor.FOREVER to FOREVER_TYPE_ID, AddonFlavor.CLASSIC to CLASSIC_ERA_TYPE_ID)) {
            pickFile(mod, typeId)?.let { latest[flavor] = it }
        }
        if (latest.isEmpty() && !keepEmpty) return null
        return RemoteAddon(
            provider = id,
            projectId = mod.id.toString(),
            name = mod.name,
            summary = mod.summary.orEmpty(),
            author = mod.authors.map { it.name }.filterNot { it.startsWith("_forgeuser", ignoreCase = true) }.joinToString(", "),
            iconUrl = mod.logo?.thumbnailUrl,
            downloads = mod.downloadCount.toLong(),
            latest = latest,
            websiteUrl = mod.links?.websiteUrl,
            categories = mod.categories.mapNotNull { AddonCategory.fromName(it.name) }.toSet(),
            screenshots = mod.screenshots.mapNotNull { it.url ?: it.thumbnailUrl },
        )
    }

    /**
     * The newest file for [typeId], preferring stable releases (releaseType 1) over beta/alpha.
     * An empty download URL means the author disabled third-party distribution.
     */
    private fun pickFile(mod: CfMod, typeId: Int): RemoteFile? {
        val order = compareBy<Pair<Long, Int>>({ if (it.second == RELEASE) 0 else 1 }, { -it.first })
        val indexed = mod.latestFilesIndexes
            .filter { it.gameVersionTypeId == typeId }
            .sortedWith(compareBy(order) { it.fileId to it.releaseType })
        val files = mod.latestFiles
            .filter { f -> !f.exposeAsAlternative && f.sortableGameVersions.any { it.gameVersionTypeId == typeId } }
            .sortedWith(compareBy(order) { it.id to it.releaseType })
        val distributable = mod.allowModDistribution != false

        val fileId = indexed.firstOrNull()?.fileId ?: files.firstOrNull()?.id ?: return null
        val file = mod.latestFiles.firstOrNull { it.id == fileId }
        if (file != null) {
            return RemoteFile(
                fileId = file.id.toString(),
                version = file.displayName ?: file.fileName.orEmpty(),
                downloadUrl = if (distributable) file.downloadUrl.orEmpty() else "",
                dependencies = file.dependencies
                    .filter { it.relationType == REQUIRED_DEPENDENCY }
                    .map { SourceRef(ProviderIds.CURSEFORGE, it.modId.toString()) },
            )
        }
        // Only the index entry is known: build the CDN URL from the file id and name.
        val index = indexed.first()
        return RemoteFile(
            fileId = index.fileId.toString(),
            version = index.filename,
            downloadUrl = if (distributable) forgeCdnUrl(index.fileId, index.filename) else "",
            dependencies = emptyList(),
        )
    }

    @Serializable
    private data class CfList(val data: List<CfMod> = emptyList())

    @Serializable
    private data class CfSingle(val data: CfMod)

    @Serializable
    private data class CfMod(
        val id: Long,
        val name: String,
        val summary: String? = null,
        val downloadCount: Double = 0.0,
        val links: CfLinks? = null,
        val logo: CfLogo? = null,
        val authors: List<CfAuthor> = emptyList(),
        val allowModDistribution: Boolean? = null,
        val latestFiles: List<CfFile> = emptyList(),
        val latestFilesIndexes: List<CfFileIndex> = emptyList(),
        val categories: List<CfCategory> = emptyList(),
        val screenshots: List<CfScreenshot> = emptyList(),
    )

    @Serializable
    private data class CfCategory(val id: Long = 0, val name: String = "", val isClass: Boolean? = null)

    @Serializable
    private data class CfCategories(val data: List<CfCategory> = emptyList())

    @Serializable
    private data class CfScreenshot(val url: String? = null, val thumbnailUrl: String? = null)

    @Serializable
    private data class CfText(val data: String = "")

    @Serializable
    private data class CfLinks(val websiteUrl: String? = null)

    @Serializable
    private data class CfLogo(val thumbnailUrl: String? = null)

    @Serializable
    private data class CfAuthor(val name: String)

    @Serializable
    private data class CfFile(
        val id: Long,
        val displayName: String? = null,
        val fileName: String? = null,
        val releaseType: Int = RELEASE,
        val downloadUrl: String? = null,
        val exposeAsAlternative: Boolean = false,
        val sortableGameVersions: List<CfSortableGameVersion> = emptyList(),
        val dependencies: List<CfDependency> = emptyList(),
    )

    @Serializable
    private data class CfSortableGameVersion(val gameVersionTypeId: Int? = null)

    @Serializable
    private data class CfDependency(val modId: Long, val relationType: Int)

    @Serializable
    private data class CfFileIndex(
        val fileId: Long,
        val filename: String = "",
        val releaseType: Int = RELEASE,
        val gameVersionTypeId: Int? = null,
    )

    companion object {
        const val GAME_ID = 1
        private const val PAGE_SIZE = 50
        private const val SORT_POPULARITY = 2
        private const val RELEASE = 1
        private const val REQUIRED_DEPENDENCY = 3

        /** `wow_classic` (Classic Era) game version type. */
        const val CLASSIC_ERA_TYPE_ID = 67408

        /** `wow-forever` game version type, as used by WowUp's CurseForge provider. */
        const val FOREVER_TYPE_ID = 88568

        fun forgeCdnUrl(fileId: Long, fileName: String): String =
            "https://edge.forgecdn.net/files/${fileId / 1000}/${fileId % 1000}/$fileName"
    }
}
