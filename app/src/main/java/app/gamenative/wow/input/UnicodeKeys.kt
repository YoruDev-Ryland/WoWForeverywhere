package app.gamenative.wow.input

/**
 * Types characters that have no key on the US layout the X server uses (Cyrillic, accented letters, and so on,
 * issue #38). Each one goes out on a spare keycode that the X server remaps to the character's keysym, telling
 * Wine with a MappingNotify; Wine turns it into a typed character like a native keyboard would.
 *
 * Wine reads the new mapping only when it gets to that MappingNotify, so a keycode is not remapped again until
 * [SETTLE_MS] after its last use. Rotating through the spare keycodes keeps fast typing from waiting.
 */
class UnicodeKeys(private val clock: () -> Long = System::currentTimeMillis) {
    private class Slot(val keycode: Byte) {
        var keysym = 0
        var lastUsed = Long.MIN_VALUE / 2
    }

    // our layout uses keycodes up to 119, and the X server keeps keycodes in a signed byte, so 128 and up
    // would wrap negative
    private val slots = (FIRST_SPARE..LAST_SPARE).map { Slot(it.toByte()) }

    /** The spare keycode to press for [keysym], and how many ms to wait first so Wine has caught up. */
    @Synchronized
    fun reserve(keysym: Int): Pair<Byte, Long> {
        val now = clock()
        val slot = slots.firstOrNull { it.keysym == keysym } ?: slots.minBy { it.lastUsed }
        val wait = if (slot.keysym == keysym) 0L else (slot.lastUsed + SETTLE_MS - now).coerceAtLeast(0L)
        slot.keysym = keysym
        slot.lastUsed = now + wait
        return slot.keycode to wait
    }

    companion object {
        const val FIRST_SPARE = 120
        const val LAST_SPARE = 127
        const val SETTLE_MS = 250L

        /** The X11 keysym for a Unicode code point: Latin-1 keysyms equal the code point, others are 0x01000000 + it. */
        fun keysymFor(codePoint: Int): Int =
            if (codePoint in 0x20..0x7E || codePoint in 0xA0..0xFF) codePoint else 0x01000000 or codePoint
    }
}

/**
 * Turns what a keyboard app sends into edits of the game's text box. Keyboards that show pending (composing) text,
 * as voice typing and some layouts do, rewrite it as you type; the game only takes key presses, so each change is
 * sent as backspaces for the part that changed and the new characters after it.
 */
class ComposingText {
    private var composing = ""

    /** Backspaces to send and text to type so the game shows [text] in place of the pending text. */
    fun replaceWith(text: String): Pair<Int, String> {
        val common = commonPrefixCodePoints(composing, text)
        val back = composing.codePointCount(0, composing.length) - common
        val add = text.substring(text.offsetByCodePoints(0, common))
        composing = text
        return back to add
    }

    /** The pending text was accepted as typed. */
    fun finish() { composing = "" }

    private fun commonPrefixCodePoints(a: String, b: String): Int {
        var i = 0; var j = 0; var n = 0
        while (i < a.length && j < b.length) {
            val ca = a.codePointAt(i); val cb = b.codePointAt(j)
            if (ca != cb) break
            i += Character.charCount(ca); j += Character.charCount(cb); n++
        }
        return n
    }
}
