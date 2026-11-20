package app.gamenative.wow.ui.addons

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.gamenative.BuildConfig
import app.gamenative.R
import app.gamenative.utils.Net
import app.gamenative.wow.addons.AddonCategory
import app.gamenative.wow.addons.AddonDb
import app.gamenative.wow.addons.AddonFlavor
import app.gamenative.wow.addons.AddonListing
import app.gamenative.wow.addons.AddonManager
import app.gamenative.wow.addons.addonTarget
import app.gamenative.wow.addons.provider.CurseForgeProvider
import app.gamenative.wow.addons.provider.GitHubProvider
import app.gamenative.wow.addons.provider.WagoProvider
import app.gamenative.wow.addons.provider.WowInterfaceProvider
import app.gamenative.wow.product.GameVersion
import app.gamenative.wow.product.WowProduct
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber

/** One install or update the player started, and how far along it is. */
data class AddonJob(
    val name: String,
    val update: Boolean,
    val state: State = State.QUEUED,
    val bytes: Long = 0,
    val total: Long? = null,
    val error: String? = null,
) {
    enum class State { QUEUED, DOWNLOADING, UNPACKING, DONE, FAILED }

    val active get() = state == State.QUEUED || state == State.DOWNLOADING || state == State.UNPACKING

    /** Share of the download done, or null while the size isn't known. */
    val fraction: Float? get() = total?.takeIf { it > 0 }?.let { (bytes.toFloat() / it).coerceIn(0f, 1f) }

    /** This job's part of the batch bar: a queued job counts nothing, a finished one all of its share. */
    val share: Float get() = when (state) {
        State.QUEUED -> 0f
        State.DOWNLOADING -> (fraction ?: 0f) * 0.9f
        State.UNPACKING -> 0.9f
        State.DONE, State.FAILED -> 1f
    }
}

/** UI state for the addon browser, bound to one `Interface/AddOns` folder whose addons are for [version]. */
class AddonsController(val addonsDir: File, val version: GameVersion, private val scope: CoroutineScope, private val context: Context) {
    /** The addons of one of the app's own installs. */
    constructor(gameRoot: File, product: WowProduct, scope: CoroutineScope, context: Context) :
        this(File(gameRoot, "${product.flavorDir}/Interface/AddOns"), GameVersion.of(product), scope, context)

    enum class Mode { INSTALLED, BROWSE }

    private val target = version.addonTarget()
    private val manager = AddonManager(
        addonsDir = addonsDir,
        db = AddonDb.forAddonsDir(addonsDir),
        providers = listOf(
            CurseForgeProvider(
                BuildConfig.CURSEFORGE_API_KEY,
                apiBase = BuildConfig.CURSEFORGE_API_BASE,
                proxy = BuildConfig.CURSEFORGE_API_KEY.isBlank(),
                target = target,
                proxyToken = CurseForgeProvider.unscramble(BuildConfig.CURSEFORGE_PROXY_TOKEN_A, BuildConfig.CURSEFORGE_PROXY_TOKEN_B),
            ),
            WagoProvider(BuildConfig.WAGO_API_KEY, target = target),
            WowInterfaceProvider(target = target),
            GitHubProvider(target = target),
        ),
        http = Net.http,
        target = target,
    )

    var mode by mutableStateOf(Mode.INSTALLED)
        private set
    var query by mutableStateOf("")
        private set
    var includeClassic by mutableStateOf(true)
        private set
    var source by mutableStateOf<String?>(null)
        private set
    var category by mutableStateOf<AddonCategory?>(null)
        private set

    /** Long descriptions by addon key, loaded when an addon is selected. */
    var descriptions by mutableStateOf<Map<String, String?>>(emptyMap())
        private set
    private var descriptionJob: Job? = null
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

    /** Installs and updates started since the strip was last closed, by addon key, in the order they started. */
    var jobs by mutableStateOf<Map<String, AddonJob>>(emptyMap())
        private set
    private val retries = mutableMapOf<String, () -> Unit>()

