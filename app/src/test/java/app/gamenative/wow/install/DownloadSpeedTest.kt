package app.gamenative.wow.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadSpeedTest {

    @Test
    fun speedIsMeasuredOverRecentSamples() {
        val meter = DownloadSpeed(windowMs = 3_000)
        assertNull(meter.add(0, 0))
        assertEquals(1_000_000L, meter.add(10_000_000, 10_000))
        meter.add(14_000_000, 12_000)
        // Only the last three seconds count: 16 MB - 10 MB over 3 s.
        assertEquals(2_000_000L, meter.add(16_000_000, 13_000))
    }

    @Test
    fun timeLeftFollowsTheSpeed() {
        assertEquals(30L, DownloadSpeed.secondsLeft(done = 40_000_000, total = 100_000_000, bytesPerSecond = 2_000_000))
        assertNull(DownloadSpeed.secondsLeft(done = 0, total = 100, bytesPerSecond = 0))
    }

    @Test
    fun textIsReadable() {
        assertEquals("8.4 MB/s", DownloadSpeed.speedText(8_400_000))
        assertEquals("640 KB/s", DownloadSpeed.speedText(640_000))
        assertEquals("about 45 s left", DownloadSpeed.timeLeftText(45))
        assertEquals("about 3 min left", DownloadSpeed.timeLeftText(170))
        assertEquals("about 1 h 5 min left", DownloadSpeed.timeLeftText(3_900))
    }

    @Test
    fun summaryOnlyPromisesWhatItKnows() {
        val fresh = InstallProgress(InstallProgress.Phase.DOWNLOADING, 40_000_000, 100_000_000, "x", fetchedBytes = 40_000_000)
        assertEquals("40 MB of 100 MB  ·  2.0 MB/s  ·  about 30 s left", DownloadSpeed.summary(fresh, 2_000_000))
        // A switch reuses most files: the speed is real, but there's no honest estimate of time left.
        val reusing = fresh.copy(doneBytes = 60_000_000, fetchedBytes = 5_000_000)
        assertEquals("60 MB of 100 MB  ·  2.0 MB/s", DownloadSpeed.summary(reusing, 2_000_000))
        assertEquals("60 MB of 100 MB  ·  checking files already here", DownloadSpeed.summary(reusing, 0))
        assertEquals("100 MB of 100 MB", DownloadSpeed.summary(fresh.copy(phase = InstallProgress.Phase.FINALIZING, doneBytes = 100_000_000), 2_000_000))
    }
}
