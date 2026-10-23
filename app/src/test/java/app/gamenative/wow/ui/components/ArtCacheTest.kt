package app.gamenative.wow.ui.components

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtCacheTest {

    @Test
    fun concurrentLoadsOfOneFileAllSucceed() {
        val file = File(Files.createTempDirectory("art").toFile(), "icon.img")
        val bothWriting = CountDownLatch(2)
        val pool = Executors.newFixedThreadPool(2)
        val results = (1..2).map {
            pool.submit<File?> {
                cacheOnce(file) { temp ->
                    bothWriting.countDown()
                    bothWriting.await(5, TimeUnit.SECONDS)
                    temp.writeText("<svg/>")
                    true
                }
            }
        }.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()
        assertEquals(listOf(file, file), results)
        assertEquals("<svg/>", file.readText())
        assertEquals(listOf("icon.img"), file.parentFile!!.list()!!.toList())
    }
}
