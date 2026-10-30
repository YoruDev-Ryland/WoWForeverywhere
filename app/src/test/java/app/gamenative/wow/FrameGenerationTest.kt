package app.gamenative.wow

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FrameGenerationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanUp() {
        FrameGeneration.remove(context)
    }

    private fun dll(size: Int = 300_000) = ByteArray(size).also { it[0] = 'M'.code.toByte(); it[1] = 'Z'.code.toByte() }

    @Test
    fun importsAWindowsDllAndArmsFrameGeneration() {
        assertFalse(FrameGeneration.isArmed(context))
        assertEquals(FrameGeneration.ImportResult.OK, FrameGeneration.import(context, dll().inputStream()))
        assertTrue(FrameGeneration.dllFile(context).isFile)
        assertTrue(FrameGeneration.isArmed(context))
        FrameGeneration.setEnabled(context, false)
        assertFalse(FrameGeneration.isArmed(context))
    }

    @Test
    fun rejectsFilesThatAreNotTheDll() {
        assertEquals(FrameGeneration.ImportResult.NOT_A_DLL, FrameGeneration.import(context, "hello".toByteArray().inputStream()))
        assertEquals(FrameGeneration.ImportResult.NOT_A_DLL, FrameGeneration.import(context, dll(size = 2_000).inputStream()))
        assertFalse(FrameGeneration.dllFile(context).exists())
    }

    @Test
    fun removeForgetsTheDll() {
        FrameGeneration.import(context, dll().inputStream())
        FrameGeneration.remove(context)
        assertFalse(FrameGeneration.dllFile(context).exists())
        assertFalse(FrameGeneration.isArmed(context))
    }
}
