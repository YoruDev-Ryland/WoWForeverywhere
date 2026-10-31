package app.gamenative.wow.debug

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DebugReportTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun redactsSecretsAndEmails() {
        val text = "login player@example.com with hunter22 then other.person@mail.co.uk, ok"
        assertEquals("login [email] with [redacted] then [email], ok", DebugReport.redact(text, listOf("hunter22", "abc")))
    }

    @Test
    fun reportHoldsTheSummaryAndGameLogsWithoutTheLogin() {
        val game = File(context.cacheDir, "game_test").apply { deleteRecursively() }
        File(game, "Logs").mkdirs()
        File(game, "Logs/Client.log").writeText("signed in as player@example.com using s3cretPass\n")
        File(game, "Logs/dump.bin").writeText("skipped")

        val zip = DebugReport.build(context, listOf("Region: Americas"), game, listOf("s3cretPass"))

        ZipFile(zip).use { z ->
            val names = z.entries().toList().map { it.name }
            assertTrue("summary.txt" in names)
            assertTrue("game/Logs/Client.log" in names)
            assertFalse("game/Logs/dump.bin" in names)
            val summary = z.getInputStream(z.getEntry("summary.txt")).reader().readText()
            assertTrue("Region: Americas" in summary)
            val log = z.getInputStream(z.getEntry("game/Logs/Client.log")).reader().readText()
            assertEquals("signed in as [email] using [redacted]\n", log)
        }
        game.deleteRecursively()
    }

    @Test
    fun debugLoggingSettingIsKept() {
        assertFalse(DebugLog.isEnabled(context))
        DebugLog.setEnabled(context, true)
        assertTrue(DebugLog.isEnabled(context))
        DebugLog.setEnabled(context, false)
        assertFalse(DebugLog.isEnabled(context))
    }
}
