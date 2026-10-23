package app.gamenative.ui.screen.wow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuProfileTest {

    @Test
    fun questThreeAndThreeSAreDetected() {
        assertTrue(isMetaQuest3("Oculus", "Quest 3", "SXR2230P"))
        assertTrue(isMetaQuest3("Oculus", "Quest 3S", "SXR2230P"))
        assertTrue(isMetaQuest3("oculus", "Eureka", "SXR2230P"))
    }

    @Test
    fun otherDevicesAreNot() {
        assertFalse(isMetaQuest3("Oculus", "Quest 2", "SXR2130"))
        assertFalse(isMetaQuest3("AYN", "Odin2", "SM8550"))
        assertFalse(isMetaQuest3("samsung", "SM-S911B", "SM8550"))
    }
}
