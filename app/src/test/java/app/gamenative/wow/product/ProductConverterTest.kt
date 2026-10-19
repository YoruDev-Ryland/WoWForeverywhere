package app.gamenative.wow.product

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProductConverterTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("wow").toFile()
        File(root, ".build.info").writeText(
            "Branch!STRING:0|Active!DEC:1|Build Key!HEX:16|Version!STRING:0|Product!STRING:0\nus|1|abcd|1.60.1.70450|wow_classic_beta\n",
        )
        File(root, "_classic_beta_/WTF/Config.wtf").apply { parentFile!!.mkdirs() }.writeText("SET portal \"test\"\n")
        File(root, "_classic_beta_/Interface/AddOns/Questie/Questie.toc").apply { parentFile!!.mkdirs() }.writeText("old")
    }

    @Test
    fun convertsAndKeepsBetaRow() {
        ProductConverter.applyLocal(root, WowProduct.BETA, WowProduct.FOREVER)
        assertEquals(WowProduct.FOREVER, detectProduct(root))
        val info = BuildInfo.read(root)!!
        assertEquals("1", info.active(WowProduct.BETA)!!["Active"])
        assertEquals("Product Flavor!STRING:0\nwowf\n", File(root, "_forever_/.flavor.info").readText())
    }

    @Test
    fun copiesAddonsAndSettingsWithoutOverwriting() {
        File(root, "_forever_/Interface/AddOns/Questie/Questie.toc").apply { parentFile!!.mkdirs() }.writeText("new")
        ProductConverter.applyLocal(root, WowProduct.BETA, WowProduct.FOREVER)
        assertEquals("new", File(root, "_forever_/Interface/AddOns/Questie/Questie.toc").readText())
        assertTrue(File(root, "_forever_/WTF/Config.wtf").isFile)
    }

    @Test
    fun portalIsNotCarriedOverFromBeta() {
        ProductConverter.applyLocal(root, WowProduct.BETA, WowProduct.FOREVER)
        assertTrue(File(root, "_forever_/WTF/Config.wtf").readLines().none { it.startsWith("SET portal ") })
    }

    @Test
    fun existingLaunchConfigIsLeftAlone() {
        ProductConverter.applyLocal(root, WowProduct.BETA, WowProduct.FOREVER)
        File(root, "_forever_/WTF/Config.wtf").writeText("SET portal \"eu\"\n")
        ProductConverter.applyLocal(root, WowProduct.BETA, WowProduct.FOREVER)
        assertEquals("SET portal \"eu\"\n", File(root, "_forever_/WTF/Config.wtf").readText())
    }

    @Test
    fun isIdempotent() {
        ProductConverter.applyLocal(root, WowProduct.BETA, WowProduct.FOREVER)
        val first = File(root, ".build.info").readText()
        ProductConverter.applyLocal(root, WowProduct.BETA, WowProduct.FOREVER)
        assertEquals(first, File(root, ".build.info").readText())
    }
}
