package app.gamenative.wow.ui.addons

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.gamenative.BuildConfig
import app.gamenative.utils.Net
import app.gamenative.wow.addons.AddonDb
import app.gamenative.wow.addons.AddonFlavor
import app.gamenative.wow.addons.AddonListing
import app.gamenative.wow.addons.AddonManager
import app.gamenative.wow.addons.provider.CurseForgeProvider
import app.gamenative.wow.addons.provider.GitHubProvider
import app.gamenative.wow.addons.provider.WagoProvider
import app.gamenative.wow.addons.provider.WowInterfaceProvider
import app.gamenative.wow.product.WowProduct
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** UI state for the addon browser, bound to one install's `Interface/AddOns` folder. */
class AddonsController(gameRoot: File, product: WowProduct, private val scope: CoroutineScope) {
    enum class Mode { INSTALLED, BROWSE }

    val addonsDir = File(gameRoot, "${product.flavorDir}/Interface/AddOns")
    private val manager = AddonManager(
        addonsDir = addonsDir,
        db = AddonDb.forAddonsDir(addonsDir),
        providers = listOf(
            CurseForgeProvider(BuildConfig.CURSEFORGE_API_KEY),
            WagoProvider(BuildConfig.WAGO_API_KEY),
            WowInterfaceProvider(),
            GitHubProvider(),
        ),
        http = Net.http,
    )

    var mode by mutableStateOf(Mode.INSTALLED)
        private set
    var query by mutableStateOf("")
        private set
    var includeClassic by mutableStateOf(true)
        private set
    var source by mutableStateOf<String?>(null)
        private set
    var listings by mutableStateOf<List<AddonListing>>(emptyList())
        private set
    var installed by mutableStateOf<List<AddonListing>?>(null)
        private set
    var selected by mutableStateOf<AddonListing?>(null)
    var loading by mutableStateOf(false)
        private set
    var working by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    val updates get() = installed?.count { it.updateAvailable }
    val sources = listOf("curseforge", "wago", "wowinterface", "github")

    private var loadJob: Job? = null

    fun refreshInstalled() {
        scope.launch {
            installed = runCatching { withContext(Dispatchers.IO) { manager.installed() } }
                .onFailure { Timber.w(it, "Listing installed addons failed") }
                .getOrDefault(emptyList())
            if (mode == Mode.INSTALLED) show(installed.orEmpty())
        }
    }

    fun switchMode(newMode: Mode) {
        mode = newMode
        selected = null
        if (newMode == Mode.INSTALLED) show(installed.orEmpty()) else load()
    }

    fun search(text: String) {
        query = text
        if (mode == Mode.INSTALLED) show(installed.orEmpty()) else load(debounce = true)
    }

    fun toggleClassic() {
        includeClassic = !includeClassic
        if (mode == Mode.INSTALLED) show(installed.orEmpty()) else load()
    }

    fun cycleSource() {
        source = (listOf<String?>(null) + sources).let { all -> all[(all.indexOf(source) + 1) % all.size] }
        if (mode == Mode.INSTALLED) show(installed.orEmpty()) else load()
    }

    private fun load(debounce: Boolean = false) {
        loadJob?.cancel()
        loadJob = scope.launch {
            if (debounce) delay(400)
            loading = true
            try {
                val found = withContext(Dispatchers.IO) {
                    if (query.isBlank()) manager.popular(includeClassic) else manager.search(query, includeClassic)
                }
                show(found)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Couldn't reach the addon sites. Check your connection."
                Timber.w(e, "Addon search failed")
            } finally {
                loading = false
            }
        }
    }

    private fun show(items: List<AddonListing>) {
        val q = query.trim().lowercase()
        listings = items.filter { listing ->
            (includeClassic || listing.bestFlavor != AddonFlavor.CLASSIC) &&
                (source == null || source in listing.sources) &&
                (mode == Mode.BROWSE || q.isEmpty() || q in listing.remote.name.lowercase())
        }
        selected = selected?.let { sel -> listings.firstOrNull { it.remote.projectId == sel.remote.projectId } } ?: listings.firstOrNull()
    }

    fun install(listing: AddonListing) = act("Installing ${listing.remote.name}") { manager.install(listing.remote) }

    fun update(listing: AddonListing) = act("Updating ${listing.remote.name}") {
        val record = listing.installed ?: return@act
        if (record.flavor == AddonFlavor.CLASSIC && listing.bestFlavor == AddonFlavor.FOREVER) manager.install(listing.remote) else manager.update(record)
    }

    fun remove(listing: AddonListing) = act("Removing ${listing.remote.name}") { listing.installed?.let { manager.remove(it) } }

    fun updateAll() = act("Updating all addons") { manager.updateAll() }

    fun installFromUrl(url: String) = act("Installing from link") { manager.installFromUrl(url.trim()) }

    private fun act(label: String, block: suspend () -> Unit) {
        if (working != null) return
        working = label
        message = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                message = "$label: done."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, label)
                message = "$label failed: ${e.message ?: "unknown error"}"
            } finally {
                working = null
                installed = runCatching { withContext(Dispatchers.IO) { manager.installed() } }.getOrDefault(installed.orEmpty())
                if (mode == Mode.INSTALLED) show(installed.orEmpty()) else show(listings.map { l -> refreshed(l) })
            }
        }
    }

    private fun refreshed(listing: AddonListing): AddonListing {
        val record = installed?.firstOrNull { it.remote.provider == listing.remote.provider && it.remote.projectId == listing.remote.projectId }
        return listing.copy(installed = record?.installed, updateAvailable = record?.updateAvailable ?: false)
    }
}
