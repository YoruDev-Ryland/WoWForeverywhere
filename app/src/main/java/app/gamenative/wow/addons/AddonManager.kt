package app.gamenative.wow.addons

import app.gamenative.wow.addons.provider.AddonProvider
import app.gamenative.wow.addons.provider.GitHubProvider
import app.gamenative.wow.addons.provider.RemoteAddon
import app.gamenative.wow.addons.provider.RemoteFile
import app.gamenative.wow.addons.provider.pageUrl
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * One row in the addon browser. [remote] is the preferred source; [sources] lists every provider
 * the addon was found on, preferred first.
 */
data class AddonListing(
    val remote: RemoteAddon,
    val bestFlavor: AddonFlavor,
    val installed: InstalledAddon?,
    val updateAvailable: Boolean,
    val sources: List<String>,
    /** Project id on each source, for follow-up calls such as [AddonManager.description]. */
    val sourceIds: Map<String, String> = emptyMap(),
    /** The addon's page on each source site, primary source first. */
    val pages: Map<String, String> = emptyMap(),
)

/**
 * Searches, installs, updates and removes addons in one `Interface/AddOns` folder.
 *
 * Network failures in search and update checks are swallowed per provider, so being offline
 * never blocks anything: results simply come back empty. Downloads run side by side; unpacking into the folder,
 * recording and removing are serialized.
 */