    /** Downloads at once: enough to keep a phone's connection busy without starving each one. */
    private val gate = kotlinx.coroutines.sync.Semaphore(3)

    val updates get() = installed?.count { it.updateAvailable }

    fun jobFor(listing: AddonListing): AddonJob? = jobs[keyOf(listing)]

    /** Clears the finished jobs from the strip; ones still running stay. */
    fun closeJobs() {
        jobs = jobs.filterValues { it.active }
        retries.keys.retainAll(jobs.keys)
    }

    /** Starts every failed job again. */
    fun retryFailed() {
        jobs.filterValues { it.state == AddonJob.State.FAILED }.keys.mapNotNull { retries[it] }.forEach { it() }
    }
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

    fun selectCategory(value: AddonCategory?) {
        category = value
        if (mode == Mode.INSTALLED) show(installed.orEmpty()) else load()
    }

    fun keyOf(listing: AddonListing) = "${listing.remote.provider}:${listing.remote.projectId}"

    /** Fetches the long description for [listing] once; shows the summary until it arrives. */
    fun loadDescription(listing: AddonListing) {
        val key = keyOf(listing)
        if (key in descriptions) return
        descriptionJob?.cancel()
        descriptionJob = scope.launch {
            delay(250)
            val text = runCatching { withContext(Dispatchers.IO) { manager.description(listing) } }.getOrNull()
            descriptions = descriptions + (key to text)
        }
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
                    if (query.isBlank()) manager.popular(includeClassic, category) else manager.search(query, includeClassic, category)
                }
                show(found)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = context.getString(R.string.fw_addon_sites_unreachable)
                Timber.w(e, "Addon search failed")
            } finally {
                loading = false
            }
        }
    }

    private fun show(items: List<AddonListing>) {
        val q = query.trim().lowercase()
        listings = items.filter { listing ->
            (includeClassic || listing.bestFlavor != AddonFlavor.FALLBACK) &&
                (source == null || source in listing.sources) &&
                (mode == Mode.BROWSE || category == null || category in listing.remote.categories) &&
                (mode == Mode.BROWSE || q.isEmpty() || q in listing.remote.name.lowercase())
        }
        selected = selected?.let { sel -> listings.firstOrNull { it.remote.projectId == sel.remote.projectId } } ?: listings.firstOrNull()
    }

    fun install(listing: AddonListing) {
        startJob(keyOf(listing), listing.remote.name, update = false, retry = { install(listing) }) { step ->
            markInstalled(listing, manager.install(listing.remote, step))
        }
    }

    fun update(listing: AddonListing) {
        val record = listing.installed ?: return
        startJob(keyOf(listing), listing.remote.name, update = true, retry = { update(listing) }) { step ->
            val updated = if (record.flavor == AddonFlavor.FALLBACK && listing.bestFlavor == AddonFlavor.MAIN) {
                manager.install(listing.remote, step)
            } else {
                manager.update(record, listing.remote, step)
            }
            markInstalled(listing, updated)
        }
    }

    /**
     * Runs one install or update as a job: queued until one of the [gate]'s slots is free, then downloading with
     * progress and unpacking. Starting the same addon again while it runs does nothing. When the last running job
     * finishes, the installed list is read again once.
     */
    private fun startJob(key: String, name: String, update: Boolean, retry: () -> Unit, block: suspend ((AddonManager.Step) -> Unit) -> Unit) {
        if (jobs[key]?.active == true) return
        // A new round starts with an empty strip once everything before it has finished.
        if (jobs.values.none { it.active }) closeJobs()
        retries[key] = retry
        message = null
        put(key, AddonJob(name, update))
        scope.launch {
            try {
                gate.withPermit {
                    put(key, AddonJob(name, update, AddonJob.State.DOWNLOADING))
                    var shown = AddonJob(name, update, AddonJob.State.DOWNLOADING)
                    withContext(Dispatchers.IO) {
                        block { step ->
                            val next = when (step) {
                                is AddonManager.Step.Downloading -> shown.copy(state = AddonJob.State.DOWNLOADING, bytes = step.bytes, total = step.total)
                                AddonManager.Step.Unpacking -> shown.copy(state = AddonJob.State.UNPACKING)
                            }
                            // Every 64 KB would redraw the list hundreds of times; a whole percent or 256 KB is enough.
                            val moved = next.state != shown.state || next.fraction?.let { f -> (f * 100).toInt() != ((shown.fraction ?: 0f) * 100).toInt() }
                                ?: (next.bytes - shown.bytes >= 256 * 1024)
                            if (moved) {
                                shown = next
                                scope.launch(Dispatchers.Main) { if (jobs[key]?.active == true) put(key, next) }
                            }
                        }
                    }
                }
                put(key, (jobs[key] ?: AddonJob(name, update)).copy(state = AddonJob.State.DONE))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Addon job $name")
                put(key, (jobs[key] ?: AddonJob(name, update)).copy(state = AddonJob.State.FAILED, error = e.message ?: context.getString(R.string.fw_unknown_error)))
            } finally {
                if (jobs.values.none { it.active }) refreshAfterChange()
            }
        }
    }

    private fun put(key: String, job: AddonJob) {
        jobs = jobs + (key to job)
    }

    /** Shows the new state right away; the full refresh after the action fills in the rest. */
    private suspend fun markInstalled(listing: AddonListing, record: app.gamenative.wow.addons.InstalledAddon) = withContext(Dispatchers.Main) {
        val key = keyOf(listing)
        val patch = { l: AddonListing -> if (keyOf(l) == key) l.copy(installed = record, updateAvailable = false) else l }
        listings = listings.map(patch)
        selected = selected?.let(patch)
    }

    fun remove(listing: AddonListing) = act(context.getString(R.string.fw_addon_removing, listing.remote.name)) { listing.installed?.let { manager.remove(it) } }

    /** Starts an update for every addon with one, from the versions the installed list already fetched. */
    fun updateAll() {
        installed.orEmpty().filter { it.updateAvailable && it.installed != null }.forEach { update(it) }
    }

    fun installFromUrl(url: String) {
        val link = url.trim()
        startJob("url:$link", context.getString(R.string.fw_addon_installing_link), update = false, retry = { installFromUrl(link) }) { step ->
            manager.installFromUrl(link, step)
        }
    }

    /** Installs the addons in a .zip the player picked on the device, copied to the cache first. */
    fun installFromFile(uri: android.net.Uri) {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: "add-on .zip"
        act(context.getString(R.string.fw_addon_installing, name)) {
            val zip = File(context.cacheDir, "addon-import-${System.nanoTime()}.zip")
            try {
                context.contentResolver.openInputStream(uri)?.use { input -> zip.outputStream().use { input.copyTo(it) } }
                    ?: throw java.io.IOException(context.getString(R.string.fw_addon_file_unreadable))
                manager.installFromZip(zip)
            } finally {
                zip.delete()
            }
        }
    }

    private fun act(label: String, block: suspend () -> Unit) {
        if (working != null) return
        working = label
        message = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                message = context.getString(R.string.fw_addon_action_done, label)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, label)
                message = context.getString(R.string.fw_addon_action_failed, label, e.message ?: context.getString(R.string.fw_unknown_error))
            } finally {
                working = null
                refreshAfterChange()
            }
        }
    }

    private fun refreshAfterChange() {
        scope.launch {
            installed = runCatching { withContext(Dispatchers.IO) { manager.installed() } }.getOrDefault(installed.orEmpty())
            if (mode == Mode.INSTALLED) show(installed.orEmpty()) else show(listings.map { l -> refreshed(l) })
        }
    }

    private fun refreshed(listing: AddonListing): AddonListing {
        val record = installed?.firstOrNull { it.remote.provider == listing.remote.provider && it.remote.projectId == listing.remote.projectId }
        return listing.copy(installed = record?.installed, updateAvailable = record?.updateAvailable ?: false)
    }
}
