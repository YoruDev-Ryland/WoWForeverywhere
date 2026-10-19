package app.gamenative.wow.install

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.gamenative.R
import app.gamenative.wow.product.GameInstall
import app.gamenative.wow.product.InstallRegistry
import app.gamenative.wow.product.WowProduct
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface InstallUiState {
    data object Idle : InstallUiState
    data class Running(val request: InstallRequest, val progress: InstallProgress) : InstallUiState
    data class Paused(val request: InstallRequest, val progress: InstallProgress?, val reason: String? = null) : InstallUiState
    data class Failed(val request: InstallRequest, val message: String) : InstallUiState
    data class Done(val install: GameInstall) : InstallUiState
}

/** Start, pause and resume game installs. The work runs in [InstallService] so it survives leaving the app. */
object InstallController {
    private val _state = MutableStateFlow<InstallUiState>(InstallUiState.Idle)
    val state: StateFlow<InstallUiState> = _state.asStateFlow()

    internal fun update(state: InstallUiState) {
        _state.value = state
    }

    fun start(context: Context, req: InstallRequest) {
        val intent = Intent(context, InstallService::class.java)
            .setAction(InstallService.ACTION_START)
            .putExtra(InstallService.EXTRA_ROOT, req.root.absolutePath)
            .putExtra(InstallService.EXTRA_PRODUCT, req.product.code)
            .putExtra(InstallService.EXTRA_LOCALE, req.locale)
        ContextCompat.startForegroundService(context, intent)
    }

    fun pause(context: Context) {
        context.startService(Intent(context, InstallService::class.java).setAction(InstallService.ACTION_PAUSE))
    }

    fun resume(context: Context) {
        when (val s = state.value) {
            is InstallUiState.Paused -> start(context, s.request)
            is InstallUiState.Failed -> start(context, s.request)
            else -> Unit
        }
    }

    fun reset() {
        if (_state.value !is InstallUiState.Running) _state.value = InstallUiState.Idle
    }
}

class InstallService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var request: InstallRequest? = null
    private var lastProgress: InstallProgress? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotified = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val req = InstallRequest(
                    root = File(intent.getStringExtra(EXTRA_ROOT) ?: return START_NOT_STICKY),
                    product = WowProduct.fromCode(intent.getStringExtra(EXTRA_PRODUCT)) ?: return START_NOT_STICKY,
                    locale = intent.getStringExtra(EXTRA_LOCALE) ?: "enUS",
                )
                startInstall(req)
            }
            ACTION_PAUSE -> pause(null)
            ACTION_CANCEL -> {
                pause(null)
                InstallController.reset()
            }
        }
        return START_NOT_STICKY
    }

    private fun startInstall(req: InstallRequest) {
        if (job?.isActive == true) return
        request = req
        startForegroundCompat(notification(req, null))
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "foreverywhere:install")
            .apply { acquire(WAKE_LOCK_MS) }
        job = scope.launch {
            try {
                GameInstaller().run(req) { progress ->
                    lastProgress = progress
                    InstallController.update(InstallUiState.Running(req, progress))
                    val now = System.currentTimeMillis()
                    if (now - lastNotified > 1000) {
                        lastNotified = now
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(req, progress))
                    }
                }
                val product = req.product
                val version = app.gamenative.wow.product.BuildInfo.read(req.root)?.active(product)?.get("Version").orEmpty()
                val install = GameInstall(req.root.absolutePath, product, version)
                InstallRegistry(applicationContext).apply {
                    add(install)
                    setActive(install.root)
                }
                InstallController.update(InstallUiState.Done(install))
                finish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Install failed")
                InstallController.update(InstallUiState.Failed(req, e.message ?: "Install failed"))
                finish()
            }
        }
    }

    private fun pause(reason: String?) {
        val req = request ?: return
        job?.cancel()
        job = null
        InstallController.update(InstallUiState.Paused(req, lastProgress, reason))
        finish()
    }

    /** Android 15+ stops data-sync services after about six hours a day; keep the progress and pause. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        pause("Android paused the download after several hours. Resume it to continue.")
    }

    private fun finish() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(req: InstallRequest, progress: InstallProgress?): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Game downloads", NotificationManager.IMPORTANCE_LOW))
        }
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        fun action(name: String, label: String) = NotificationCompat.Action(
            0,
            label,
            PendingIntent.getService(this, name.hashCode(), Intent(this, InstallService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE),
        )
        val total = progress?.totalBytes ?: 0
        val done = progress?.doneBytes ?: 0
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.fw_ic_download)
            .setContentTitle("Installing ${req.product.displayName}")
            .setContentText(if (total > 0) "%.1f of %.1f GB".format(done / 1e9, total / 1e9) else "Preparing download…")
            .setProgress(1000, if (total > 0) (done * 1000 / total).toInt() else 0, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(action(ACTION_PAUSE, "Pause"))
            .addAction(action(ACTION_CANCEL, "Cancel"))
            .build()
    }

    companion object {
        const val ACTION_START = "app.wowforeverywhere.install.START"
        const val ACTION_PAUSE = "app.wowforeverywhere.install.PAUSE"
        const val ACTION_CANCEL = "app.wowforeverywhere.install.CANCEL"
        const val EXTRA_ROOT = "root"
        const val EXTRA_PRODUCT = "product"
        const val EXTRA_LOCALE = "locale"
        private const val CHANNEL = "installs"
        private const val NOTIFICATION_ID = 4101
        private const val WAKE_LOCK_MS = 8 * 60 * 60 * 1000L
    }
}
