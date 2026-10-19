# WoW Foreverywhere Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the jaredgei/wow-forever-android fork into an app that installs WoW Forever without a PC, manages addons from CurseForge, Wago, GitHub and WoWInterface, and supports both the beta and the launch product.

**Architecture:**
- New code lives in new packages under `app.gamenative.wow.*`:
  - `product`: product model and install discovery
  - `tact`: CDN/CASC primitives
  - `install`: installer engine and service
  - `addons`: TOC parsing, providers, manager
  - `ui`: new screens
- These packages are plain Kotlin wherever possible, so they can be unit-tested on the JVM.
- Upstream files (`WoWForeverScreen.kt`, `WowClientDownloader.kt`, `BattleNetSignIn.kt`) change only through thin shims that call the new code, so `upstream/main` keeps merging cleanly.

**Tech Stack:** Kotlin 2 / Jetpack Compose / Hilt (existing), OkHttp (existing), kotlinx.serialization (existing), JUnit4 + MockWebServer + Robolectric (existing test deps). No new runtime dependencies unless a task says so.

**Spec:** `docs/superpowers/specs/2026-10-18-wow-foreverywhere-design.md`

## Global Constraints

- License stays GPL-3.0; keep `THIRD_PARTY_NOTICES`. Ship no Blizzard files.
- arm64-v8a, flavor `modern`, minSdk 29. Build with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew assembleModernDebug`. Run tests with `./gradlew testModernDebugUnitTest`.
- Products (from the live patch service on 2026-10-18):
  - `wowf`: subfolder `_forever_`, ARM64 exe `Wow-arm64.exe`
  - `wow_classic_beta`: subfolder `_classic_beta_`, ARM64 exe `WowB-ARM64.exe`
  - Both share CDNConfig `f065eade5276a5ac05606e60a4c5a1f7`, so the archives are shared.
- Patch service: `http://us.patch.battle.net:1119/<product>/{versions,cdns}`. CDN path `tpr/wow`. Hosts `level3.blizzard.com`, `us.cdn.blizzard.com`.
- Addon flavor: Wago id `forever`, patch `1.60.1`, TOC suffixes `-Camelot`, `_Camelot`, `-Forever`, `_Forever`. Classic Era TOC suffixes: `-Classic`, `_Classic`, `-Vanilla`, `_Vanilla`.
- API keys come from `local.properties` (`CURSEFORGE_API_KEY`, `WAGO_API_KEY`) via `BuildConfig`. They must never be committed. A blank key disables that provider.
- **UI rule (spec §5.2a):**
  - No gradients, no blue/purple, no rainbow accents, no left-accent-bar cards, no emoji icons.
  - The visual direction must be approved by the user before UI styling is finalized.
  - Screens may be built structurally against theme tokens first.
- Nothing updates without consent, except the existing "outdated game blocks Play" prompt.
- `login.txt` handling stays exactly as upstream: write at launch; wipe after `Logs/Hotfix.log` passes 50 KB or 180 s; wipe on resume, exit and force-close.

## Review Focus

1. **Interrupted or partial downloads.** Expected: the app is killed or the network drops mid-file, and on restart the install resumes and nothing corrupt is ever treated as complete. Test: `GameInstallerTest.resumesAfterInterruptedFile` (Task 8).
2. **Storage disappears or fills up.** Expected: the SD card is ejected or the disk fills mid-install, and the app shows a clear error and can resume later. It never deletes the user's existing files. Test: `GameInstallerTest.failsCleanlyWhenTargetUnwritable` (Task 8).
3. **Malicious or odd addon zips.** Expected: entries like `../` (zip-slip), absolute paths, and zips with a single top-level wrapper folder are handled, and nothing is written outside `Interface/AddOns`. Test: `AddonArchiveTest.rejectsZipSlip`, `AddonArchiveTest.unwrapsSingleRootFolder` (Task 14).
4. **Two addons claim the same folder** (for example both ship `Libs` or `!BugGrabber`). Expected: removing one addon doesn't delete a folder the other still owns. Test: `AddonManagerTest.removeKeepsSharedFolders` (Task 15).
5. **Offline at launch.** Expected: a failed version check or addon check never blocks Play (upstream behavior is preserved). Test: `ProductRegistryTest` stays network-free; `AddonManagerTest.checkUpdatesOfflineReturnsEmpty` (Task 15).

---

## Phase A — Foundation (needed before 4 Nov)

### Task 1: Fork identity

**Files:**
- Create: `app/src/main/java/app/gamenative/wow/AppIdentity.kt`
- Modify: `app/build.gradle.kts` (applicationId, versionName suffix, BuildConfig API key fields)
- Modify: `app/src/main/java/app/gamenative/utils/AppUpdater.kt:17`
- Modify: every `/data/data/app.wowforever` literal: `Container.java:53-60`, `WineUtils.java:48,60`, `WoWForeverScreen.kt:977`
- Modify: `app/src/main/res/values/strings.xml` (app name)
- Modify: `README.md` (fork intro section at the top)
- Test: `app/src/test/java/app/gamenative/utils/AppUpdaterTest.kt` (update the URLs)

