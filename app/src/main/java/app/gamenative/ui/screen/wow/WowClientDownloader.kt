package app.gamenative.ui.screen.wow

import android.content.Context
import app.gamenative.R
import app.gamenative.utils.Net
import app.gamenative.wow.product.BuildInfo
import app.gamenative.wow.product.GameRegions
import app.gamenative.wow.product.WowProduct
import app.gamenative.wow.product.detectProduct
import app.gamenative.wow.tact.ArchiveIndex
import app.gamenative.wow.tact.Blte
import app.gamenative.wow.tact.Cdn
import app.gamenative.wow.tact.EncodingLookup
import app.gamenative.wow.tact.InstallEntry
import app.gamenative.wow.tact.InstallManifest
import app.gamenative.wow.tact.Psv
import app.gamenative.wow.tact.TactConfig
import app.gamenative.wow.tact.hex
import app.gamenative.wow.tact.md5Hex
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import timber.log.Timber

object WowClientDownloader {
    const val BUILD_INFO = BuildInfo.FILE_NAME
    private const val PLATFORM_ARCH = "arm64"
    private const val EXCLUDED_ARCH = "x86_64"
    // Blizzard's HTTPS version service; it lists every region's build like the plain-HTTP patch server on port 1119.
    private fun patchUrl(product: WowProduct) = "https://us.version.battle.net/v2/products/${product.code}"
    private const val DEFAULT_CDN_PATH = "tpr/wow"
    private val DEFAULT_CDN_HOSTS = listOf("level3.blizzard.com", "us.cdn.blizzard.com")

