package app.gamenative.wow

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Records what the main thread is doing when it stops responding. Android's own ANR traces are taken
 * after a stall has already ended, so they can't show the cause. This pings the main thread every
 * [PING_MS]; once a ping is unanswered for [STALL_MS] it writes the main thread's stack (sampled again
 * while the stall lasts) and every other thread's stack to `crash_logs/stall_*.txt`.
 */
object StallWatchdog {
    private const val PING_MS = 500L
    private const val STALL_MS = 2_000L
    private const val SAMPLE_MS = 1_000L
    private const val MAX_REPORTS = 10

    @Volatile private var started = false

    private fun now() = System.nanoTime() / 1_000_000

    fun start(context: Context) {
        if (started) return
        started = true
        val dir = File(context.getExternalFilesDir(null), "crash_logs")
        val main = Handler(Looper.getMainLooper())
        val lastPong = AtomicLong(now())
        Thread({
            var reported = false
            while (true) {
                main.post { lastPong.set(now()) }
                Thread.sleep(PING_MS)
                val stalledFor = now() - lastPong.get()
                if (stalledFor < STALL_MS) {
                    reported = false
                } else if (!reported) {
                    reported = true
                    runCatching { report(dir, lastPong) }
                }
            }
        }, "StallWatchdog").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
        }.start()
    }

    /**
     * Writes the snapshot straight away and appends each later sample, so the report survives even if
     * the user picks "Close app" on Android's not-responding dialog while the stall is still going.
     */
    private fun report(dir: File, lastPong: AtomicLong) {
        val started = lastPong.get()
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        dir.mkdirs()
        dir.listFiles { f -> f.name.startsWith("stall_") }?.sortedBy { it.name }?.dropLast(MAX_REPORTS - 1)?.forEach { it.delete() }
        val file = File(dir, "stall_$stamp.txt")
        val snapshot = StringBuilder("Main thread stall detected at $stamp\n\n")
        // Every thread once, at the start: the one holding what main waits for is in here.
        for ((thread, stack) in Thread.getAllStackTraces()) {
            snapshot.append("\"${thread.name}\" state=${thread.state}\n")
            stack.forEach { snapshot.append("    at $it\n") }
            snapshot.append('\n')
        }
        file.writeText(snapshot.toString())
        // Then the main thread every second until it recovers (at most 15 samples).
        val mainThread = Looper.getMainLooper().thread
        var samples = 0
        while (lastPong.get() == started && samples < 15) {
            val sample = StringBuilder("--- main thread after ${now() - started} ms ---\n")
            mainThread.stackTrace.forEach { sample.append("    at $it\n") }
            file.appendText(sample.append('\n').toString())
            samples++
            Thread.sleep(SAMPLE_MS)
        }
        file.appendText("Stall ended or sampling stopped after ${now() - started} ms\n")
    }
}