**Interfaces:**
- Produces:
  - `object AppIdentity { const val RELEASES_URL: String; fun dataDir(packageName: String): String }`
  - `BuildConfig.CURSEFORGE_API_KEY: String`
  - `BuildConfig.WAGO_API_KEY: String`

- [ ] Step 1: Set the identity values:
  - applicationId `app.wowforeverywhere`
  - app name "WoW Foreverywhere"
  - `RELEASES_URL = "https://api.github.com/repos/YoruDev-Ryland/WoWForeverywhere/releases/latest"`
- [ ] Step 2: Replace each hard-coded `/data/data/app.wowforever` with a value derived from the runtime package name. In Java, `context.getPackageName()` is available in every listed spot; the static `DEFAULT_DRIVES` becomes a method that takes a `Context`.
- [ ] Step 3: Read `CURSEFORGE_API_KEY` and `WAGO_API_KEY` from `local.properties`, defaulting to `""`, into `buildConfigField`s.
- [ ] Step 4: Run `./gradlew testModernDebugUnitTest assembleModernDebug`. Expected: BUILD SUCCESSFUL, tests pass.
- [ ] Step 5: Commit `chore: rebrand fork as WoW Foreverywhere with its own app id and update channel`.

### Task 2: Product model and detection

**Files:**
- Create: `app/src/main/java/app/gamenative/wow/product/WowProduct.kt`
- Create: `app/src/main/java/app/gamenative/wow/product/BuildInfo.kt`
- Test: `app/src/test/java/app/gamenative/wow/product/BuildInfoTest.kt`
- Test: `app/src/test/java/app/gamenative/wow/product/WowProductTest.kt`

**Interfaces:**
- Produces:
  - ```
    enum class WowProduct(
        val code: String,
        val flavorDir: String,
        val armExe: String,
        val x64Exe: String,
        val portal: String,
        val displayName: String,
    )
    ```
    Values:
    - `FOREVER("wowf", "_forever_", "Wow-arm64.exe", "Wow.exe", "us", "WoW Forever")`
    - `BETA("wow_classic_beta", "_classic_beta_", "WowB-ARM64.exe", "WowB.exe", "test", "WoW Forever Beta")`
  - `companion fun fromCode(code: String?): WowProduct?`
  - ```
    data class BuildInfo(val header: List<String>, val rows: List<Map<String, String>>) {
        fun active(product: WowProduct? = null): Map<String, String>?
        fun withUpdatedActive(product: WowProduct, values: Map<String, String>): BuildInfo
        fun withProduct(from: WowProduct, to: WowProduct): BuildInfo
        fun serialize(): String
        companion object { fun parse(text: String): BuildInfo; fun read(root: File): BuildInfo? }
    }
    ```
  - The header keeps the `!TYPE:n` suffixes so that serializing reproduces the original header line exactly.
  - `fun detectProduct(root: File): WowProduct?`: the active row's Product.

- [ ] Step 1: Write the tests with a real `.build.info` fixture (header `Branch!STRING:0|Active!DEC:1|Build Key!HEX:16|CDN Key!HEX:16|Install Key!HEX:16|IM Size!DEC:4|CDN Path!STRING:0|CDN Hosts!STRING:0|CDN Servers!STRING:0|Tags!STRING:0|Armadillo!STRING:0|Last Activated!STRING:0|Version!STRING:0|KeyRing!HEX:16|Product!STRING:0`, one `us|1|…|wow_classic_beta` row):
  - `parse(serialize(x)) == x`
  - `active(BETA)["Version"] == "1.60.1.70450"`
  - `withProduct(BETA, FOREVER).active(FOREVER)!!["Product"] == "wowf"`
  - `withUpdatedActive` changes only the named columns
  - `fromCode("nope") == null`
  - `detectProduct(dirWithoutBuildInfo) == null`
- [ ] Step 2: Run `./gradlew testModernDebugUnitTest --tests '*wow.product*'`. Expected: FAIL (unresolved).
- [ ] Step 3: Implement.
- [ ] Step 4: Run the same command. Expected: PASS.
- [ ] Step 5: Commit `feat: product model and .build.info parser for beta and launch products`.

### Task 3: Make upstream product-aware (thin shim)

**Files:**
- Modify: `app/src/main/java/app/gamenative/ui/screen/wow/WowClientDownloader.kt`
  - Remove the `TARGET_PRODUCT`/`FLAVOR_DIR`/`EXE_NAME` constants.
  - Functions take `product: WowProduct`.
  - `PATCH_URL` becomes `patchUrl(product)`.
  - `activeBuild`/`updateBuildInfo` use `BuildInfo`.
- Modify: `app/src/main/java/app/gamenative/ui/screen/wow/WoWForeverScreen.kt`
  - `GamePath.Status` gains `product: WowProduct?`; `isWrongGame` means "not a known product".
  - `ensureGameConfig`, `containerConfig` and `prepareLaunch` take a `WowProduct`. CONFIG_DEFAULTS `portal` = `product.portal` and `agentUID` = `product.code`.
