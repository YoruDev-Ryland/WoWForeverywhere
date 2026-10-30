package app.gamenative.wow.install

/**
 * Download speed from progress samples, averaged over the last [windowMs] so it doesn't jump with
 * every file. The newest sample older than the window stays as the baseline.
 */
class DownloadSpeed(private val windowMs: Long = 3_000) {
    private val samples = ArrayDeque<Pair<Long, Long>>()

    /** Records [doneBytes] at [nowMs] and returns bytes per second, or null until there are two samples. */
    fun add(doneBytes: Long, nowMs: Long): Long? {
        samples.addLast(nowMs to doneBytes)
        while (samples.size > 2 && samples[1].first <= nowMs - windowMs) samples.removeFirst()
        val (t0, b0) = samples.first()
        val elapsed = nowMs - t0
        if (samples.size < 2 || elapsed <= 0) return null
        return (doneBytes - b0).coerceAtLeast(0) * 1000 / elapsed
    }

    companion object {
        /**
         * "40 MB of 100 MB · 2.0 MB/s · about 30 s left". Speed and time left only while files are
         * coming in; time left only when what's done so far really came from the network (a fresh
         * install), since a repair or switch reuses files it can't predict.
         */
        fun summary(progress: InstallProgress, bytesPerSecond: Long?): String {
            val parts = mutableListOf(progress.sizeText)
            if (progress.phase == InstallProgress.Phase.DOWNLOADING && progress.doneBytes < progress.totalBytes && bytesPerSecond != null) {
                if (bytesPerSecond <= 0) {
                    parts += "checking files already here"
                } else {
                    parts += speedText(bytesPerSecond)
                    if (progress.fetchedBytes >= progress.doneBytes * 9 / 10) {
                        secondsLeft(progress.doneBytes, progress.totalBytes, bytesPerSecond)?.let { parts += timeLeftText(it) }
                    }
                }
            }
            return parts.joinToString("  ·  ")
        }

        fun secondsLeft(done: Long, total: Long, bytesPerSecond: Long): Long? =
            if (bytesPerSecond <= 0 || total <= done) null else (total - done) / bytesPerSecond

        fun speedText(bytesPerSecond: Long): String = when {
            bytesPerSecond >= 1_000_000 -> "%.1f MB/s".format(bytesPerSecond / 1e6)
            else -> "%.0f KB/s".format(bytesPerSecond / 1e3)
        }

        fun timeLeftText(seconds: Long): String = when {
            seconds < 60 -> "about $seconds s left"
            seconds < 3_600 -> "about ${(seconds + 30) / 60} min left"
            else -> "about ${seconds / 3_600} h ${(seconds % 3_600 + 30) / 60} min left"
        }
    }
}
