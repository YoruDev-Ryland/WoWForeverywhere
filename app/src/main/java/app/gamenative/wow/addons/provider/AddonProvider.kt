package app.gamenative.wow.addons.provider

import app.gamenative.wow.addons.AddonFlavor
import app.gamenative.wow.addons.SourceRef

/** An addon as listed by a provider, with its newest file per game flavor. */
data class RemoteAddon(
    val provider: String,
    val projectId: String,
    val name: String,
    val summary: String,
    val author: String,
    val iconUrl: String?,
    val downloads: Long,
    val latest: Map<AddonFlavor, RemoteFile>,
    val websiteUrl: String?,
)

/**
 * A downloadable release. An empty [downloadUrl] means the author disabled third-party
 * distribution (CurseForge): show "Open on website" instead of Install.
 */
data class RemoteFile(
    val fileId: String,
    val version: String,
    val downloadUrl: String,
    val dependencies: List<SourceRef>,
) {
    val installable: Boolean get() = downloadUrl.isNotBlank()
}

/**
 * An addon source. Implementations return `null` from [get] when the project doesn't exist, and
 * throw [java.io.IOException] on network or server errors. A provider that isn't [enabled] (for
 * example a missing API key) makes no HTTP calls and returns empty results.
 */
interface AddonProvider {
    val id: String
    val enabled: Boolean
    suspend fun search(query: String, page: Int): List<RemoteAddon>
    suspend fun popular(page: Int): List<RemoteAddon>
    suspend fun get(projectId: String): RemoteAddon?
    suspend fun getMany(ids: List<String>): List<RemoteAddon>
}
