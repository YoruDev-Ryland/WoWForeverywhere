# Other WoW Versions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Settings choice that switches the whole app between WoW Forever (default) and four other official WoW versions, with per-version installs and addons.

**Architecture:** A `GameVersion` enum groups `WowProduct`s; a process-wide `GameVersions.selected` (persisted in SharedPreferences, default Forever) filters product detection, the install registry and the default install folder. Addons get a per-version `AddonTarget`; `AddonFlavor` becomes the version-neutral roles `MAIN`/`FALLBACK`/`OTHER` (serialized under the old names) so `AddonManager` needs no logic change.

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx.serialization, OkHttp, JUnit/Robolectric, Python (catalog builder).

**Spec:** `docs/superpowers/specs/2026-10-22-game-versions-design.md`

## Global Constraints

- Default (setting never changed): identical screens, text, addon results, install paths and files. Pref missing/unknown ⇒ Forever.
- Products: `wowf`/`wow_classic_beta` (Forever), `wow_classic_era` → `_classic_era_` / `WowClassic-arm64.exe`, `wow_anniversary` → `_anniversary_` / `WowClassic-arm64.exe`, `wow_classic` → `_classic_` / `WowClassic-arm64.exe`, `wow` → `_retail_` / `Wow-ARM64.exe`. Portal `US` (Branch region wins, as today).
- Display names: "WoW Forever", "Classic Era", "Burning Crusade Anniversary", "Mists of Pandaria Classic", "Retail".
- CurseForge type ids: Forever 88568, Classic Era 67408, Anniversary 73246, Mists 79434, Retail 517. Wago: `forever`, `classic`, `bc`, `mop`, `retail`.
- Default install folder: Forever `WoW Forever`; others `World of Warcraft`.
- Icons: Retail `https://forge.akamaized.net/icons/world-of-warcraft.svg`; Classic family uses `GameIcon.CLASSIC`.
- Copy: plain, professional, no em dashes. Never run `formatKotlin`. Build with JDK 21 (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk`) and `-PWAGO_API_KEY=`; check output for `error:`/`e: `/`BUILD FAILED`.
- Test command: `./gradlew testModernDebugUnitTest --tests '<pattern>' -PWAGO_API_KEY=`.

## Review Focus

1. An existing Forever user upgrading: stored installs (no version info) and `wow_forever/game_path` still resolve to the same Forever install; the app opens on Home as before. → Task 2 test `foreverRegistryUnchangedAfterUpgrade`.
2. A shared folder holding `wowf` and `wow_classic_era` rows resolves to whichever version is selected, and installing one keeps the other's `.build.info` row. → Task 1 test `sharedInstallFollowsSelectedVersion`, Task 2 test `installerKeepsOtherProductRows`.
3. Addon databases written by v0.3.0 (`"flavor": "FOREVER"`/`"CLASSIC"`) load unchanged. → Task 3 test `legacyFlavorNamesLoad`.
4. Switching versions while an install runs is refused. → Task 4 test `switchRefusedWhileInstalling`.
5. In Forever mode no Retail/Mists/Anniversary-only addon appears. → Task 3 test `foreverTargetDropsOtherVersions` per provider.

---

### Task 1: Game version model and detection

**Files:**
- Modify: `app/src/main/java/app/gamenative/wow/product/WowProduct.kt`
- Create: `app/src/main/java/app/gamenative/wow/product/GameVersion.kt`
- Modify: `app/src/main/java/app/gamenative/wow/product/BuildInfo.kt` (`products`)
- Test: `app/src/test/java/app/gamenative/wow/product/GameVersionTest.kt`

**Interfaces:**
- Produces: `WowProduct.CLASSIC_ERA, ANNIVERSARY, MISTS, RETAIL`; `enum class GameVersion(val key: String, val displayName: String, val products: List<WowProduct>, val defaultFolder: String)` with `FOREVER("forever", "WoW Forever", [FOREVER, BETA], "WoW Forever")`, `CLASSIC_ERA("classic_era", …)`, `ANNIVERSARY("anniversary", …)`, `MISTS("mists", …)`, `RETAIL("retail", …)`; `val isExperimental get() = this != FOREVER`; `GameVersion.of(product: WowProduct): GameVersion`; `GameVersion.fromKey(key: String?): GameVersion` (unknown ⇒ FOREVER); `object GameVersions { @Volatile var selected: GameVersion; fun load(context: Context); fun save(context: Context, version: GameVersion) }` (prefs `wow_settings`, key `game_version`); `BuildInfo.products(today: LocalDate = LocalDate.now(), version: GameVersion = GameVersions.selected)`.

- [ ] **Step 1: Write failing tests** in `GameVersionTest`: `fromKeyDefaultsToForever` (`fromKey(null)`, `fromKey("x")` ⇒ FOREVER); `sharedInstallFollowsSelectedVersion` (BuildInfo with rows `wowf` and `wow_classic_era`: `products(version = FOREVER)` == `[FOREVER]`, `products(version = CLASSIC_ERA)` == `[CLASSIC_ERA]`); `foreverOrderingUnchanged` (rows `wowf`+`wow_classic_beta` before 2026-11-04 ⇒ `[BETA, FOREVER]`, after ⇒ `[FOREVER, BETA]`); `newProductsHaveVerifiedValues` (codes, flavor dirs, exe names from Global Constraints).
- [ ] **Step 2: Run, expect compile failure.**
- [ ] **Step 3: Implement.** `products()` filters `WowProduct.entries` to `version.products`, keeping today's release-date sort. `GameVersions.load` reads prefs once in `PluviaApp.onCreate` (next to `StallWatchdog.start`).
- [ ] **Step 4: Run `GameVersionTest`, `BuildInfoTest`; expect PASS.**
- [ ] **Step 5: Commit** `feat: game version model; detection follows the selected version`.

### Task 2: Per-version installs and paths

**Files:**
- Modify: `app/src/main/java/app/gamenative/wow/product/InstallRegistry.kt`
- Modify: `app/src/main/java/app/gamenative/ui/screen/wow/WoWForeverScreen.kt` (`GamePath` only)
- Test: `app/src/test/java/app/gamenative/wow/product/InstallRegistryTest.kt` (Robolectric), `app/src/test/java/app/gamenative/wow/install/GameInstallerTest.kt` (add case)

**Interfaces:**
- Consumes: `GameVersions.selected`, `GameVersion.of`, `GameVersion.defaultFolder`.
- Produces: `InstallRegistry.all()` returns only the selected version's installs; `active()` per version (Forever keeps keys `active` and legacy `wow_forever/game_path`; others use `active_<key>` and `game_path_<key>`); `add/remove/setActive` keep other versions' entries. `GamePath.load/save/defaultPath` per selected version; `GamePath.findGameRoot` unchanged.

- [ ] **Step 1: Failing tests:** `foreverRegistryUnchangedAfterUpgrade` (prefs pre-seeded the v0.3.0 way ⇒ `active()` same root under FOREVER); `versionsKeepSeparateActiveInstalls` (add Forever install A, switch to CLASSIC_ERA, add B ⇒ `active()` is B; switch back ⇒ A, `all()` == [A]); `installerKeepsOtherProductRows` (GameInstaller writing `.build.info` for `wow_classic_era` into a folder with a `wowf` row keeps the `wowf` row byte-identical; use the existing installer test fixtures).
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement.** If the installer test fails, fix `.build.info` writing so only the target product's row changes.
- [ ] **Step 4: Run tests; PASS.**
- [ ] **Step 5: Commit** `feat: each game version keeps its own install`.

### Task 3: Addon targets for every version

**Files:**
- Modify: `app/src/main/java/app/gamenative/wow/addons/AddonFlavor.kt` (rename roles, add `AddonTarget`, `FlavorSpec`, parametrize `FlavorRules`)
- Modify: `AddonManager.kt`, `AddonScanner.kt`, `provider/CurseForgeProvider.kt`, `provider/WagoProvider.kt`, `provider/WowInterfaceProvider.kt`, `provider/GitHubProvider.kt`, `wow/ui/addons/AddonsController.kt`, `wow/ui/addons/AddonsScreen.kt` (rename only here)
- Modify: `tools/catalog/build_catalog.py`
- Test: existing provider tests + new cases; `AddonFlavorTest`/`AddonDbTest`

**Interfaces:**
- Produces: `enum class AddonFlavor { @SerialName("FOREVER") MAIN, @SerialName("CLASSIC") FALLBACK, OTHER }`; `data class FlavorSpec(val label: String, val curseForgeTypeId: Int, val wagoGameVersion: String, val catalogKey: String, val tocSuffixes: List<String>, val interfaces: (Int) -> Boolean, val wowInterface: (major: Int, minor: Int, name: String) -> Boolean)`; `data class AddonTarget(val main: FlavorSpec, val fallback: FlavorSpec?)` with `AddonTarget.FOREVER` (main Forever, fallback Classic Era) and `fun GameVersion.addonTarget(): AddonTarget`; every provider and `FlavorRules.pickToc/classify/ofTocFileName` take `target: AddonTarget = AddonTarget.FOREVER`.
- Spec values: Forever (`forever`, suffixes `-Forever,_Forever,-Camelot,_Camelot`, interface 16000–16099, WoWI 1.60+ or name forever/camelot); Classic Era (`classic`, `-Classic,_Classic,-Vanilla,_Vanilla`, 10000–15999, WoWI major 1 below 1.60); Anniversary (`bcc`, `-TBC,_TBC,-BCC,_BCC`, 20000–29999, WoWI major 2); Mists (`mists`, `-Mists,_Mists,-MoP,_MoP`, 50000–59999, WoWI major 5); Retail (`mainline`, `-Mainline,_Mainline`, ≥100000, WoWI major ≥10).
- Catalog: add `bcc`/`tbc`→`bcc`, `mists`/`mop`→`mists`, `mainline`/`retail`→`mainline` to `FLAVORS`; existing `forever`/`classic` keys unchanged.

- [ ] **Step 1: Failing tests:** `legacyFlavorNamesLoad` (AddonDb JSON with `"FOREVER"`/`"CLASSIC"` ⇒ MAIN/FALLBACK); per provider `retailTargetPicksRetailFile` (CurseForge mod with type 517 file ⇒ MAIN under `AddonTarget` for RETAIL; Wago request uses `game_version=retail`; WoWI compat `12.1.5` ⇒ MAIN; GitHub catalog key `mainline` ⇒ MAIN) and `foreverTargetDropsOtherVersions` (same inputs under FOREVER ⇒ dropped/no MAIN); `FlavorRules` picks `-Mainline` TOC for RETAIL.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement**; existing tests keep passing after mechanical rename (FOREVER→MAIN, CLASSIC→FALLBACK). Wago with no fallback queries only the main version. `AddonsController(gameRoot, product, scope)` builds the target from `GameVersion.of(product)`.
- [ ] **Step 4: Run all addon tests; PASS. Run `python3 tools/catalog/build_catalog.py --help`-free sanity: `python3 -c "import ast,sys;ast.parse(open('tools/catalog/build_catalog.py').read())"`.**
- [ ] **Step 5: Commit** `feat: addon sources follow the selected game version`.

### Task 4: Switching, launch and first run

**Files:**
- Modify: `wow/ui/LauncherController.kt`, `wow/ui/ForeverywhereApp.kt`, `wow/ui/FirstRunScreen.kt`, `wow/ui/GameFilesScreen.kt`, `ui/screen/wow/WoWForeverScreen.kt` (`installPortal`, error text)
- Test: `app/src/test/java/app/gamenative/wow/ui/LauncherControllerTest.kt` (Robolectric; create if absent)

**Interfaces:**
- Consumes: Tasks 1–2.
- Produces: `LauncherController.version: GameVersion` (state); `fun switchVersion(to: GameVersion): Boolean` (false while `installing`/busy; else saves, reloads `gamePath` from registry/GamePath, `checkFiles()`); `ForeverywhereApp` after a switch navigates to HOME if `registry.active() != null || !filesMissing`, else FIRST_RUN, clearing the back stack. FirstRun: product for install = Forever logic when FOREVER, else `version.products.single()`; folder = `version.defaultFolder`; texts use `version.displayName`; Welcome gets ghost button "Playing another version of WoW?" ⇒ Settings. Beta conversion only when `version == FOREVER`.

- [ ] **Step 1: Failing tests:** `switchRefusedWhileInstalling`; `switchReloadsPathForVersion`.
- [ ] **Step 2: Run, FAIL. Step 3: Implement. Step 4: PASS.**
- [ ] **Step 5: Commit** `feat: switching game versions; launch and first run follow it`.

### Task 5: Settings, Home and addon browser UI

**Files:**
- Modify: `wow/ui/AccountSettingsScreens.kt` (Game version `FwSection` + confirm dialog), `wow/ui/HomeScreen.kt`, `wow/ui/addons/AddonsScreen.kt`, `wow/ui/components/Art.kt` (`GameIcon.RETAIL`, `GameVersion.icon`)

**Interfaces:**
- Consumes: `LauncherController.switchVersion`, `GameVersion.isExperimental`, `GameVersion.addonTarget()`.
- Copy (exact): dialog title "Other WoW versions are experimental"; body "Foreverywhere is made for WoW Forever. Other versions may run poorly or not start at all, and they get little support. Your WoW Forever install and addons stay as they are, and you can switch back any time."; buttons "Switch to <name>" / "Cancel"; while installing: "Finish or cancel the current download before switching."; Home tag "Experimental".
- Forever mode: every existing string and the "Forever + Classic"/"Forever only" chip stay byte-identical. Other versions: no chip; labels "Made for <name>" / "No game version listed. It may not work in <name>."; search hint "Search <name> addons…".

- [ ] **Step 1:** Implement; compile with `assembleModernDebug`.
- [ ] **Step 2:** Emulator check: default Home/Settings/Addons look unchanged except the new Settings section; switching shows the dialog, Home shows the name/icon/tag, switching back restores Forever. Screenshots to scratchpad.
- [ ] **Step 3: Commit** `feat: game version setting, experimental warning and per-version labels`.

### Task 6: Docs and final verification

- [ ] Add a short "Other WoW versions (experimental)" section to `README.md` (after Settings and game files).
- [ ] Full `testModernDebugUnitTest` + `assembleModernRelease`; copy APK to `/mnt/fileserver/WoW-Foreverywhere-game-versions-test.apk`.
- [ ] Whole-branch review by a fresh reviewer; fix findings; commit and push `foreverywhere` and `main`. No release tag.