class AddonManager(
    private val addonsDir: File,
    private val db: AddonDb,
    private val providers: List<AddonProvider>,
    private val http: OkHttpClient,
    private val target: AddonTarget = AddonTarget.FOREVER,
) {
    private val lock = Mutex()

    /** What an install is doing, reported to the caller as it goes. */
    sealed interface Step {
        /** [bytes] of the zip so far, out of [total] when the server said. */
        data class Downloading(val bytes: Long, val total: Long?) : Step
        data object Unpacking : Step
    }

    suspend fun search(query: String, includeClassic: Boolean = true, category: AddonCategory? = null): List<AddonListing> =
        browse(includeClassic, category) { it.search(query, 0) }

    suspend fun popular(includeClassic: Boolean = true, category: AddonCategory? = null): List<AddonListing> =
        browse(includeClassic, category) { it.popular(0, category) }

    /** The long description from the first of [listing]'s sources that has one. */
    suspend fun description(listing: AddonListing): String? {
        val order = listOf(listing.remote.provider) + listing.sources
        for (providerId in order.distinct()) {
            val provider = providers.firstOrNull { it.id == providerId && it.enabled } ?: continue
            val projectId = if (providerId == listing.remote.provider) listing.remote.projectId else listing.sourceIds[providerId] ?: continue
            safely { provider.description(projectId) }?.let { return it }
        }
        return listing.remote.summary.takeIf { it.isNotBlank() }
    }

    /** Every installed addon (database plus anything found on disk), with update state. */
    suspend fun installed(): List<AddonListing> {
        val records = lock.withLock { syncDb() }
        val remotes = fetchRemotes(records)
        return records.map { record ->
            val remote = record.source?.let { src -> remotes[src.provider to src.projectId] }
            val group = listOfNotNull(remote)
            AddonListing(
                remote = remote ?: placeholder(record),
                bestFlavor = remote?.let { bestFlavor(group) }?.takeIf { remote.latest.isNotEmpty() } ?: record.flavor,
                installed = record,
                updateAvailable = needsUpdate(record, group),
                sources = listOfNotNull(record.source?.provider),
                pages = listOfNotNull(remote?.let { r -> r.pageUrl()?.let { r.provider to it } }).toMap(),
            )
        }
    }

    suspend fun checkUpdates(): List<AddonListing> = installed().filter { it.updateAvailable }

    /** Installs [remote]'s best file (Forever first), installing missing required dependencies first. */
    suspend fun install(remote: RemoteAddon, onStep: (Step) -> Unit = {}): InstalledAddon = installWithDependencies(remote, mutableSetOf(), onStep)

    /**
     * Reinstalls [addon] from its source's newest best file. [known] is the source's listing when the caller already
     * has it (from [installed]), which saves asking the source again.
     */
    suspend fun update(addon: InstalledAddon, known: RemoteAddon? = null, onStep: (Step) -> Unit = {}): InstalledAddon {
        val source = addon.source ?: throw IOException("${addon.name} has no known source")
        val remote = known?.takeIf { it.provider == source.provider && it.projectId == source.projectId && it.latest.isNotEmpty() }
            ?: fetchRemote(addon, source)
        return install(remote, onStep)
    }

    /** Updates every addon with an update available, up to [parallel] at once; failures are skipped. */
    suspend fun updateAll(parallel: Int = 3): List<InstalledAddon> {
        val gate = kotlinx.coroutines.sync.Semaphore(parallel)
        return coroutineScope {
            checkUpdates().mapNotNull { listing ->
                val installed = listing.installed ?: return@mapNotNull null
                async {
                    gate.withPermit {
                        try {
                            update(installed, listing.remote)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    /** Removes [addon] and every folder of it that no other installed addon lists. */
    suspend fun remove(addon: InstalledAddon): Unit = lock.withLock {
        val records = syncDb()
        val record = records.firstOrNull { it.id == addon.id } ?: addon
        val others = records.filter { it.id != record.id }
        val owned = others.flatMap { it.folders }.toSet()
        (record.folders + addon.folders).distinct().filter { it !in owned }.forEach { deleteFolder(it) }
        db.save(others)
    }

    /** Installs from a GitHub repository URL or a direct zip URL. */
    suspend fun installFromUrl(url: String, onStep: (Step) -> Unit = {}): InstalledAddon {
        val repo = GitHubProvider.fromUrl(url)
        if (repo != null) {
            val github = providers.firstOrNull { it.id == ProviderIds.GITHUB } ?: GitHubProvider(http)
            val remote = github.get(repo) ?: throw IOException("no release found for $repo")
            return install(remote, onStep)
        }
        val trimmed = url.trim()
        require(trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)) {
            "not a URL: $url"
        }
        return downloadThenUnpack(trimmed, onStep) { folders -> recordUrl(trimmed, folders) }
    }

    private fun recordUrl(trimmed: String, folders: List<String>): InstalledAddon {
        val main = folders.firstNotNullOfOrNull { FlavorRules.pickToc(File(addonsDir, it), target) }
        val record = InstalledAddon(
            id = "${ProviderIds.URL}:$trimmed",
            name = main?.title ?: folders.first(),
            folders = folders,
            version = main?.version,
            flavor = classifyFolders(folders),
            source = SourceRef(ProviderIds.URL, trimmed),
            installedAt = System.currentTimeMillis(),
        )
        return commit(record)
    }

    /**
     * Installs the addons in a zip already on the device. They're recorded like addons copied in by hand, from their
     * TOCs, so one whose TOC names its CurseForge or Wago project still gets update checks. A record whose folders
     * the zip all replaced is dropped first, since it describes the copy that was overwritten. A zip addon with the
     * same id as an older record is folded into it, see [mergeSameId].
     */
    suspend fun installFromZip(zip: File): List<InstalledAddon> = lock.withLock {
        val folders = withContext(Dispatchers.IO) { AddonArchive.install(zip, addonsDir) }.toSet()
        val records = db.load()
        db.save(records.filterNot { folders.containsAll(it.folders) })
        val synced = syncDb()
        val merged = mergeSameId(synced, folders)
        if (merged != synced) db.save(merged)
        syncDb().filter { record -> record.folders.any { it in folders } }
    }

    /**
     * Two records with one id would show the same addon twice (and crash the Installed list), so a record the zip just
     * added is folded into an older one with its id. The older record keeps every folder it had: addons such as DBM
     * ship several folders under one project, and a zip may hold only one of them. The only folders removed are
     * suffixed copies of a folder the zip brought in ("Foo_Forever" when "Foo" arrived), left by older installs.
     * When that leaves nothing of the older record outside the zip, the zip's record replaces it.
     */
    private fun mergeSameId(records: List<InstalledAddon>, folders: Set<String>): List<InstalledAddon> {
        val fresh = records.filter { folders.containsAll(it.folders) }
        val older = (records - fresh.toSet()).associateBy { it.id }
        val folded = fresh.filter { it.id in older }
        if (folded.isEmpty()) return records
        val replacements = folded.groupBy { it.id }.mapValues { (id, news) ->
            val old = older.getValue(id)
            val newFolders = news.flatMap { it.folders }.distinct()
            val stale = old.folders.filter { o -> o !in folders && newFolders.any { AddonArchive.isSuffixedCopy(o, it) } }
            stale.forEach { deleteFolder(it) }
            val outside = old.folders.filter { it !in stale && it !in folders }
            if (outside.isEmpty() && news.size == 1) news.single() else old.copy(folders = (old.folders - stale.toSet() + newFolders).distinct())
        }
        return records.filterNot { it in folded }.map { replacements[it.id] ?: it }
    }

    // --- browsing -------------------------------------------------------------------------------

    private suspend fun browse(
        includeClassic: Boolean,
        category: AddonCategory?,
        call: suspend (AddonProvider) -> List<RemoteAddon>,
    ): List<AddonListing> {
        val results = coroutineScope {
            providers.filter { it.enabled }.map { p -> async { safely { call(p) }.orEmpty() } }.awaitAll().flatten()
        }
        val records = db.load()
        return merge(results, records)
            .filter { includeClassic || it.bestFlavor != AddonFlavor.FALLBACK }
            .filter { category == null || category in it.remote.categories }
    }

    private fun merge(results: List<RemoteAddon>, records: List<InstalledAddon>): List<AddonListing> {
        val groups = LinkedHashMap<String, MutableList<RemoteAddon>>()
        results.forEach { groups.getOrPut(mergeKey(it)) { mutableListOf() } += it }
        return groups.values.map { group ->
            val primary = group.maxWith(compareBy<RemoteAddon>({ AddonFlavor.MAIN in it.latest }, { it.downloads }))
            val installed = records.firstOrNull { record -> group.any { record.source.matches(it) } }
            AddonListing(
                // Sources fill in for each other: one may list categories or screenshots another lacks.
                remote = primary.copy(
                    summary = primary.summary.ifBlank { group.firstOrNull { it.summary.isNotBlank() }?.summary.orEmpty() },
                    iconUrl = primary.iconUrl ?: group.firstNotNullOfOrNull { it.iconUrl },
                    categories = group.flatMap { it.categories }.toSet(),
                    screenshots = primary.screenshots.ifEmpty { group.firstOrNull { it.screenshots.isNotEmpty() }?.screenshots.orEmpty() },
                ),
                bestFlavor = bestFlavor(group),
                installed = installed,
                updateAvailable = installed != null && needsUpdate(installed, group),
                sources = (listOf(primary) + group).map { it.provider }.distinct(),
                sourceIds = group.associate { it.provider to it.projectId },
                pages = (listOf(primary) + group).distinctBy { it.provider }.mapNotNull { r -> r.pageUrl()?.let { r.provider to it } }.toMap(),
            )
        }.sortedWith(compareBy<AddonListing>({ it.bestFlavor != AddonFlavor.MAIN }, { -it.remote.downloads }))
    }

    /**
     * Listings of one addon on different sites carry different author names (an org on GitHub, a
     * person on WoWInterface) and decorated titles ("BigWigs (Boss Timers & Tools)"), so they merge
     * on the name alone, without any bracketed suffix.
     */
    private fun mergeKey(remote: RemoteAddon): String = normalize(remote.name.replace(Regex("""\s*[(\[].*?[)\]]"""), ""))

    private fun normalize(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

    private fun bestFlavor(group: List<RemoteAddon>): AddonFlavor = when {
        group.any { AddonFlavor.MAIN in it.latest } -> AddonFlavor.MAIN
        group.any { AddonFlavor.FALLBACK in it.latest } -> AddonFlavor.FALLBACK
        else -> AddonFlavor.OTHER
    }

    /**
     * True when the installed source's best file differs from the installed one, or the installed
     * copy is Classic and a Forever file now exists on any of [group]'s sources.
     */
    private fun needsUpdate(installed: InstalledAddon, group: List<RemoteAddon>): Boolean {
        val source = installed.source ?: return false
        val own = group.firstOrNull { source.matches(it) }
        val best = own?.let { bestFile(it) }?.second
        val changed = when {
            best == null || !best.installable -> false
            source.fileId != null -> best.fileId != source.fileId
            else -> installed.version == null || !best.version.contains(installed.version)
        }
        val switchToForever = installed.flavor == AddonFlavor.FALLBACK &&
            group.any { it.latest[AddonFlavor.MAIN]?.installable == true }
        return changed || switchToForever
    }

    /** Forever first, then a generic file, then Classic; installable files win within that order. */
    private fun bestFile(remote: RemoteAddon): Pair<AddonFlavor, RemoteFile>? {
        val ordered = listOf(AddonFlavor.MAIN, AddonFlavor.OTHER, AddonFlavor.FALLBACK)
            .mapNotNull { flavor -> remote.latest[flavor]?.let { flavor to it } }
        return ordered.firstOrNull { it.second.installable } ?: ordered.firstOrNull()
    }

    private suspend fun fetchRemotes(records: List<InstalledAddon>): Map<Pair<String, String>, RemoteAddon> {
        val bySource = records.mapNotNull { it.source }.groupBy { it.provider }
        return coroutineScope {
            bySource.mapNotNull { (providerId, refs) ->
                val provider = providers.firstOrNull { it.id == providerId && it.enabled } ?: return@mapNotNull null
                async { safely { provider.getMany(refs.map { it.projectId }.distinct()) }.orEmpty() }
            }.awaitAll().flatten()
        }.associateBy { it.provider to it.projectId }
    }

    private fun placeholder(record: InstalledAddon) = RemoteAddon(
        provider = record.source?.provider ?: ProviderIds.LOCAL,
        projectId = record.source?.projectId ?: record.folders.firstOrNull().orEmpty(),
        name = record.name,
        summary = "",
        author = "",
        iconUrl = null,
        downloads = 0,
        latest = emptyMap(),
        websiteUrl = null,
    )

    // --- installing -----------------------------------------------------------------------------

    private suspend fun fetchRemote(addon: InstalledAddon, source: SourceRef): RemoteAddon {
        val provider = providers.firstOrNull { it.id == source.provider && it.enabled }
            ?: throw IOException("provider ${source.provider} is not available")
        return provider.get(source.projectId) ?: throw IOException("${addon.name} no longer exists on ${source.provider}")
    }

    private suspend fun installWithDependencies(remote: RemoteAddon, visiting: MutableSet<String>, onStep: (Step) -> Unit): InstalledAddon {
        visiting += "${remote.provider}:${remote.projectId}"
        val (flavor, file) = bestFile(remote) ?: throw IOException("${remote.name} has no compatible file")
        if (!file.installable) throw IOException("${remote.name} can't be downloaded by third-party apps; open ${remote.websiteUrl}")

        for (dep in file.dependencies) {
            val key = "${dep.provider}:${dep.projectId}"
            if (!visiting.add(key)) continue
            if (lock.withLock { syncDb() }.any { it.source?.provider == dep.provider && it.source.projectId == dep.projectId }) continue
            val provider = providers.firstOrNull { it.id == dep.provider && it.enabled } ?: continue
            val depRemote = provider.get(dep.projectId) ?: continue
            installWithDependencies(depRemote, visiting) {}
        }

        return downloadThenUnpack(file.downloadUrl, onStep) { folders ->
            commit(
                InstalledAddon(
                    id = "${remote.provider}:${remote.projectId}",
                    name = remote.name.trim(),
                    folders = folders,
                    version = file.version,
                    flavor = if (flavor == AddonFlavor.OTHER) classifyFolders(folders) else flavor,
                    source = SourceRef(remote.provider, remote.projectId, file.fileId),
                    installedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * Saves [record], replacing a previous record with the same id or whose folders were all
     * overwritten. Folders the previous version had but the new one doesn't are deleted unless
     * another addon lists them.
     */
    private fun commit(record: InstalledAddon): InstalledAddon {
        val records = syncDb()
        val newFolders = record.folders.toSet()
        val replaced = records.filter { it.id == record.id || newFolders.containsAll(it.folders) }
        val kept = records - replaced.toSet()
        val owned = kept.flatMap { it.folders }.toSet() + newFolders
        replaced.flatMap { it.folders }.filter { it !in owned }.distinct().forEach { deleteFolder(it) }
        db.save(kept + record)
        return record
    }

    /**
     * Downloads [url] (side by side with other downloads), then unpacks it into the folder and records it with [record]
     * while holding [lock], so two addons never write the folder or the database at once.
     */
    private suspend fun downloadThenUnpack(url: String, onStep: (Step) -> Unit, record: (List<String>) -> InstalledAddon): InstalledAddon {
        val zip = download(url, onStep)
        try {
            onStep(Step.Unpacking)
            return lock.withLock {
                val folders = withContext(Dispatchers.IO) { AddonArchive.install(zip, addonsDir) }
                record(folders)
            }
        } finally {
            zip.delete()
        }
    }

    private suspend fun download(url: String, onStep: (Step) -> Unit): File = withContext(Dispatchers.IO) {
        val parent = addonsDir.absoluteFile.parentFile ?: addonsDir
        parent.mkdirs()
        val tmp = File(parent, ".foreverywhere-download-${System.nanoTime()}.zip")
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("download failed: HTTP ${response.code}")
                val total = response.body.contentLength().takeIf { it > 0 }
                onStep(Step.Downloading(0, total))
                tmp.outputStream().use { out ->
                    val input = response.body.byteStream()
                    val buffer = ByteArray(64 * 1024)
                    var bytes = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        bytes += read
                        onStep(Step.Downloading(bytes, total))
                    }
                }
            }
            tmp
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        }
    }

    private fun classifyFolders(folders: List<String>): AddonFlavor =
        FlavorRules.classify(folders.flatMap { FlavorRules.readTocs(File(addonsDir, it)) }, target)

    /** Brings the database in line with the disk and persists it. Call with [lock] held. */
    private fun syncDb(): List<InstalledAddon> {
        val known = db.load()
        val scanned = AddonScanner.scan(addonsDir, known, target = target)
        if (scanned != known) db.save(scanned)
        return scanned
    }

    /** Deletes a top-level folder of [addonsDir]; anything that isn't a plain child name is ignored. */
    private fun deleteFolder(name: String) {
        if (name.isBlank() || name == "." || name == ".." || '/' in name || '\\' in name) return
        val target = File(addonsDir, name)
        if (target.canonicalFile.parentFile != addonsDir.canonicalFile) return
        target.deleteRecursively()
    }

    private fun SourceRef?.matches(remote: RemoteAddon): Boolean =
        this != null && provider == remote.provider && projectId == remote.projectId

    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