- Modify: `app/src/main/java/app/gamenative/ui/screen/wow/BattleNetSignIn.kt`: `writeLoginFile`/`removeLoginFile` remove the login file from every known flavor dir that exists.
- Test: `app/src/test/java/app/gamenative/ui/screen/wow/GameConfigTest.kt`

**Interfaces:**
- Consumes: Task 2.
- Produces:
  - `WowClientDownloader.checkVersion(gameRoot: File, product: WowProduct): VersionCheckResult?`
  - `updateGame(gameRoot, product, target, onStatus)`
  - `download(gameRoot, product, onStatus)`
  - `internal fun buildConfigWtf(existing: List<String>?, product: WowProduct, screenSize: String): List<String>?` (returns null when nothing changes)

- [ ] Step 1: Move the pure part of `ensureGameConfig` into `buildConfigWtf`. Write `GameConfigTest`:
  - A new FOREVER config contains `SET portal "us"` and `SET agentUID "wowf"`; BETA has `"test"`.
  - Existing configs keep user lines and always force `SET gxApi "D3D11"`.
  - An unchanged config returns null.
- [ ] Step 2: Run the tests. Expected: FAIL.
- [ ] Step 3: Implement the refactor. The `isClientBinary()` filter keeps `Windows`+`arm64`. `md5`/`blte` helpers stay where they are; they move in Task 7.
- [ ] Step 4: Run `./gradlew testModernDebugUnitTest assembleModernDebug`. Expected: PASS.
- [ ] Step 5: Commit `feat: detect and launch either WoW Forever product`.

### Task 4: Install discovery

