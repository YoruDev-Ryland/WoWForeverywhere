package app.gamenative.wow.product

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InstallRegistryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

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
}