    private val patchHttp by lazy {
        Net.http.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    data class VersionCheckResult(
        val localVersion: String,
        val remoteVersion: String,
        val isOutdated: Boolean,
        val remoteBuildConfig: String = "",
        val remoteCdnConfig: String = "",
        val remoteCdnHosts: List<String> = emptyList(),
        val remoteCdnPath: String = DEFAULT_CDN_PATH,
    )

    /** True when the patch service lists a build of [product]. */
    fun isProductLive(product: WowProduct): Boolean {
        val versions = runCatching { fetchText("${patchUrl(product)}/versions") }.getOrNull() ?: return false
        return Psv.parse(versions).any { !it["BuildConfig"].isNullOrBlank() }
    }

    fun readBuildInfo(buildInfo: File): Map<String, String>? =
        runCatching { activeBuild(buildInfo) }.getOrNull()

    fun checkVersion(gameRoot: File, product: WowProduct? = detectProduct(gameRoot)): VersionCheckResult? {
        if (product == null) {
            Timber.w("checkVersion: no supported product in $gameRoot")
            return null
        }
        val row = runCatching { activeBuild(File(gameRoot, BUILD_INFO), product) }.getOrElse {
            Timber.w(it, "checkVersion: no readable $BUILD_INFO in $gameRoot")
            return null
        }
        if (row["Product"] != product.code) {
            Timber.w("checkVersion: wrong product '${row["Product"]}', expected '${product.code}'")
            return null
        }
        val localVersion = row["Version"] ?: run {
            Timber.w("checkVersion: row has no Version")
            return null
        }
        val localBuildKey = row["Build Key"] ?: run {
            Timber.w("checkVersion: row has no Build Key")
            return null
        }

        val versions = runCatching { fetchText("${patchUrl(product)}/versions") }.getOrElse {
            Timber.w(it, "checkVersion: http call failed")
            return null
        } ?: run {
            Timber.w("checkVersion: response body is null or unsuccessful")
            return null
        }
        // Regions patch on different days, so compare with the region the player logs into, not the first (US) row (#36).
        val region = GameRegions.effective(row["Branch"]).code
        val remoteRow = regionRow(Psv.parse(versions), "Region", region)
            ?: run {
                Timber.w("checkVersion: no remote row in $versions")
                return null
            }
        val remoteVersion = remoteRow["VersionsName"] ?: remoteRow["VersionName"] ?: remoteRow["Version"]
            ?: run {
                Timber.w("checkVersion: no remoteVersion in $remoteRow")
                return null
            }
        val remoteBuildKey = remoteRow["BuildConfig"].orEmpty()

        val cdns = runCatching { fetchText("${patchUrl(product)}/cdns") }.getOrNull()?.let(Psv::parse).orEmpty()
        val cdnsRow = regionRow(cdns, "Name", region)

        val isOutdated = localVersion != remoteVersion || (remoteBuildKey.isNotBlank() && localBuildKey != remoteBuildKey)
        Timber.i("checkVersion: region=$region, localVersion=$localVersion, remoteVersion=$remoteVersion, isOutdated=$isOutdated")
        return VersionCheckResult(
            localVersion = localVersion,
            remoteVersion = remoteVersion,
            isOutdated = isOutdated,
            remoteBuildConfig = remoteBuildKey,
            remoteCdnConfig = remoteRow["CDNConfig"].orEmpty(),
            remoteCdnHosts = (cdnsRow?.get("Hosts") ?: row["CDN Hosts"])?.let(::splitHosts) ?: DEFAULT_CDN_HOSTS,
            remoteCdnPath = cdnsRow?.get("Path") ?: row["CDN Path"] ?: DEFAULT_CDN_PATH,
        )
    }

    /** The patch service row for [region] in [column], else the US row, else the first if neither is listed. */
    internal fun regionRow(rows: List<Map<String, String>>, column: String, region: String): Map<String, String>? =
        rows.firstOrNull { it[column].equals(region, ignoreCase = true) }
            ?: rows.firstOrNull { it[column].equals("us", ignoreCase = true) }
            ?: rows.firstOrNull()

    fun updateGame(
        context: Context,
        gameRoot: File,
        product: WowProduct,
        target: VersionCheckResult,
        onProgress: (String, Float) -> Unit,
    ) {
        val cdn = Cdn(
            hosts = target.remoteCdnHosts.ifEmpty { DEFAULT_CDN_HOSTS },
            path = target.remoteCdnPath.ifEmpty { DEFAULT_CDN_PATH },
        )
        onProgress(context.getString(R.string.fw_reading_build, target.remoteVersion), -1f)
        val buildConfigRaw = cdn.require("config", target.remoteBuildConfig)
        val cdnConfigRaw = target.remoteCdnConfig.takeIf { it.isNotBlank() }?.let { cdn.require("config", it) }

        saveConfig(gameRoot, target.remoteBuildConfig, buildConfigRaw)
        cdnConfigRaw?.let { saveConfig(gameRoot, target.remoteCdnConfig, it) }

        val cdnConfig = cdnConfigRaw?.let { TactConfig.parse(String(it)) }
        syncClient(
            context = context,
            gameRoot = gameRoot,
            product = product,
            cdn = cdn,
            buildConfig = TactConfig.parse(String(buildConfigRaw)),
            archives = lazy { cdnConfig?.let { ArchiveLocator(cdn, gameRoot, it.getValue("archives")) } },
            onProgress = onProgress,
        )
        cdnConfig?.let { syncIndices(context, gameRoot, cdn, it, onProgress) }

        onProgress(context.getString(R.string.fw_finishing_update), -1f)
        updateBuildInfo(gameRoot, product, target)
    }

    fun download(context: Context, gameRoot: File, product: WowProduct, onProgress: (String, Float) -> Unit) {
        val row = activeBuild(File(gameRoot, BUILD_INFO), product)
        check(row["Product"] == product.code) { "Incompatible game product: ${row["Product"]}. Expected ${product.code}." }
        onProgress(context.getString(R.string.fw_connecting_blizzard), -1f)
        val latest = checkVersion(gameRoot, product)
        if (latest != null && latest.remoteBuildConfig.isNotBlank()) {
            return updateGame(context, gameRoot, product, latest, onProgress)
        }

        val cdn = Cdn(hosts = splitHosts(row.getValue("CDN Hosts")), path = row.getValue("CDN Path"))
        onProgress(context.getString(R.string.fw_reading_build, row["Version"].orEmpty()), -1f)
        syncClient(
            context = context,
            gameRoot = gameRoot,
            product = product,
            cdn = cdn,
            buildConfig = TactConfig.parse(String(cdn.require("config", row.getValue("Build Key")))),
            archives = lazy {
                ArchiveLocator(cdn, gameRoot, TactConfig.parse(String(cdn.require("config", row.getValue("CDN Key")))).getValue("archives"))
            },
            onProgress = onProgress,
        )
    }

    private fun syncClient(
        context: Context,
        gameRoot: File,
        product: WowProduct,
        cdn: Cdn,
        buildConfig: Map<String, List<String>>,
        archives: Lazy<ArchiveLocator?>,
        onProgress: (String, Float) -> Unit,
    ) {
        val files = InstallManifest.parse(Blte.decode(cdn.require("data", buildConfig.getValue("install")[1]))).filter { it.isClientBinary() }
        check(files.isNotEmpty()) { "No Windows ARM64 client files in this build" }

        val clientDir = File(gameRoot, product.flavorDir)
        val pending = files.filter { md5Hex(File(clientDir, it.relativePath)) != it.ckey }
        if (pending.isEmpty()) return

        onProgress(context.getString(R.string.fw_launch_looking_up_files), -1f)
        val ekeys = EncodingLookup(cdn, buildConfig.getValue("encoding")[1]).find(pending.map { it.ckey }.toSet())
        try {
            pending.forEachIndexed { index, entry ->
                val label = context.getString(R.string.fw_launch_downloading_client, entry.name, index, pending.size)
                onProgress(label, index.toFloat() / pending.size)
                // The client is usually one 80 MB file, so the bar follows its bytes rather than the file count. Index
                // reads for an archive fallback briefly count as the file too, which only nudges the bar.
                var length = -1L
                var received = 0L
                var shown = -1
                cdn.onLength = { length = it; received = 0 }
                cdn.onBytes = { bytes ->
                    received += bytes
                    if (length > 0) {
                        val fraction = (index + (received.toFloat() / length).coerceAtMost(1f)) / pending.size
                        // One update per whole percent: each one redraws the splash.
                        val percent = (fraction * 100).toInt()
                        if (percent != shown) {
                            shown = percent
                            onProgress(label, fraction)
                        }
                    }
                }
                val ekey = ekeys[entry.ckey] ?: throw IOException("${entry.name} is missing from the encoding table")
                val encoded = cdn.fetch("data", ekey) ?: archives.value?.read(ekey) ?: throw IOException("${entry.name} not found on the CDN")
                writeVerified(File(clientDir, entry.relativePath), entry, encoded)
            }
        } finally {
            cdn.onLength = null
            cdn.onBytes = null
        }
        onProgress(context.getString(R.string.fw_launch_client_downloaded), 1f)
    }

    private fun writeVerified(dest: File, entry: InstallEntry, encoded: ByteArray) {
        dest.parentFile?.mkdirs()
        val temp = File(dest.parentFile, "${dest.name}.download")
        val digest = MessageDigest.getInstance("MD5")
        DigestOutputStream(temp.outputStream().buffered(), digest).use { Blte.decode(encoded, it) }
        if (hex(digest.digest(), 0, 16) != entry.ckey) {
            temp.delete()
            throw IOException("Checksum mismatch for ${entry.name}")
        }
        dest.delete()
        check(temp.renameTo(dest)) { "Could not write ${dest.path}" }
    }

    private fun saveConfig(gameRoot: File, key: String, bytes: ByteArray) {
        val dir = File(gameRoot, "Data/config/${key.substring(0, 2)}/${key.substring(2, 4)}")
        dir.mkdirs()
        File(dir, key).writeBytes(bytes)
    }

    private fun syncIndices(
        context: Context,
        gameRoot: File,
        cdn: Cdn,
        cdnConfig: Map<String, List<String>>,
        onProgress: (String, Float) -> Unit,
    ) {
        val indicesDir = File(gameRoot, "Data/indices")
        indicesDir.mkdirs()
        val required = listOf("archives", "patch-archives").flatMap { cdnConfig[it].orEmpty() } +
            listOf("file-index", "patch-file-index", "archive-group", "patch-archive-group").mapNotNull { cdnConfig[it]?.firstOrNull() }
        val missing = required.distinct().filter { !File(indicesDir, "$it.index").isFile }
        missing.forEachIndexed { index, hash ->
            onProgress(
                context.getString(R.string.fw_launch_syncing_indexes, index, missing.size),
                index.toFloat() / missing.size,
            )
            cdn.fetch("data", "$hash.index")?.let { File(indicesDir, "$hash.index").writeBytes(it) }
        }
    }

    private fun updateBuildInfo(gameRoot: File, product: WowProduct, target: VersionCheckResult) {
        val buildInfo = File(gameRoot, BUILD_INFO)
        val info = BuildInfo.read(gameRoot) ?: return
        val updates = mapOf(
            "Version" to target.remoteVersion,
            "Build Key" to target.remoteBuildConfig,
            "CDN Key" to target.remoteCdnConfig,
            "CDN Hosts" to target.remoteCdnHosts.joinToString(" "),
            "CDN Path" to target.remoteCdnPath,
        ).filterValues { it.isNotBlank() }
        val temp = File(gameRoot, "$BUILD_INFO.tmp")
        temp.writeText(info.withUpdatedActive(product, updates).serialize())
        check(temp.renameTo(buildInfo)) { "Could not update $BUILD_INFO" }
    }

    private fun InstallEntry.isClientBinary(): Boolean {
        if ("Windows" !in tags || PLATFORM_ARCH !in tags || EXCLUDED_ARCH in tags) return false
        val lower = name.lowercase()
        return !lower.startsWith("utils\\") || "exceptionhandler" in lower
    }

    private class ArchiveLocator(private val cdn: Cdn, private val gameRoot: File, private val archives: List<String>) {
        fun read(ekey: String): ByteArray? {
            for (archive in archives) {
                val local = File(gameRoot, "Data/indices/$archive.index")
                val index = if (local.isFile) {
                    local.readBytes()
                } else {
                    val fetched = cdn.fetch("data", "$archive.index") ?: continue
                    local.parentFile?.mkdirs()
                    local.writeBytes(fetched)
                    fetched
                }
                val (offset, size) = ArchiveIndex(index).locate(ekey) ?: continue
                return cdn.fetch("data", archive, offset until offset + size)
            }
            return null
        }
    }

    private fun splitHosts(hosts: String) = hosts.split(" ").filter { it.isNotBlank() }

    private fun fetchText(url: String): String? =
        patchHttp.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }

    private fun activeBuild(buildInfo: File, product: WowProduct? = null): Map<String, String> {
        check(buildInfo.isFile) { "Missing $BUILD_INFO in ${buildInfo.parent}" }
        val info = BuildInfo.parse(buildInfo.readText())
        return (product ?: info.products().firstOrNull())?.let { info.active(it) }
            ?: info.active()
            ?: throw IllegalStateException("No build in $BUILD_INFO")
    }
}
