package app.gamenative.ui.screen.wow

import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import app.gamenative.wow.product.GameVersion
import app.gamenative.wow.product.GameVersions
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GamePathVersionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun resetVersion() {
        GameVersions.selected = GameVersion.FOREVER
    }

    @Test
    fun defaultFolderFollowsVersion() {
        assertEquals(File(Environment.getExternalStorageDirectory(), "WoW Forever").absolutePath, GamePath.load(context))
        GameVersions.selected = GameVersion.RETAIL
        assertEquals(File(Environment.getExternalStorageDirectory(), "World of Warcraft").absolutePath, GamePath.load(context))
    }

    @Test
    fun savedPathIsPerVersion() {
        GamePath.save(context, "/forever")
        GameVersions.selected = GameVersion.MISTS
        GamePath.save(context, "/mists")
        assertEquals("/mists", GamePath.load(context))
        GameVersions.selected = GameVersion.FOREVER
        assertEquals("/forever", GamePath.load(context))
        assertEquals("/forever", context.getSharedPreferences("wow_forever", Context.MODE_PRIVATE).getString("game_path", null))
    }
}
