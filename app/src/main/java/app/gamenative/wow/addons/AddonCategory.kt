package app.gamenative.wow.addons

/**
 * One category list for every addon source. CurseForge, Wago and WoWInterface each name their
 * categories differently ("Bags & Inventory", "Bags, Bank, Inventory"...), so their names are mapped
 * onto these by keyword. Order is the order shown in the browser.
 */
enum class AddonCategory(val label: String, keywords: List<String>) {
    QUESTS("Quests & leveling", listOf("quest", "leveling", "character advancement")),
    MAP("Map & navigation", listOf("map", "coords", "compass", "minimap")),
    RAIDS("Raids & bosses", listOf("raid", "boss", "encounter", "dungeon")),
    COMBAT("Combat & meters", listOf("combat", "damage", "dps", "healer", "tank", "threat")),
    UNIT_FRAMES("Unit frames", listOf("unit", "ouf", "nui")),
    ACTION_BARS("Action bars", listOf("action bar")),
    BUFFS("Buffs & cooldowns", listOf("buff", "debuff", "cooldown", "casting bar", "spell")),
    BAGS("Bags & inventory", listOf("bag", "inventory", "bank")),
    AUCTION("Auction & gold", listOf("auction", "vendor", "economy", "gold")),
    PROFESSIONS("Professions", listOf("profession", "tradeskill")),
    CHAT("Chat & social", listOf("chat", "communication", "guild", "group", "friends", "mail")),
    TOOLTIPS("Tooltips", listOf("tooltip")),
    INTERFACE("Interface & media", listOf("graphic ui", "ui media", "hud", "artwork", "suite", "skin", "titan panel", "data broker", "fubar", "info bar")),
    CLASS("Class", listOf("class\\b", "druid", "hunter", "mage", "paladin", "priest", "rogue", "shaman", "warlock", "warrior", "death knight", "monk")),
    PVP("PvP", listOf("pvp", "arena", "battleground")),
    PETS("Mounts & pets", listOf("mount", "pet", "companion")),
    LIBRARIES("Libraries", listOf("librar", "ace", "developer", "development")),
    OTHER("Other", listOf("miscellaneous", "other", "roleplay", "music", "mini game", "minigame", "data mods", "plug-ins")),
    ;

    private val patterns = keywords.map { Regex("\\b$it") }

    companion object {
        /**
         * Keywords match at the start of a word ("librar" matches "Libraries", "ace" doesn't match
         * "Interface"); a trailing `\b` makes one a whole word, so "Classic - General", a game-version
         * folder rather than a topic, isn't "Class".
         */
        fun fromName(name: String): AddonCategory? {
            val lower = name.lowercase()
            return entries.firstOrNull { category -> category.patterns.any { it.containsMatchIn(lower) } }
        }

        fun fromWowInterfaceId(id: String): AddonCategory? = WOWINTERFACE_CATEGORIES[id]?.let(::fromName)

        /** WoWInterface category ids to names (from the site's category pages; the API returns ids only). */
        private val WOWINTERFACE_CATEGORIES = mapOf(
            "17" to "Graphic UI Mods", "18" to "Character Advancement", "19" to "Action Bar Mods", "20" to "Bags, Bank, Inventory",
            "21" to "Unit Mods", "22" to "Buff, Debuff, Spell", "24" to "Map, Coords, Compasses", "25" to "Combat Mods",
            "26" to "Data Mods", "27" to "Miscellaneous", "33" to "Plug-Ins & Patches", "35" to "Developer Utilities",
            "40" to "TradeSkill Mods", "45" to "Raid Mods", "47" to "Ace", "53" to "Libraries", "55" to "Chat Mods",
            "56" to "Druid", "57" to "Hunter", "58" to "Mage", "59" to "Paladin", "60" to "Priest", "61" to "Rogue",
            "62" to "Shaman", "63" to "Warlock", "64" to "Warrior", "86" to "FuBar", "94" to "Auction House & Vendors",
            "95" to "Group, Guild & Friends", "96" to "PvP, Arena, BattleGrounds", "97" to "Mail", "98" to "ToolTip",
            "99" to "Titan Panel", "100" to "Mini Games, ROFL", "108" to "Data Broker", "111" to "Other",
            "112" to "Casting Bars, Cooldowns", "113" to "Suites", "114" to "RolePlay, Music Mods", "120" to "Death Knight",
            "127" to "oUF: Layouts", "128" to "oUF: Plugins", "129" to "oUF: Core", "130" to "nUI: Core", "132" to "nUI: Layouts",
            "133" to "nUI: HUD Designs", "134" to "nUI: Unit Frame Panels", "135" to "nUI: Info Panel Plugins",
            "136" to "nUI: Enhancements", "138" to "Carbonite", "146" to "Mounts & Pets", "147" to "UI Media", "149" to "DPS",
            "150" to "Healers", "151" to "Tank", "152" to "Monk", "154" to "Utility Mods", "155" to "Garrisons",
            "160" to "Classic - General", "161" to "The Burning Crusade Classic", "162" to "WOTLK Classic", "163" to "Cataclysm Classic",
        )
    }
}
