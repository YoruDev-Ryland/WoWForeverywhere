package app.gamenative.wow.addons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddonCategoryTest {

    @Test
    fun mapsWowInterfaceNames() {
        assertEquals(AddonCategory.BAGS, AddonCategory.fromName("Bags, Bank, Inventory"))
        assertEquals(AddonCategory.ACTION_BARS, AddonCategory.fromName("Action Bar Mods"))
        assertEquals(AddonCategory.MAP, AddonCategory.fromName("Map, Coords, Compasses"))
        assertEquals(AddonCategory.RAIDS, AddonCategory.fromName("Raid Mods"))
        assertEquals(AddonCategory.AUCTION, AddonCategory.fromName("Auction House & Vendors"))
        assertEquals(AddonCategory.CLASS, AddonCategory.fromName("Death Knight"))
        assertEquals(AddonCategory.BUFFS, AddonCategory.fromName("Casting Bars, Cooldowns"))
    }

    @Test
    fun mapsCurseForgeNames() {
        assertEquals(AddonCategory.BAGS, AddonCategory.fromName("Bags & Inventory"))
        assertEquals(AddonCategory.RAIDS, AddonCategory.fromName("Boss Encounters"))
        assertEquals(AddonCategory.QUESTS, AddonCategory.fromName("Quests & Leveling"))
        assertEquals(AddonCategory.UNIT_FRAMES, AddonCategory.fromName("Unit Frames"))
        assertEquals(AddonCategory.COMBAT, AddonCategory.fromName("Damage Dealer"))
        assertEquals(AddonCategory.PROFESSIONS, AddonCategory.fromName("Professions"))
        assertEquals(AddonCategory.CHAT, AddonCategory.fromName("Chat & Communication"))
    }

    @Test
    fun wowInterfaceIdsResolve() {
        assertEquals(AddonCategory.BAGS, AddonCategory.fromWowInterfaceId("20"))
        assertEquals(AddonCategory.LIBRARIES, AddonCategory.fromWowInterfaceId("53"))
        assertNull(AddonCategory.fromWowInterfaceId("99999"))
    }

    @Test
    fun gameVersionCategoriesAreNotTopics() {
        assertNull(AddonCategory.fromName("Classic - General"))
        assertNull(AddonCategory.fromWowInterfaceId("160"))
        assertEquals(AddonCategory.CLASS, AddonCategory.fromName("Class"))
        assertEquals(AddonCategory.INTERFACE, AddonCategory.fromName("Graphic UI Mods"))
        assertNull(AddonCategory.fromName("Surface Tension"))
    }

    @Test
    fun unknownNameIsNull() {
        assertNull(AddonCategory.fromName("Twitch Integration"))
    }
}
