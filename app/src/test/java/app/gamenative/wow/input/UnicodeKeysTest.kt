package app.gamenative.wow.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UnicodeKeysTest {
    @Test
    fun cyrillicUsesTheUnicodeKeysymRange() {
        assertEquals(0x0100043F, UnicodeKeys.keysymFor('п'.code))
        assertEquals(0x01000401, UnicodeKeys.keysymFor('Ё'.code))
    }

    @Test
    fun latin1KeysymsAreTheCodePoint() {
        assertEquals(0xE9, UnicodeKeys.keysymFor('é'.code))
        assertEquals(0xF1, UnicodeKeys.keysymFor('ñ'.code))
    }

    @Test
    fun differentCharactersGetDifferentSpareKeysWithoutWaiting() {
        var now = 1_000L
        val keys = UnicodeKeys { now }
        val used = "привет".map { keys.reserve(UnicodeKeys.keysymFor(it.code)) }
        assertEquals(used.map { it.first }.toSet().size, "привет".toSet().size)
        used.forEach { assertEquals(0L, it.second) }
        used.forEach { assert((it.first.toInt() and 0xFF) in UnicodeKeys.FIRST_SPARE..UnicodeKeys.LAST_SPARE) }
    }

    @Test
    fun spareKeysStayPositiveAsASignedByte() {
        val keys = UnicodeKeys { 1_000L }
        // more distinct characters than spare keys: every keycode handed out must be a valid X keycode
        "здравствуйте, мир".map { keys.reserve(UnicodeKeys.keysymFor(it.code)).first }
            .forEach { assert(it in UnicodeKeys.FIRST_SPARE.toByte()..UnicodeKeys.LAST_SPARE.toByte()) { "keycode $it" } }
    }

    @Test
    fun aRepeatedCharacterReusesItsKey() {
        val keys = UnicodeKeys { 1_000L }
        val first = keys.reserve(UnicodeKeys.keysymFor('ж'.code))
        val again = keys.reserve(UnicodeKeys.keysymFor('ж'.code))
        assertEquals(first, again.first to 0L)
    }

    @Test
    fun aKeyIsNotRemappedUntilWineHasCaughtUp() {
        var now = 1_000L
        val keys = UnicodeKeys { now }
        val spare = UnicodeKeys.LAST_SPARE - UnicodeKeys.FIRST_SPARE + 1
        // fill every spare key at the same moment, then one more character has to wait for the oldest
        (0 until spare).forEach { keys.reserve(0x01000410 + it) }
        val (keycode, wait) = keys.reserve(0x01000500)
        assertEquals(UnicodeKeys.SETTLE_MS, wait)
        assertEquals(UnicodeKeys.FIRST_SPARE, keycode.toInt() and 0xFF)
        now += UnicodeKeys.SETTLE_MS
        assertNotEquals(UnicodeKeys.FIRST_SPARE, keys.reserve(0x01000501).first.toInt() and 0xFF)
    }
}

class ComposingTextTest {
    @Test
    fun typingIntoPendingTextOnlySendsTheNewPart() {
        val c = ComposingText()
        assertEquals(0 to "п", c.replaceWith("п"))
        assertEquals(0 to "р", c.replaceWith("пр"))
        assertEquals(0 to "ивет", c.replaceWith("привет"))
    }

    @Test
    fun aCorrectionDeletesOnlyWhatChanged() {
        val c = ComposingText()
        c.replaceWith("hellp")
        assertEquals(1 to "o", c.replaceWith("hello"))
        assertEquals(3 to "", c.replaceWith("he"))
    }

    @Test
    fun acceptedTextIsNotDeletedByTheNextWord() {
        val c = ComposingText()
        c.replaceWith("привет")
        c.finish()
        assertEquals(0 to "мир", c.replaceWith("мир"))
    }

    @Test
    fun emojiCountAsOneCharacter() {
        val c = ComposingText()
        c.replaceWith("ok👍")
        assertEquals(1 to "!", c.replaceWith("ok!"))
    }
}
