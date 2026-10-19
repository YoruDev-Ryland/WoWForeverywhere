package app.gamenative.wow.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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

object ArtCache {
    private val memory = HashMap<String, ImageBitmap>()

    suspend fun load(context: Context, url: String): ImageBitmap? {
        synchronized(memory) { memory[url] }?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
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
                BitmapFactory.decodeFile(file.path)?.asImageBitmap()?.also { synchronized(memory) { memory[url] = it } }
            }.onFailure { Timber.w(it, "Art load failed: $url") }.getOrNull()
        }
    }

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Full-bleed art that fills its parent; shows nothing (the parent's surface) until loaded or when offline. */
@Composable
fun ArtImage(url: String?, modifier: Modifier = Modifier, zoom: Float = 1f) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, url) { value = url?.let { ArtCache.load(context, it) } }
    image?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.graphicsLayer {
                scaleX = zoom
                scaleY = zoom
            },
        )
    }
}

@Composable
fun ArtImage(art: ForeverArt, modifier: Modifier = Modifier) = ArtImage(art.url, modifier, art.zoom)