**Files:**
- Create: `app/src/main/java/app/gamenative/wow/product/InstallScanner.kt`
- Create: `app/src/main/java/app/gamenative/wow/product/InstallRegistry.kt` (list of installs stored as JSON in SharedPreferences `wow_installs`; migrates upstream's `wow_forever/game_path`)
- Test: `app/src/test/java/app/gamenative/wow/product/InstallScannerTest.kt`

**Interfaces:**
- Produces:
  - `data class GameInstall(val root: String, val product: WowProduct, val version: String)`
  - `object InstallScanner { fun scan(storageRoots: List<File>, maxDepth: Int = 3): List<GameInstall> }`: finds every directory with a `.build.info` whose product is known and that has a non-empty `Data/`. Skips `Android/`, hidden dirs and symlink loops.
  - `fun storageRoots(context: Context): List<File>`: internal storage plus every removable volume, from `StorageManager.storageVolumes` → `directory`.
  - `class InstallRegistry(context) { fun all(): List<GameInstall>; fun active(): GameInstall?; fun setActive(root: String); fun add(i: GameInstall); fun remove(root: String) }`

- [ ] Step 1: Write the tests on a temp tree:
  - finds `sd/WoW Forever` at depth 1 and `sd/Games/WoW` at depth 2
  - ignores a `.build.info` with an unknown product
  - ignores roots with an empty `Data/`
  - doesn't recurse into `Android/`
  - returns both products when both are present
- [ ] Step 2: Run them. Expected: FAIL. Step 3: Implement. Step 4: Run. Expected: PASS.
- [ ] Step 5: Commit `feat: scan internal and SD storage for existing WoW Forever installs`.

### Task 5: Beta → launch conversion

**Files:**
- Create: `app/src/main/java/app/gamenative/wow/product/ProductConverter.kt`
- Test: `app/src/test/java/app/gamenative/wow/product/ProductConverterTest.kt`

**Interfaces:**
- Consumes: Task 2, Task 3 (`WowClientDownloader.download`).
- Produces: `object ProductConverter { fun planConversion(root: File, from: WowProduct, to: WowProduct): ConversionPlan; fun applyLocal(root: File, plan: ConversionPlan) }` with `data class ConversionPlan(val newBuildInfo: BuildInfo, val copyDirs: List<Pair<String,String>>)`.
- `applyLocal` does three things:
  1. writes `.build.info` atomically (temp file + rename) with the `to` product row added and made Active, keeping the `from` row inactive;
  2. creates `<to.flavorDir>/.flavor.info`;
  3. copies `WTF/` and `Interface/AddOns/` from the old flavor dir if the new one doesn't have them.
- The caller then runs `WowClientDownloader.download(root, to, …)` to fetch the new exe. `Data/` is reused as-is.

- [ ] Step 1: Write the tests:
  - after `applyLocal`, `detectProduct(root) == FOREVER`;
  - the old row is still present with `Active=0`;
  - AddOns are copied without overwriting existing ones;
  - a second `applyLocal` doesn't change anything (idempotent).
- [ ] Steps 2–4: Watch the tests fail, implement, then watch them pass.
- [ ] Step 5: Commit `feat: convert a beta install to the launch product reusing Data/`.

## Phase B — Installer

### Task 6: Installer feasibility spike (throwaway, on PC)

**Files:**
- Create: `tools/spike/install_minimal.py` (throwaway; reuses the parsing in `tools/download_wow_arm64.py`)
- Create: `docs/superpowers/notes/2026-10-18-installer-spike.md` (findings)

- [ ] Step 1: Build an install from the CDN alone into a scratch dir:
  - `.build.info` (written from scratch)
  - `Data/config/` (build and CDN config)
  - `Data/indices/` (all archive indices)
  - the Windows **x86_64** install-manifest files, which include `Wow.exe` for `wowf`
  - `_forever_/.flavor.info`
- [ ] Step 2: Run `Wow.exe` under Wine on the dev PC (`wine 11.20` with DXVK, or `umu-run`, headless via `gamescope --headless` if possible). Check `_forever_/Logs/` and `Errors/` for "CAS system was unable to initialize". Watch whether `Data/data/` starts filling, which would mean the client streams or builds local storage itself.
- [ ] Step 3: Record in the notes which outcome holds:
  - **(a)** The minimal install boots and the client fills local storage itself. The installer only needs configs, indices, binaries and `.build.info`.
  - **(b)** The client requires pre-built local CASC storage (`Data/data/data.###` + `.idx`). The installer must write local storage, and Task 8 grows a `LocalStorageWriter`.
- [ ] Step 4: Commit the notes only (`docs: installer spike findings`). The spike script may be committed under `tools/spike/` and marked throwaway.

### Task 7: Extract TACT primitives

**Files:**
- Create: `app/src/main/java/app/gamenative/wow/tact/{Psv.kt, TactConfig.kt, Blte.kt, InstallManifest.kt, EncodingLookup.kt, ArchiveIndex.kt, Cdn.kt, Hex.kt}`
- Modify: `WowClientDownloader.kt`: delegate to `tact.*`. Behavior stays the same.
- Test: `app/src/test/java/app/gamenative/wow/tact/{BlteTest.kt, InstallManifestTest.kt, ArchiveIndexTest.kt, CdnTest.kt}`

**Interfaces:**
- Produces:
  - `object Blte { fun decode(data: ByteArray): ByteArray; fun decode(data: ByteArray, out: OutputStream); fun encodeForTest(chunks: List<ByteArray>, mode: Char): ByteArray }` (the test helper lives in the test source set)
  - `data class InstallEntry(val name: String, val ckey: String, val size: Long, val tags: Set<String>)`
  - `object InstallManifest { fun parse(data: ByteArray): List<InstallEntry> }`
  - `class Cdn(hosts: List<String>, path: String, http: OkHttpClient = Net.http) { fun fetch(kind, key, range: LongRange? = null): ByteArray?; fun require(kind, key): ByteArray; fun stream(kind, key, sink: OutputStream, range: LongRange? = null): Boolean }`
  - `class EncodingLookup(cdn: Cdn, ekey: String) { fun find(ckeys: Set<String>): Map<String,String> }`
  - `class ArchiveIndex(bytes: ByteArray) { fun locate(ekey: String): Pair<Long,Long>? }` (offset, size)
  - `object Psv`
  - `object TactConfig { fun parse(text: String): Map<String, List<String>> }`
- Note: `InstallEntry.size` is newly parsed (upstream skipped those 4 bytes). The installer needs it for progress and free-space checks.

- [ ] Step 1: Write the tests:
  - BLTE: N, Z, single-chunk headerless, and multi-chunk round-trips; an unknown mode throws `IOException`.
  - Install manifest: a synthesized 2-tag/3-entry manifest parses names, sizes and tag sets.
  - `ArchiveIndex.locate` finds an entry in a synthesized 4096-byte block.
  - `Cdn`, using MockWebServer:
    - falls back to the second host on a 500
    - returns null on 404
    - sends the `Range` header
- [ ] Steps 2–4: Watch the tests fail, implement by moving code out of `WowClientDownloader`, then watch them pass. The existing app behavior must be unchanged (`assembleModernDebug` passes).
- [ ] Step 5: Commit `refactor: extract TACT/CASC primitives into wow.tact`.

### Task 8: Game installer engine

**Files:**
- Create: `app/src/main/java/app/gamenative/wow/install/{InstallPlan.kt, GameInstaller.kt, InstallState.kt}`
- Test: `app/src/test/java/app/gamenative/wow/install/GameInstallerTest.kt`

**Interfaces:**
- Consumes: Tasks 2 and 7.
- Produces:
  - ```
    data class InstallRequest(
        val root: File,
        val product: WowProduct,
        val locale: String,
        val arch: String = "arm64",
    )
    ```
  - `data class InstallProgress(val phase: Phase, val doneBytes: Long, val totalBytes: Long, val currentFile: String?)` with `enum Phase { RESOLVING, DOWNLOADING, VERIFYING, FINALIZING, DONE }`
  - ```
    class GameInstaller(
        private val patchBase: String = "http://us.patch.battle.net:1119",
        private val http: OkHttpClient = Net.http,
    ) {
        suspend fun plan(req: InstallRequest): InstallPlan
        suspend fun run(req: InstallRequest, onProgress: (InstallProgress) -> Unit)
    }
    ```
  - `InstallPlan(val version: String, val buildConfig: String, val cdnConfig: String, val items: List<PlanItem>, val totalBytes: Long)`
- Behavior (shape depends on the Task 6 outcome):
  - `plan()`:
    - resolves versions and cdns for the product
    - selects install-manifest entries tagged `Windows`, `arch` and the locale (for example `enUS`)
    - adds config and index files
    - for outcome (b), also adds the local storage build step
  - `run()`:
    - writes `<root>/.foreverywhere-install.json` (the list of completed item keys) after each item
    - downloads each item to `name.download`, verifies its MD5 against the ckey, then renames it into place
    - skips items whose file already verifies
    - writes `.build.info` and `.flavor.info` last, atomically, so an interrupted install is never detected as a complete one by `GamePath.status()`
    - deletes the state file at the end
  - Free space: `plan()` throws `InsufficientSpaceException(needed, available)` when `root.usableSpace < totalBytes * 1.15`.

- [ ] Step 1: Write the tests against MockWebServer serving a fake product:
  - `versions`/`cdns`, plus build config, cdn config, an install manifest with 3 files (2 tagged Windows+arm64+enUS, 1 tagged x86_64), an encoding file, and the 2 BLTE blobs.
  - Tests:
    - `installsOnlyMatchingTags`
    - `writesBuildInfoLast` (interrupt via a dispatcher that fails the 2nd blob; assert no `.build.info`)
    - `resumesAfterInterruptedFile` (rerun after the failure; 1st blob not re-requested; final state complete)
    - `rejectsChecksumMismatch`
    - `failsCleanlyWhenTargetUnwritable` (read-only root → `IOException`, no partial `.build.info`, pre-existing files untouched)
    - `insufficientSpaceThrows` (inject a `usableSpace` provider)
- [ ] Steps 2–4: Watch the tests fail, implement, then watch them pass.
- [ ] Step 5: Commit `feat: resumable verified game installer`.

### Task 9: Install service

**Files:**
- Create: `app/src/main/java/app/gamenative/wow/install/InstallService.kt` (foreground service, type `dataSync`, notification with progress and Pause/Cancel actions)
- Create: `app/src/main/java/app/gamenative/wow/install/InstallController.kt` (exposes `StateFlow<InstallUiState>`; start/pause/resume)
- Modify: `app/src/main/AndroidManifest.xml` (service, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`)

**Interfaces:**
- Consumes: Task 8.
- Produces: `object InstallController { val state: StateFlow<InstallUiState>; fun start(context, req: InstallRequest); fun pause(context); fun resume(context) }` with ```sealed interface InstallUiState { Idle; Running(progress: InstallProgress); Paused(progress); Failed(message: String); Done(install: GameInstall) }```.
- On Done: `InstallRegistry.add` + `setActive`.
- Pause cancels the coroutine. Resume calls `run` again, which skips verified items.

- [ ] Step 1: Implement. Behavior is covered by Task 8's engine tests; the service is glue.
- [ ] Step 2: Run `./gradlew assembleModernDebug`. Expected: success.
- [ ] Step 3: Commit `feat: foreground install service with pause and resume`.

## Phase C — Addons

### Task 10: TOC parsing and flavor classification

**Files:**
- Create: `app/src/main/java/app/gamenative/wow/addons/{Toc.kt, AddonFlavor.kt}`
- Test: `app/src/test/java/app/gamenative/wow/addons/TocTest.kt`

**Interfaces:**
- Produces:
  - ```
    data class Toc(
        val folder: String,
        val fileName: String,
        val title: String?,
        val version: String?,
        val interfaces: List<Int>,
        val dependencies: List<String>,
        val curseId: String?,
        val wagoId: String?,
        val wowiId: String?,
        val raw: Map<String, String>,
    )
    ```
  - `object TocParser { fun parse(folder: String, fileName: String, text: String): Toc }`
    - strips `|c…|r` color codes from Title
    - `## Interface: 11507, 16001` → list
    - `## Dependencies`/`RequiredDeps`
    - `X-Curse-Project-ID`, `X-Wago-ID`, `X-WoWI-ID`
  - `enum class AddonFlavor { FOREVER, CLASSIC, OTHER }`
  - `object FlavorRules { fun ofTocFileName(name: String): AddonFlavor?; fun pickToc(folder: File): Toc?; fun classify(tocs: List<Toc>): AddonFlavor }`
    - Name suffixes: `-Forever`, `_Forever`, `-Camelot` and `_Camelot` → FOREVER; `-Classic`, `_Classic`, `-Vanilla` and `_Vanilla` → CLASSIC; anything else (or no suffix) → null.
    - `pickToc` prefers a Forever TOC, then a plain `<Folder>.toc`, then Classic.
    - When there is no suffix, `classify` uses the interface number: values starting with `1` (11xxx) → CLASSIC; the Forever interface constant `FOREVER_INTERFACE` (defined once in `AddonFlavor.kt`, initial guess `16001`, see spec §5.7) → FOREVER.

- [ ] Step 1: Write the tests:
  - title color stripping
  - multi-interface parsing
  - BOM tolerance
  - `pickToc` prefers `Foo_Forever.toc` over `Foo.toc` over `Foo_Vanilla.toc`
  - `classify` of a `## Interface: 11507` plain TOC is CLASSIC
- [ ] Steps 2–4: Watch the tests fail, implement, then watch them pass. Step 5: Commit `feat: addon TOC parser and Forever/Classic classification`.

### Task 11: Installed addon scanner and database

**Files:**
- Create: `app/src/main/java/app/gamenative/wow/addons/{InstalledAddon.kt, AddonDb.kt, AddonScanner.kt}`
- Test: `app/src/test/java/app/gamenative/wow/addons/AddonScannerTest.kt`

**Interfaces:**
- Consumes: Task 10.
- Produces:
  - ```
    @Serializable data class InstalledAddon(
        val id: String,
        val name: String,
        val folders: List<String>,
        val version: String?,
        val flavor: AddonFlavor,
        val source: SourceRef?,
        val installedAt: Long,
    )
    ```
  - `@Serializable data class SourceRef(val provider: String, val projectId: String, val fileId: String? = null)`
  - `class AddonDb(file: File) { fun load(): List<InstalledAddon>; fun save(list: List<InstalledAddon>) }`. The file is `<root>/<flavorDir>/Interface/AddOns/../.foreverywhere-addons.json`, so the data travels with the install.
  - `object AddonScanner { fun scan(addonsDir: File, known: List<InstalledAddon>): List<InstalledAddon> }`. It groups unknown folders into logical addons using:
    - `Dependencies` pointing at a sibling with the same title prefix (for example `DBM-Core` + `DBM-*`)
    - a shared `X-Curse-Project-ID`/`X-Wago-ID`
    It sets `source` from TOC ids when present. Folders already in `known` keep their record.

- [ ] Step 1: Write the tests:
  - a tree with `Questie` (Curse id), `DBM-Core` + `DBM-StatusBarTimers` (same curse id), and a loose `MyAddon` (no ids) gives 3 addons with correct sources and folders
  - known records are preserved
  - deleted folders drop out
- [ ] Steps 2–4: Watch the tests fail, implement, then watch them pass. Step 5: Commit `feat: detect installed addons and persist the addon database`.

### Task 12: Provider interface and GitHub provider

**Files:**
- Create: `app/src/main/java/app/gamenative/wow/addons/provider/{AddonProvider.kt, GitHubProvider.kt}`
- Test: `app/src/test/java/app/gamenative/wow/addons/provider/GitHubProviderTest.kt`

**Interfaces:**
- Produces:
  - ```
    data class RemoteAddon(
        val provider: String,
        val projectId: String,
        val name: String,
        val summary: String,
        val author: String,
        val iconUrl: String?,
        val downloads: Long,
        val latest: Map<AddonFlavor, RemoteFile>,
        val websiteUrl: String?,
    )
    ```
  - `data class RemoteFile(val fileId: String, val version: String, val downloadUrl: String, val dependencies: List<SourceRef>)`
  - ```
    interface AddonProvider {
        val id: String
        val enabled: Boolean
        suspend fun search(query: String, page: Int): List<RemoteAddon>
        suspend fun popular(page: Int): List<RemoteAddon>
        suspend fun get(projectId: String): RemoteAddon?
        suspend fun getMany(ids: List<String>): List<RemoteAddon>
    }
    ```
  - `class GitHubProvider(http, apiBase = "https://api.github.com")`:
    - `projectId` is `owner/repo`
    - `fromUrl(url): String?` parses `github.com/owner/repo[...]`
    - `get` reads `/repos/{id}/releases/latest`
    - asset selection: when a `release.json` asset exists (the BigWigs packager format), use its `flavor` metadata (`classic`/`vanilla` → CLASSIC; `forever`/`camelot` → FOREVER); otherwise match zip names containing `-forever`/`-camelot` → FOREVER and `-classic`/`-vanilla` → CLASSIC; a single generic zip → OTHER
    - `search`/`popular` return empty: GitHub works by URL only

- [ ] Step 1: Write the MockWebServer tests:
  - `release.json` flavor mapping
  - filename fallback mapping
  - `fromUrl` variants (`.git`, `/releases`, trailing slash)
  - 404 → null
- [ ] Steps 2–4: Watch the tests fail, implement, then watch them pass. Step 5: Commit `feat: addon provider interface and GitHub releases provider`.

### Task 13: CurseForge, Wago and WoWInterface providers

**Files:**
- Create: `app/src/main/java/app/gamenative/wow/addons/provider/{CurseForgeProvider.kt, WagoProvider.kt, WowInterfaceProvider.kt}`
- Test: `app/src/test/java/app/gamenative/wow/addons/provider/{CurseForgeProviderTest.kt, WagoProviderTest.kt, WowInterfaceProviderTest.kt}` (recorded JSON fixtures in `app/src/test/resources/addons/`)

**Interfaces:**
- Consumes: Task 12.
- **CurseForge** (`https://api.curseforge.com`, header `x-api-key`, game id `1`):
  - `enabled = key.isNotBlank()`
  - search: `/v1/mods/search?gameId=1&searchFilter=&sortField=2&sortOrder=desc&index=&pageSize=50`
  - files: `latestFilesIndexes[]`, mapped by `gameVersionTypeId`. The Classic Era type id is `67408`. The Forever type id is discovered at runtime from `/v1/games/1/version-types` by name containing "Forever" (cached), or hard-coded once known.
  - dependencies: `relationType == 3` (required)
  - an empty `downloadUrl` means distribution is disabled. Such a file is listed with "Open on CurseForge" and is not installable.
- **Wago** (`https://addons.wago.io/api/external`, bearer key):
  - `enabled = key.isNotBlank()`
  - flavors: `forever` → FOREVER, `classic` → CLASSIC
  - endpoints follow WowUp's `WagoAddonProvider` (search `/addons/_search?query=&game_version=`, details `/addons/{id}?game_version=`)
- **WoWInterface** (`https://api.mmoui.com/v3/game/WOW`):
  - `filelist.json` (cached 1 h) for search/popular
  - `filedetails/{id}.json` for downloads
  - flavor from `UICompatibility[].name` ("Classic" → CLASSIC, "Forever" → FOREVER)
  - always enabled
- Fixture rule: build fixtures from real responses where reachable without a key (WoWInterface, Wago `/api/data/game`), or from documented schemas (CurseForge, Wago external), and name them `*.schema.json` when synthesized.

- [ ] Step 1: Write the tests for each provider:
  - search maps name/author/downloads
  - the flavor mapping picks the FOREVER file over CLASSIC
  - the disabled-distribution case (CF)
  - `enabled == false` with a blank key, and no HTTP call is made
- [ ] Steps 2–4: Watch the tests fail, implement, then watch them pass. Step 5: Commit `feat: CurseForge, Wago and WoWInterface addon providers`.

### Task 14: Addon archive installer

**Files:**
- Create: `app/src/main/java/app/gamenative/wow/addons/AddonArchive.kt`
- Test: `app/src/test/java/app/gamenative/wow/addons/AddonArchiveTest.kt`

**Interfaces:**
- Produces: `object AddonArchive { fun install(zip: File, addonsDir: File): List<String> }`:
  - returns the top-level addon folder names written
  - extracts to a sibling temp dir first, then swaps folders in atomically (old folder → `.old`, new folder in, delete `.old`)
  - unwraps a single wrapper folder that contains no `.toc` but whose children do
  - throws `IOException("unsafe path")` on entries that are absolute or contain `..`

- [ ] Step 1: Write the tests:
  - `rejectsZipSlip`
  - `unwrapsSingleRootFolder`
  - `multiFolderZipReturnsAllFolders`
  - `replacesExistingFolderAtomically` (a failure mid-extract leaves the old folder intact)
- [ ] Steps 2–4: Watch the tests fail, implement, then watch them pass. Step 5: Commit `feat: safe addon zip installer`.

### Task 15: Addon manager

**Files:**
- Create: `app/src/main/java/app/gamenative/wow/addons/AddonManager.kt`
- Test: `app/src/test/java/app/gamenative/wow/addons/AddonManagerTest.kt` (fake providers, temp dirs, MockWebServer serving the zips)

**Interfaces:**
- Consumes: Tasks 10–14.
- Produces:
  - `data class AddonListing(val remote: RemoteAddon, val bestFlavor: AddonFlavor, val installed: InstalledAddon?, val updateAvailable: Boolean, val sources: List<String>)`
  - ```
    class AddonManager(
        addonsDir: File,
        db: AddonDb,
        providers: List<AddonProvider>,
        http: OkHttpClient,
    ) {
        suspend fun search(query: String, includeClassic: Boolean = true): List<AddonListing>
        suspend fun popular(includeClassic: Boolean = true): List<AddonListing>
        suspend fun installed(): List<AddonListing>
        suspend fun install(remote: RemoteAddon): InstalledAddon
        suspend fun update(addon: InstalledAddon): InstalledAddon
        suspend fun updateAll(): List<InstalledAddon>
        suspend fun remove(addon: InstalledAddon)
        suspend fun checkUpdates(): List<AddonListing>
        suspend fun installFromUrl(url: String): InstalledAddon
    }
    ```
- Behavior:
  - search merges results across providers by normalized name and author; prefers the source with a Forever file, then the most downloads; and lists all sources
  - `bestFlavor` is FOREVER if any merged source has a Forever file, else CLASSIC
  - `includeClassic=false` filters CLASSIC out
  - `updateAvailable` is true when the remote best file's id differs from `installed.source.fileId`, or the installed copy is CLASSIC and a FOREVER file now exists (the "switch to Forever" case)
  - `install` resolves required dependencies recursively (cycle-safe) and skips ones already installed
  - `remove` deletes only folders that no other installed addon lists
  - `checkUpdates` swallows network errors per provider and returns what it could

- [ ] Step 1: Write the tests:
  - `searchMergesDuplicatesAcrossProviders`
  - `classicOnlyAddonIsMarkedClassic`
  - `includeClassicFalseHidesClassic`
  - `installResolvesDependencies`
  - `updateDetectsClassicToForeverSwitch`
  - `removeKeepsSharedFolders`
  - `checkUpdatesOfflineReturnsEmpty`
  - `installFromGitHubUrl`
- [ ] Steps 2–4: Watch the tests fail, implement, then watch them pass. Step 5: Commit `feat: addon manager with merge, dependencies, updates and safe removal`.

## Phase D — UI

### Task 16: Visual direction proposal (user approval gate)

**Files:**
- Create: `docs/superpowers/notes/2026-10-18-visual-direction.md`, plus an HTML mockup of the home and addons screens in that direction, published for the user to review.
- [ ] Step 1: Propose one deliberate direction that obeys spec §5.2a: palette with one accent, type scale, surface treatment, focus states for controller navigation, and the Forever/Classic marks. Check it against the banned list.
- [ ] Step 2: Encode it as tokens in `app/src/main/java/app/gamenative/wow/ui/theme/ForeverywhereTheme.kt` (colors, type, shapes, a `Modifier.focusRing()`). No gradient brushes. Leave upstream's `WowBackgroundGradient` unused in new screens.
- [ ] Step 3: Commit `feat: visual direction tokens (pending user approval)`.

### Task 17: Navigation shell and home dashboard

**Files:**
- Create: `app/src/main/java/app/gamenative/wow/ui/{ForeverywhereApp.kt, HomeScreen.kt, LauncherController.kt}`
- Modify: the place that hosts `WoWForeverScreen` (find it with `grep -rn "WoWForeverScreen(" app/src/main`) so it hosts `ForeverywhereApp`.
- `LauncherController` holds the launch, update and version-check logic currently inline in `WoWForeverScreen` (lines 82–343). It is moved, not rewritten, and keeps the auto-launch, outdated-blocks-Play and app-update behavior.

**Interfaces:**
- Consumes: Tasks 3, 4, 9, 15, 16.
- Produces:
  - routes `home`, `addons`, `game`, `account`, `settings`, `firstrun`
  - Home tiles:
    - Play: large; shows version and status; launches
    - Addons: badge = `checkUpdates().size`
    - Game files: install location and size; opens `game`
    - Account
    - Settings
  - All tiles are D-pad focusable, with a visible focus ring.

- [ ] Step 1: Implement. Keep `WoWForeverScreen.kt` compiling for upstream merges; it simply isn't the entry point any more.
- [ ] Step 2: Run `./gradlew assembleModernDebug testModernDebugUnitTest`. Expected: success.
- [ ] Step 3: Commit `feat: dashboard home screen and navigation shell`.

### Task 18: First-run flow

**Files:**
- Create: `app/src/main/java/app/gamenative/wow/ui/firstrun/{FirstRunScreen.kt, DeviceCheck.kt}`

**Interfaces:**
- Consumes: Tasks 4, 8, 9, 17.
- Produces: steps Welcome → Where's the game → Install → Sign in → Home, as approved in spec §5.4.
  - The device check reuses `GPUInformation` and `Build.SOC_MODEL`.
  - "Where's the game" runs `InstallScanner.scan(storageRoots(ctx))`.
  - Install offers each storage root with its `usableSpace`, a locale picker (device locale if supported, else enUS) and the product (FOREVER once `wowf` versions respond, else BETA).
  - Sign in reuses `BattleNetCredentialDialog` content.
  - Shown when `InstallRegistry.active() == null`.
- [ ] Step 1: Implement. Step 2: Build. Step 3: Commit `feat: first-run setup flow`.

### Task 19: Addons screen

**Files:**
- Create: `app/src/main/java/app/gamenative/wow/ui/addons/{AddonsScreen.kt, AddonsViewModel.kt, FlavorMark.kt}`

**Interfaces:**
- Consumes: Task 15.
- Produces the spec §5.3 layout:
  - top bar: Installed/Browse toggle, search, source filter, flavor filter (Forever only / Forever + Classic), Update all (n)
  - a compact list, with each row showing a `FlavorMark`
  - a detail panel; under 600dp width it slides over the list instead
  - an "Install from URL" action
  - `FlavorMark` doesn't rely on color alone (a letter/shape glyph plus a label in the detail panel)
- [ ] Step 1: Implement. Step 2: Build. Step 3: Commit `feat: addon browser screen`.

### Task 20: Game files, Account, Settings screens

**Files:**
- Create: `app/src/main/java/app/gamenative/wow/ui/{GameFilesScreen.kt, AccountScreen.kt, SettingsScreen.kt}`
- Game files screen:
  - shows the active install, product, version, size and location
  - Install / Repair (re-runs `GameInstaller.run`, which verifies and skips) / Change location (folder picker + `findGameRoot`)
  - "Convert to launch version" when the product is BETA and `wowf` is live (Task 5 + `download`)
  - an install picker when there is more than one install
- Account screen: save, update or forget the login (existing `BattleNetSignIn`).
- Settings screen: app version, check for app update, addon provider status (which keys are present), links.
- [ ] Step 1: Implement. Step 2: Build and run the tests. Step 3: Commit `feat: game files, account and settings screens`.

### Task 21: Release build and README

- [ ] Step 1: Update `README.md` with: what the fork adds, no-PC setup steps, how to build with API keys (`local.properties`), and the unofficial/ToS notice.
- [ ] Step 2: Run `./gradlew assembleModernRelease` (debug-key fallback is fine). Expected: an APK in `app/build/outputs/apk/modern/release/`.
- [ ] Step 3: Commit `docs: README for WoW Foreverywhere`. Push the branch and open a draft GitHub pre-release on the fork with the APK, marked untested-on-device.
