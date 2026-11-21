package app.gamenative.ui.components

import android.content.Context
import app.gamenative.PluviaApp
import app.gamenative.R
import app.gamenative.events.AndroidEvent
import app.gamenative.utils.downloader.ProgressCallback

/** Sends at most one startup update per percentage point. Cached components never report a transfer. */
class BootComponentProgress(private val context: Context, private val component: String) : ProgressCallback {
    private var lastPercent: Int? = null

    override fun onProgress(progress: Float) {
        val percent = if (progress < 0f) -1 else (progress * 100).toInt().coerceIn(0, 100)
        if (percent == lastPercent) return
        lastPercent = percent
        val label = if (percent < 0) {
            context.getString(R.string.fw_launch_downloading_component, component)
        } else {
            context.getString(R.string.fw_launch_downloading_component_percent, component, percent)
        }
        PluviaApp.events.emit(AndroidEvent.SetBootingSplashText(label, if (percent < 0) -1f else percent / 100f))
    }

    fun prepare() {
        if (lastPercent != null) {
            PluviaApp.events.emit(AndroidEvent.SetBootingSplashText(context.getString(R.string.fw_launch_preparing_component, component)))
        }
    }
}
