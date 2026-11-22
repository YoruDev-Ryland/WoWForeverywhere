package com.winlator.xserver

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SelectionManagerTest {
    private val selections = SelectionManager(mockk(relaxed = true))
    private val clipboard = Atom.internAtom("CLIPBOARD")
    private val data = Atom.internAtom("SELECTION_DATA")

    /** A window that records the last property written to it. */
    private class Written {
        var type = 0
        var format: Property.Format? = null
        var bytes = ByteArray(0)
    }

    private val written = Written()
    private val window: Window = mockk {
        val type = slot<Int>()
        val format = slot<Property.Format>()
        val bytes = slot<ByteArray>()
        every { modifyProperty(any(), capture(type), capture(format), Property.Mode.REPLACE, capture(bytes)) } answers {
            written.type = type.captured
            written.format = format.captured
            written.bytes = bytes.captured
            mockk()
        }
    }

    private fun convert(target: String) = selections.convertServerSelection(clipboard, Atom.internAtom(target), data, window)

    @Test
    fun androidTextIsOfferedAsUtf8AndLatin1() {
        selections.offerClipboardText("Guide é ✓", window)
        assertTrue(selections.getSelection(clipboard).ownedByServer())

        assertEquals(data, convert("UTF8_STRING"))
        assertEquals("UTF8_STRING", Atom.getName(written.type))
        assertArrayEquals("Guide é ✓".toByteArray(Charsets.UTF_8), written.bytes)

        assertEquals(data, convert("STRING"))
        assertEquals("STRING", Atom.getName(written.type))
        assertArrayEquals("Guide é ?".toByteArray(Charsets.ISO_8859_1), written.bytes)
    }

    @Test
    fun targetsListChangesWithEachNewText() {
        selections.offerClipboardText("first", window)
        convert("TARGETS")
        val first = written.bytes
        assertEquals("ATOM", Atom.getName(written.type))
        assertEquals(Property.Format.INT_ARRAY, written.format)
        val atoms = (first.indices step 4).map { java.nio.ByteBuffer.wrap(first, it, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int }
        assertTrue(Atom.internAtom("UTF8_STRING") in atoms)

        selections.offerClipboardText("second", window)
        convert("TARGETS")
        assertNotEquals(first.toList(), written.bytes.toList())
    }

    @Test
    fun refusesWhatItCantConvert() {
        assertEquals(0, convert("UTF8_STRING"))
        selections.offerClipboardText("text", window)
        assertEquals(0, convert("image/png"))
        assertEquals(0, selections.convertServerSelection(Atom.getId("PRIMARY"), Atom.internAtom("UTF8_STRING"), data, window))
    }

    @Test
    fun emptyTextChangesNothing() {
        selections.offerClipboardText("", window)
        assertNull(selections.getSelection(clipboard).owner)
    }
}
