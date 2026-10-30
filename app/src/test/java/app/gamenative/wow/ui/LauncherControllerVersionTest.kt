package app.gamenative.wow.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.gamenative.wow.install.InstallController
import app.gamenative.wow.install.InstallProgress
import app.gamenative.wow.install.InstallRequest
import app.gamenative.wow.install.InstallUiState
import app.gamenative.wow.product.GameInstall
import app.gamenative.wow.product.GameVersion
import app.gamenative.wow.product.GameVersions
import app.gamenative.wow.product.WowProduct
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Android 10: Robolectric has no storage volumes for the Android 11+ all-files-access check.
@Config(sdk = [29])
@RunWith(RobolectricTestRunner::class)
class LauncherControllerVersionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun launcher() = LauncherController(context, CoroutineScope(Dispatchers.Unconfined)) {}

    @After
    fun reset() {
        InstallController.update(InstallUiState.Idle)
        GameVersions.selected = GameVersion.FOREVER
        app.gamenative.wow.product.ForeverTracks.selected = app.gamenative.wow.product.ForeverTrack.LAUNCH
    }

    @Test
    fun switchRefusedWhileInstalling() {
        val launcher = launcher()
        InstallController.update(
            InstallUiState.Running(InstallRequest(File("/x"), WowProduct.FOREVER), InstallProgress(InstallProgress.Phase.entries.first(), 0, 1, null)),
        )
        assertFalse(launcher.switchVersion(GameVersion.RETAIL))
        assertEquals(GameVersion.FOREVER, GameVersions.selected)
        assertEquals(GameVersion.FOREVER, launcher.version)
    }

    @Test
    fun switchReloadsPathForVersion() {
        val launcher = launcher()
        launcher.registry.add(GameInstall("/forever", WowProduct.FOREVER, "1"))
        GameVersions.selected = GameVersion.RETAIL
        launcher.registry.add(GameInstall("/retail", WowProduct.RETAIL, "2"))
        GameVersions.selected = GameVersion.FOREVER

        assertTrue(launcher.switchVersion(GameVersion.RETAIL))
        assertEquals("/retail", launcher.gamePath)
        assertEquals(GameVersion.RETAIL, launcher.version)
        assertEquals("retail", context.getSharedPreferences("wow_settings", Context.MODE_PRIVATE).getString("game_version", null))

        assertTrue(launcher.switchVersion(GameVersion.FOREVER))
        assertEquals("/forever", launcher.gamePath)
    }

    @Test
    fun foreverTrackSwitchIsSavedAndRefusedWhileInstalling() {
        val launcher = launcher()
        assertTrue(launcher.switchForeverTrack(app.gamenative.wow.product.ForeverTrack.BETA))
        assertEquals(app.gamenative.wow.product.ForeverTrack.BETA, app.gamenative.wow.product.ForeverTracks.selected)
        assertEquals("beta", context.getSharedPreferences("wow_settings", Context.MODE_PRIVATE).getString("forever_track", null))
        InstallController.update(
            InstallUiState.Running(InstallRequest(File("/x"), WowProduct.FOREVER), InstallProgress(InstallProgress.Phase.entries.first(), 0, 1, null)),
        )
        assertFalse(launcher.switchForeverTrack(app.gamenative.wow.product.ForeverTrack.LAUNCH))
        assertEquals(app.gamenative.wow.product.ForeverTrack.BETA, launcher.foreverTrack)
    }
}
