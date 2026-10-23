package app.gamenative.wow.product

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InstallRegistryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun resetVersion() {
        GameVersions.selected = GameVersion.FOREVER
    }

    private fun install(product: String = "wowf"): File {
        val root = Files.createTempDirectory("wow").toFile()
        File(root, ".build.info").writeText("Active!DEC:1|Version!STRING:0|Product!STRING:0\n1|1.60.1.1|$product\n")
        File(root, "Data/config").mkdirs()
        return root
    }

    @Test
    fun emptyWhenNothingKnown() {
        val registry = InstallRegistry(context)
        assertEquals(emptyList<GameInstall>(), registry.all())
        assertNull(registry.active())
    }

    @Test
    fun migratesUpstreamGamePath() {
        val root = install("wow_classic_beta")
        context.getSharedPreferences("wow_forever", Context.MODE_PRIVATE).edit().putString("game_path", root.absolutePath).commit()
        val active = InstallRegistry(context).active()!!
        assertEquals(root.absolutePath, active.root)
        assertEquals(WowProduct.BETA, active.product)
    }

    @Test
    fun addSetActiveRemove() {
        val registry = InstallRegistry(context)
        val a = GameInstall("/a", WowProduct.BETA, "1")
        val b = GameInstall("/b", WowProduct.FOREVER, "2")
        registry.add(a)
        registry.add(b)
        assertEquals(a, registry.active())
        registry.setActive("/b")
        assertEquals(b, registry.active())
        assertEquals("/b", context.getSharedPreferences("wow_forever", Context.MODE_PRIVATE).getString("game_path", null))
        registry.remove("/b")
        assertEquals(a, registry.active())
        assertEquals(listOf(a), registry.all())
    }

    @Test
    fun foreverRegistryUnchangedAfterUpgrade() {
        // As v0.3.0 stored it: one install list, one active root, mirrored to the legacy path.
        val forever = GameInstall("/sdcard/WoW Forever", WowProduct.FOREVER, "1.60.1")
        context.getSharedPreferences("wow_installs", Context.MODE_PRIVATE).edit()
            .putString("installs", "[{\"root\":\"/sdcard/WoW Forever\",\"product\":\"FOREVER\",\"version\":\"1.60.1\"}]")
            .putString("active", "/sdcard/WoW Forever")
            .commit()
        val registry = InstallRegistry(context)
        assertEquals(forever, registry.active())
        assertEquals(listOf(forever), registry.all())
    }

    @Test
    fun versionsKeepSeparateActiveInstalls() {
        val registry = InstallRegistry(context)
        val a = GameInstall("/a", WowProduct.FOREVER, "1")
        val b = GameInstall("/b", WowProduct.CLASSIC_ERA, "2")
        registry.add(a)
        GameVersions.selected = GameVersion.CLASSIC_ERA
        assertNull(registry.active())
        registry.add(b)
        assertEquals(b, registry.active())
        assertEquals(listOf(b), registry.all())
        GameVersions.selected = GameVersion.FOREVER
        assertEquals(a, registry.active())
        assertEquals(listOf(a), registry.all())
        assertEquals("/a", context.getSharedPreferences("wow_forever", Context.MODE_PRIVATE).getString("game_path", null))
    }

    @Test
    fun removeKeepsOtherVersions() {
        val registry = InstallRegistry(context)
        registry.add(GameInstall("/a", WowProduct.FOREVER, "1"))
        registry.add(GameInstall("/b", WowProduct.RETAIL, "2"))
        registry.remove("/a")
        GameVersions.selected = GameVersion.RETAIL
        assertEquals(listOf(GameInstall("/b", WowProduct.RETAIL, "2")), registry.all())
    }

    @Test
    fun sharedFolderHoldsOneInstallPerVersion() {
        val registry = InstallRegistry(context)
        registry.add(GameInstall("/wow", WowProduct.FOREVER, "1"))
        registry.add(GameInstall("/wow", WowProduct.CLASSIC_ERA, "2"))
        assertEquals(listOf(GameInstall("/wow", WowProduct.FOREVER, "1")), registry.all())
        GameVersions.selected = GameVersion.CLASSIC_ERA
        assertEquals(GameInstall("/wow", WowProduct.CLASSIC_ERA, "2"), registry.active())
        registry.remove("/wow")
        GameVersions.selected = GameVersion.FOREVER
        assertEquals(GameInstall("/wow", WowProduct.FOREVER, "1"), registry.active())
    }

    @Test
    fun activeInstallIsMirroredToEachVersionsGamePath() {
        GameVersions.selected = GameVersion.CLASSIC_ERA
        InstallRegistry(context).add(GameInstall("/sd/World of Warcraft", WowProduct.CLASSIC_ERA, "1"))
        val legacy = context.getSharedPreferences("wow_forever", Context.MODE_PRIVATE)
        assertEquals("/sd/World of Warcraft", legacy.getString("game_path_classic_era", null))
        assertNull(legacy.getString("game_path", null))
    }
}
