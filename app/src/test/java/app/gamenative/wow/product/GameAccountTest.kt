package app.gamenative.wow.product

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameAccountTest {
    @Test
    fun typedNamesAreTrimmedAndKeepTheirCase() {
        assertEquals("LELLON", GameAccounts.clean("  LELLON "))
        assertEquals("My Account", GameAccounts.clean("My Account"))
    }

    @Test
    fun namesThatWouldBreakTheConfigLineAreRefused() {
        listOf("", "   ", "a|b", "a\"b", "a\nb", "!WoW1", "x".repeat(65)).forEach { assertNull(it, GameAccounts.clean(it)) }
    }

    @Test
    fun namesAreReadFromTheGamesAccountList() {
        val lines = listOf("SET portal \"US\"", "set accountList \"WoW1|!LELLON|\"")
        assertEquals(listOf("WoW1", "LELLON"), GameAccounts.namesIn(lines))
        assertEquals(emptyList<String>(), GameAccounts.namesIn(listOf("SET portal \"US\"")))
    }

    @Test
    fun suggestionsListTheGamesNamesThenTheUsualOnesWithoutRepeats() {
        assertEquals(listOf("WoW1", "WoW2"), GameAccounts.suggestions(emptyList(), null))
        assertEquals(listOf("LELLON", "wow1", "WoW2"), GameAccounts.suggestions(listOf("LELLON", "wow1"), "lellon"))
        assertEquals(listOf("WoW1", "WoW2", "Typed"), GameAccounts.suggestions(emptyList(), "Typed"))
    }

    @Test
    fun goingBackToTheGamesChoiceDropsOnlyTheAccountList() {
        val lines = listOf("SET portal \"US\"", "SET accountList \"!WoW2|\"", "SET gxApi \"D3D11\"")
        assertEquals(listOf("SET portal \"US\"", "SET gxApi \"D3D11\""), GameAccounts.withoutAccountList(lines))
    }
}
