package app.gamenative.wow.addons.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class RichTextTest {

    @Test
    fun stripsHtml() {
        assertEquals("Title\n• one\n• two\n\nA & B", RichText.toPlain("<h2>Title</h2><ul><li>one</li><li>two</li></ul><p>A &amp; B</p>"))
    }

    @Test
    fun stripsBbcodeButKeepsBracketedWords() {
        assertEquals("Big news [Beta] here", RichText.toPlain("[SIZE=5][b]Big news[/b][/SIZE] [Beta] here"))
        assertEquals("Donate", RichText.toPlain("[URL=\"https://example.com\"]Donate[/URL]"))
    }

    @Test
    fun collapsesBlankLines() {
        assertEquals("a\n\nb", RichText.toPlain("a\r\n\r\n\r\n\r\nb"))
    }

    @Test
    fun joinsHardWrappedLinesButKeepsParagraphsAndBullets() {
        val text = "BigWigs is a boss add-on. It consists of many\nindividual\nencounter scripts.\n\nTry these:\n* LittleWigs covers dungeons\n* Capping covers\nbattlegrounds"
        assertEquals(
            "BigWigs is a boss add-on. It consists of many individual encounter scripts.\n\nTry these:\n* LittleWigs covers dungeons\n* Capping covers battlegrounds",
            RichText.toPlain(text),
        )
    }
}
