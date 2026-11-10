package app.gamenative.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import app.gamenative.BuildConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber

object AppUpdater {
    private const val REPO = "YoruDev-Ryland/WoWForeverywhere"
    private const val RELEASES_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val APK_NAME = "WoW-Foreverywhere.apk"
    private const val USER_AGENT = "WoW-Foreverywhere"

    // github.com redirects this to the latest tag without counting against the API's 60 requests an hour per
    // address, which phones sharing one mobile data address can run out of.
    private const val LATEST_PAGE_URL = "https://github.com/$REPO/releases/latest"

    /** True when the last [check] couldn't reach GitHub, as opposed to finding no newer version. */
    @Volatile
    var lastCheckFailed = false
        private set

    data class Release(
        val version: String,
        val name: String,
        val notes: String,
        val downloadUrl: String,
        val sizeBytes: Long,
    )

    fun isNewer(latest: String, current: String = BuildConfig.VERSION_NAME): Boolean {
        val parse = { v: String ->
            v.trimStart('v', 'V')
                .split('-', '_', '+')[0]
                .split('.')
                .mapNotNull { it.toIntOrNull() }
        }
        val l = parse(latest)
        val c = parse(current)
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }

    fun parseRelease(jsonString: String, currentVersion: String = BuildConfig.VERSION_NAME): Release? = runCatching {
        val json = JSONObject(jsonString)
        val tag = json.optString("tag_name")
        if (!isNewer(tag, currentVersion)) return null

        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                return Release(
                    version = tag.trimStart('v', 'V'),
                    name = json.optString("name", tag),
                    notes = json.optString("body"),
                    downloadUrl = url,
                    sizeBytes = asset.optLong("size", 0L),
                )
            }
        }
        null
    }.getOrNull()

    suspend fun check(currentVersion: String = BuildConfig.VERSION_NAME): Release? = withContext(Dispatchers.IO) {
        val api = fetchFromApi(currentVersion)
        if (api.isSuccess) {
            lastCheckFailed = false
            return@withContext api.getOrNull()
        }
        Timber.w(api.exceptionOrNull(), "AppUpdater API check failed, trying the releases page")
        val page = fetchFromReleasesPage(currentVersion)
        lastCheckFailed = page.isFailure
        page.onFailure { Timber.w(it, "AppUpdater releases page check failed") }
        page.getOrNull()
    }

    private fun fetchFromApi(currentVersion: String): Result<Release?> = runCatching {
        val request = Request.Builder()
            .url(RELEASES_URL)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/vnd.github+json")
            .build()
        Net.http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}, rate limit left ${response.header("X-RateLimit-Remaining")}")
            parseRelease(response.body?.string() ?: error("Empty response"), currentVersion)
        }
    }

    private val noRedirectHttp by lazy { Net.http.newBuilder().followRedirects(false).followSslRedirects(false).build() }

    private fun fetchFromReleasesPage(currentVersion: String): Result<Release?> = runCatching {
        val request = Request.Builder().url(LATEST_PAGE_URL).header("User-Agent", USER_AGENT).build()
        val location = noRedirectHttp.newCall(request).execute().use { response ->
            if (!response.isRedirect) error("HTTP ${response.code}")
            response.header("Location")
        }
        val tag = tagFromLatestRedirect(location) ?: error("No tag in redirect: $location")
        if (!isNewer(tag, currentVersion)) return@runCatching null
        Release(
            version = tag.trimStart('v', 'V'),
            name = tag,
            notes = fetchReleaseNotes(tag),
            downloadUrl = "https://github.com/$REPO/releases/download/$tag/$APK_NAME",
            sizeBytes = 0L,
        )
    }

    fun tagFromLatestRedirect(location: String?): String? =
        location?.substringAfter("/releases/tag/", "")?.substringBefore('?')?.trimEnd('/')?.takeIf { it.isNotBlank() && '/' !in it }

    private fun fetchReleaseNotes(tag: String): String = runCatching {
        val request = Request.Builder()
            .url("https://raw.githubusercontent.com/$REPO/$tag/.github/release-notes.md")
            .header("User-Agent", USER_AGENT)
            .build()
        Net.http.newCall(request).execute().use { if (it.isSuccessful) it.body?.string().orEmpty() else "" }
    }.getOrDefault("")

    suspend fun download(
        context: Context,
        release: Release,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val dest = File(dir, "WoW-Foreverywhere-${release.version}.apk")
        if (dest.exists() && dest.length() == release.sizeBytes && release.sizeBytes > 0) {
            onProgress(1f)
            return@withContext dest
        }
        Net.fetchFile(release.downloadUrl, dest, onProgress)
        dest
    }

    fun canInstall(context: Context): Boolean = runCatching {
        context.packageManager.canRequestPackageInstalls()
    }.getOrElse {
        Timber.w(it, "canRequestPackageInstalls check failed")
        false
    }

    fun openInstallPermissionSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }
        }
    }

    fun install(context: Context, apkFile: File) {
        if (!canInstall(context)) {
            openInstallPermissionSettings(context)
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
