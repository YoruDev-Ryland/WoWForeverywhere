package app.gamenative.wow.addons.provider

import app.gamenative.wow.addons.AddonCategory
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
    val categories: Set<AddonCategory> = emptySet(),
    /** Preview image URLs, largest available. */
    val screenshots: List<String> = emptyList(),
)

/** The addon's own page on its source site, for "Open on ..." links. */
fun RemoteAddon.pageUrl(): String? = when (provider) {
    "wago" -> "https://addons.wago.io/addons/$projectId"
    "github" -> "https://github.com/$projectId"
    "wowinterface" -> websiteUrl ?: "https://www.wowinterface.com/downloads/info$projectId"
    else -> websiteUrl
}?.takeIf { it.startsWith("https://") }

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

    /** Popular addons in [category]. Sources that can't filter by category return [popular]. */
    suspend fun popular(page: Int, category: AddonCategory?): List<RemoteAddon> = popular(page)

    /** The long description as plain text, or null if the source has none. */
    suspend fun description(projectId: String): String? = null
}

/** Turns HTML or BBCode descriptions into readable plain text (paragraphs and list bullets kept). */
object RichText {
    fun toPlain(text: String): String = text
        .replace(Regex("(?i)<br\\s*/?>|</p>|</h[1-6]>|</div>|</li>|</ul>|</ol>"), "\n")
        .replace(Regex("(?i)<li[^>]*>"), "• ")
        .replace(Regex("\\[\\*]"), "\n• ")
        .replace(Regex("<[^>]+>"), "")
        .replace(Regex("(?i)\\[/?(url|color|size|b|i|u|s|center|left|right|font|quote|code|list|img|indent|highlight|spoiler)(?:[=\\s][^\\]]*)?]"), "")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("\r", "")
        .lines().joinToString("\n") { it.trim() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
