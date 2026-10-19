package app.gamenative.wow.addons

import kotlinx.serialization.Serializable

/** Where an addon came from: a provider id (see `ProviderIds`) plus that provider's project and file ids. */
@Serializable
data class SourceRef(val provider: String, val projectId: String, val fileId: String? = null)

/** One logical addon in `Interface/AddOns`, which may span several folders. */
@Serializable
data class InstalledAddon(
    val id: String,
    val name: String,
    val folders: List<String>,
    val version: String?,
    val flavor: AddonFlavor,
    val source: SourceRef?,
    val installedAt: Long,
)

/** Provider ids used in [SourceRef.provider] and `AddonProvider.id`. */
object ProviderIds {
    const val CURSEFORGE = "curseforge"
    const val WAGO = "wago"
    const val WOWINTERFACE = "wowinterface"
    const val GITHUB = "github"

    /** A direct zip URL installed via "Install from URL"; `projectId` is the URL. */
    const val URL = "url"

    /** An addon found on disk with no known source; `projectId` is its main folder. */
    const val LOCAL = "local"
}
