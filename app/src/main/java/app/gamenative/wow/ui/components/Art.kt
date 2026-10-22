package app.gamenative.wow.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.gamenative.utils.Net
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import timber.log.Timber

/**
 * WoW Forever promo art from Blizzard's official page, loaded at runtime from Blizzard's image CDN and
 * cached on the device, so Blizzard's art isn't redistributed inside the APK or the repo.
 * [zoom] crops the decorative frame some screenshots have baked into their edges.
 */
enum class ForeverArt(val path: String, val zoom: Float = 1f) {
    KEY_ART("blt4d412035d16da095/6aa204fb2c0580bfcf273e54/masthead-art.jpg"),
    INTERFACE("blt848d2f0dbc2d2d1e/6aa09dd67ec8fedf4f00059c/Every_Journey_Matters.jpg", 1.1f),
    ASHENVALE("blt58abba267979c75c/6a985e232437ed4648d48726/Updates_Ashenvale_01.jpg"),
    SKYBORNE("blt86ec9f1909d2e4f3/6a99ff068102310aeb81e0de/Skyborne-BG.jpg"),
    CAMP("blt50839e89a98e4d22/6aa09f512437ed84d9d48846/System_Revamps.jpg", 1.1f),
    ;

    val url get() = "https://blz-contentstack-images.akamaized.net/v3/assets/blt9c12f249ac15c7ec/$path?format=jpg&width=1280"
}

/**
 * Blizzard's own round game icons (the ones its site navigation uses), loaded at runtime like the art.
 * [viewBox] crops the icon to just its round crest (Classic's icon has a wordmark underneath).
 */
enum class GameIcon(val url: String, val viewBox: String? = null) {
    FOREVER("https://forge.akamaized.net/icons/world-of-warcraft-forever.svg"),
    CLASSIC("https://forge.akamaized.net/icons/world-of-warcraft-classic.svg", viewBox = "6 2 36 36"),
}

object ArtCache {
    /** Decoded images, capped at about 48 MB; anything evicted is re-read from the disk cache. */
    private val memory = object : android.util.LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /** Renders an SVG icon to a square bitmap of [sizePx], cached like other art. */
    suspend fun loadSvg(context: Context, icon: GameIcon, sizePx: Int): ImageBitmap? {
        val key = "${icon.url}@$sizePx"
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = fetch(context, icon.url) ?: return@runCatching null
                val svg = com.caverock.androidsvg.SVG.getFromString(file.readText())
                icon.viewBox?.split(" ")?.map { it.toFloat() }?.let { (x, y, w, h) -> svg.setDocumentViewBox(x, y, w, h) }
                svg.documentWidth = sizePx.toFloat()
                svg.documentHeight = sizePx.toFloat()
                val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
                svg.renderToCanvas(android.graphics.Canvas(bitmap))
                bitmap.asImageBitmap().also { memory.put(key, it) }
            }.onFailure { Timber.w(it, "Icon load failed: ${icon.url}") }.getOrNull()
        }
    }

    /** Downloads [url] into the disk cache once and returns the cached file. */
    private fun fetch(context: Context, url: String): File? {
        val dir = File(context.cacheDir, "art").apply { mkdirs() }
        val file = File(dir, sha1(url) + ".img")
        if (file.isFile && file.length() > 0L) return file
        val temp = File(dir, file.name + ".part")
        Net.http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            temp.outputStream().use { response.body!!.byteStream().copyTo(it) }
        }
        return file.takeIf { temp.renameTo(it) }
    }

    /**
     * Loads [url], from memory, then the disk cache, then the network. Addon icons and screenshots
     * pass [keepOnDisk] false: CurseForge's API terms don't allow storing its data, so those stay in
     * memory only and are downloaded again in a later session.
     */
    suspend fun load(context: Context, url: String, keepOnDisk: Boolean = true): ImageBitmap? {
        memory.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                if (!keepOnDisk) {
                    val bytes = Net.http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (!response.isSuccessful) return@runCatching null
                        response.body!!.bytes()
                    }
                    return@runCatching decode(bytes)?.asImageBitmap()?.also { memory.put(url, it) }
                }
                val dir = File(context.cacheDir, "art").apply { mkdirs() }
                val file = File(dir, sha1(url) + ".img")
                if (!file.isFile || file.length() == 0L) {
                    val temp = File(dir, file.name + ".part")
                    Net.http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (!response.isSuccessful) return@runCatching null
                        temp.outputStream().use { response.body!!.byteStream().copyTo(it) }
                    }
                    temp.renameTo(file)
                }
                decode(file.readBytes())?.asImageBitmap()?.also { memory.put(url, it) }
            }.onFailure { Timber.w(it, "Art load failed: $url") }.getOrNull()
        }
    }

    /** Decodes at most ~1600 px wide: screenshots can be far larger than any tile. */
    private fun decode(bytes: ByteArray): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_WIDTH) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private const val MAX_WIDTH = 1600

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Full-bleed art that fills its parent; shows nothing (the parent's surface) until loaded or when offline. */
@Composable
fun ArtImage(url: String?, modifier: Modifier = Modifier, zoom: Float = 1f, fitIfNarrowerThan: Int = 0, keepOnDisk: Boolean = true) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, url) { value = url?.let { ArtCache.load(context, it, keepOnDisk) } }
    image?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            // Small previews stay sharp at their own size instead of being blown up to fill the box.
            contentScale = if (it.width < fitIfNarrowerThan) ContentScale.Fit else ContentScale.Crop,
            modifier = modifier.graphicsLayer {
                scaleX = zoom
                scaleY = zoom
            },
        )
    }
}

@Composable
fun ArtImage(art: ForeverArt, modifier: Modifier = Modifier) = ArtImage(art.url, modifier, art.zoom)

/** A Blizzard game icon at [size]; shows [placeholder] until it loads (or when offline). */
@Composable
fun GameIconImage(icon: GameIcon, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, placeholder: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    val image by produceState<ImageBitmap?>(null, icon, px) { value = ArtCache.loadSvg(context, icon, px) }
    val loaded = image
    if (loaded != null) {
        Image(loaded, contentDescription = null, modifier = modifier.then(Modifier.size(size)))
    } else {
        placeholder()
    }
}
