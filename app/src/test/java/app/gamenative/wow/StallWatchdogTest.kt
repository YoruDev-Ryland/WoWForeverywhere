package app.gamenative.wow

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class StallWatchdogTest {

    @Test
    fun writesReportWhenMainThreadDoesNotRespond() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val dir = File(context.getExternalFilesDir(null), "crash_logs")
        StallWatchdog(dir).run()
        // The paused main looper never runs the watchdog's pings: a stall from its point of view.
        Thread.sleep(4_000)
        val reports = dir.listFiles { f -> f.name.startsWith("stall_") }.orEmpty()
        assertTrue("no stall report written", reports.isNotEmpty())
        val text = reports.first().readText()
        assertTrue(text.startsWith("Main thread stall detected"))
        assertTrue("main thread samples missing", "--- main thread after" in text)
        assertTrue("other threads missing", "\"StallWatchdog\"" in text)
    }
}
