package app.gamenative.wow.addons

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class AddonDbTest {

    @Test
    fun legacyFlavorNamesLoad() {
        val file = File(Files.createTempDirectory("db").toFile(), AddonDb.FILE_NAME)
        file.writeText(
            """[{"id":"a","name":"A","folders":["A"],"version":"1","flavor":"FOREVER","source":null,"installedAt":1},
               {"id":"b","name":"B","folders":["B"],"version":"1","flavor":"CLASSIC","source":null,"installedAt":1}]""",
        )
        val db = AddonDb(file)
        assertEquals(listOf(AddonFlavor.MAIN, AddonFlavor.FALLBACK), db.load().map { it.flavor })
        db.save(db.load())
        assertEquals(true, file.readText().contains("\"FOREVER\""))
    }
}
