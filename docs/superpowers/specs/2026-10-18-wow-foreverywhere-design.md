# WoW Foreverywhere — Design

Date: 2026-10-18
Status: Draft for approval

## 1. Intent

World of Warcraft: Forever is Blizzard's official "Classic+" game. Its beta is live now and it launches on 4 Nov 2026. [jaredgei/wow-forever-android](https://github.com/jaredgei/wow-forever-android) already runs Blizzard's Windows ARM64 client on Snapdragon Android handhelds such as the AYN Odin 3. But it can't install the game: the user has to copy `.build.info` and the ~67 GB `Data/` folder from a PC that already has the game installed. It also has no addon support.

We will fork that project so that a person with **only an Android handheld** can:

1. install the APK,
2. download and install WoW Forever from inside the app,
3. browse, install, update and remove any WoW Forever addon (a WowUp-style manager built in), and
4. play, logging in automatically the way the existing app already does.

Working name: **WoW Foreverywhere** (taken from the project folder; renaming is fine).

## 2. Goals

- No PC needed at any step.
- A full game installer that downloads from Blizzard's CDN to internal storage or an SD card, with pause/resume and background downloading.
- Detects and adopts an existing install, such as the user's current one on the SD card, without downloading it again.
- Game patch updates keep working (the existing behavior).
- An addon manager that covers **every addon available for WoW Forever**, not a curated list. Sources: CurseForge, Wago Addons, GitHub; WoWInterface if it has Forever content.
- Supports both the beta product (`wow_classic_beta`) and the launch product (`wowf`, expected; see §6.1).
- Controller-first UI that stays usable on screens smaller than the Odin 3's.

## 3. Non-goals

- Changing how the game runs: Wine/Proton, DXVK, Turnip, GPU detection, controller mapping and Config.wtf defaults all stay as upstream has them. Pulling upstream's improvements to these is covered in §5.1.
- Support for non-Snapdragon/Adreno devices beyond what upstream already supports.
- Any WoW version other than Forever (Retail, Classic Era, and so on).
- A "stream while you play" partial install. Possible later, not in this version.
- Shipping any Blizzard files in the APK.

## 4. Anti-goals (failure even if it "works")

- Requiring a PC for any part of setup.
- An addon browser limited to a hand-picked list.
- Breaking or degrading how the game runs compared with upstream.
- Updating the game or addons without asking.
- Leaking Battle.net credentials: plaintext storage, logs, or `login.txt` left on disk.

## 5. Decided

### 5.1 Base: fork jaredgei/wow-forever-android

- The fork is GPL-3.0 and stays GPL-3.0 and open source. Keep `THIRD_PARTY_NOTICES`.
- Upstream facts (HEAD 4ae075d, v2.2.6):
  - Kotlin + Jetpack Compose, single module `:app`, flavor `modern`, arm64-v8a, minSdk 29.
  - It is a stripped fork of GameNative 1.2.1 (Pluvia/Winlator).
  - Game runtime: `WowB-ARM64.exe` runs under Proton 11 ARM64EC, with DXVK 2.4.1 and Turnip via adrenotools. Bundled components are fetched by `tools/fetch_components.sh`. The rootfs is downloaded from `downloads.gamenative.app` on first run.
  - Key files, all in `app/src/main/java/app/gamenative/ui/screen/wow/`:
    - `WoWForeverScreen.kt`: launcher UI, `GamePath.status()`, `findGameRoot()`, Config.wtf handling.
    - `WowClientDownloader.kt`: TACT/CASC version check, BLTE decoding, ARM64 binary download, patch updates.
    - `BattleNetSignIn.kt`: credentials and `login.txt`.
  - Game launch: `ui/screen/xserver/XServerScreen.kt` (~L3860–3890).
  - Reference Python: `tools/download_wow_arm64.py`.
- **Keep the ability to merge upstream.** Keep new code in new packages/files and keep edits to upstream files small, so upstream fixes to the game-running side (drivers, GPU tuning, launch-product changes) can be merged regularly.
- Use a new application ID so the fork can be installed alongside upstream. Self-update points at the fork's GitHub releases.

### 5.2 Home screen: dashboard tiles (mockup option C, chosen by the user)

- One home screen made of large tiles. **Play** is the biggest tile and shows the game version and status (up to date / update available / not installed).
- Smaller tiles: **Addons** (badge with the update count), **Game files** (location, size, install/repair), **Account**, **Settings**.
- Each tile opens its own full screen.
- Large tiles were chosen so the layout works on smaller screens than the Odin 3's.
- Fully navigable with the D-pad/A/B; touch also works.

### 5.2a Visual design: no generic "AI app" look (user requirement, hard rule)

The user explicitly rejects the cookie-cutter look common to AI-generated apps. **Do not use:**
- skittles/rainbow accent colors, or many competing bright colors
- blue-to-purple palettes, or blue/purple as the default accent
- gradients: backgrounds, buttons, hero banners
- cards with a colored left-edge highlight bar
- the generic "rounded card grid + pastel badges + emoji icons" dashboard

Instead, give the app a deliberate, distinctive identity that fits WoW Forever, chosen and argued for before building screens:
- a restrained palette with one considered accent
- flat surfaces with real typographic hierarchy
- texture and material drawn from the game's own world rather than SaaS defaults

The brainstorm wireframes (including their gradient hero and emoji) showed **structure only**, not visual style. The builder proposes a visual direction and the user approves it before any UI is built out.

### 5.3 Addons screen: list + detail panel (mockup option A, chosen by the user)

- Top bar:
  - **Installed (n)** / **Browse** toggle
  - search box ("Search Forever addons…")
  - source filter (All / CurseForge / Wago / GitHub / …)
  - **Update all (n)**
- Left side: a compact list. Each row shows the icon, name, sources/downloads and a state tag (Installed / Update / none).
- Right side: a detail panel for the highlighted addon. It shows the icon, name, version, author, summary, dependencies, source, and Install / Update / Remove buttons.
- On narrow screens the detail panel slides over the list. Row sizing can be adjusted per device later (the user's call).

### 5.4 First-run flow (approved)

1. **Welcome.** Explains the app and checks the device (Snapdragon/Adreno, Android version). Warns on unsupported hardware.
2. **Where's the game?** Scans internal storage and the SD card for an existing install, reusing upstream's detection (`.build.info` + `Data/` + correct Product). Options: use the found install / install fresh / choose a folder.
3. **Install.**
   - Choose internal storage or the SD card; show free space against the ~80 GB needed.
   - Downloads from Blizzard's CDN, with pause/resume.
   - Runs as a foreground service with a progress notification, so it survives backgrounding and app restarts.
4. **Sign in.**
   - Battle.net email and password, stored encrypted with Android Keystore (upstream approach). "Skip for now" is allowed.
   - The authenticator code is still typed in-game.
5. **Home.** Optional nudge to browse popular addons.

### 5.5 Ongoing behavior

- When the app opens, it checks for game patches, addon updates and new app versions, and shows them as badges on the tiles.
- Nothing updates without the user's consent. The exception is the existing upstream behavior: an outdated game blocks Play and offers "Update" / "Launch anyway".

### 5.6 Login

Keep upstream's mechanism unchanged:

- Keystore-encrypted credentials.
- At launch, `<flavor>/login.txt` is written with `email\npassword\n`.
- It is zeroed and deleted once `Logs/Hotfix.log` passes 50 KB, or after 180 s at most. It is also removed on resume, on exit and on force-close.

### 5.7 Addon sources and API access

- The user will apply for a **CurseForge API key**, and if needed **Wago partner access**, under their own name.
- **The key must not be committed to the public repo.** Inject it at build time from local properties or CI secrets. A build without a key still works, with only the keyless sources enabled. This mirrors WowUp's split between WowUp and WowUp-CF builds.
- GitHub (install from a repo/release URL) and WoWInterface work without keys and must work in any build.
- Forever flavor facts (from Wago's public `/api/data/game`):
  - flavor id `forever`, patch `1.60.1`
  - TOC suffixes `-Camelot`, `_Camelot`, `-Forever`, `_Forever`
  - The numeric `## Interface` value is unverified (likely 16001). Confirm it from a live client install.

### 5.8 Addon manager behavior

- **Compatibility (user decision).** The browser shows both kinds of addon, each with a small game-flavor mark next to it so the user can tell them apart at a glance (the user suggested a game logo):
  - **Forever addons:** releases explicitly made for Forever (the Forever game version on the source, or a `-Forever`/`-Camelot`/`_Forever`/`_Camelot` TOC). These are preferred whenever a Forever release exists.
  - **Classic addons:** releases for Classic Era (vanilla) only. They are listed with the Classic mark and a short "made for Classic, may not work" note in the detail panel.
  - Filter: Forever only / Forever + Classic (default).
  - "Update available" and TOC selection always prefer the Forever release when there is one. An installed Classic addon offers to switch once a Forever release appears.
  - The marks follow the no-generic-AI-look rule (§5.2a). They must stay readable at list-row size, and color must not be the only thing telling them apart. If Blizzard's logos turn out to be a trademark problem, a neutral mark is fine.
- Search across all enabled sources. Merge duplicates of the same addon found on multiple sources.
- Install, update, remove, and "update all".
- Resolve required dependencies (libraries) automatically.
- Install from a URL (GitHub repo/release, or a direct zip).
- **Recognize addons already in `Interface/AddOns`**, including folders installed by WowUp on PC. Match them by TOC metadata (`X-Curse-Project-ID`, `X-Wago-ID`, `X-WoWI-ID`, title/version) and by folder fingerprint.
- Multi-folder addons are one logical addon. Removing it removes all of its folders.
- Install target: `<root>/<flavor>/Interface/AddOns/`.

## 6. Constraints and known risks

### 6.1 Launch product changeover (deadline: 4 Nov 2026)

- The live patch service currently shows:
  - `wowf` 1.60.1.70245 (subfolder `_forever_`, binary `Wow-arm64.exe`)
  - `wow_classic_beta` 1.60.1.70450 (subfolder `_classic_beta_`, binary `WowB-ARM64.exe`)
- Upstream currently hard-codes the beta product in three places: detection (`Product == wow_classic_beta`), the launch path, and the version URL.
- Make product, subfolder and binary name a per-install data record rather than constants. Detect which product an install is from its `.build.info`.
- Fresh installs default to the launch product once it is live.
- Not verified: that `wowf` is the final launch product code, and that `login.txt` works on the launch build. Both must be verified on launch day.

### 6.2 Full installer scope

- Upstream only downloads the ARM64 binaries and patch configs and indices. A full install needs much more:
  - all `Data/data` archives plus their indexes, as required by the build config, encoding and install manifests
  - the install-manifest files tagged for Windows/arm64 and the locale
  - `.build.info`
  - `<flavor>/.flavor.info`
  - the starting `WTF/Config.wtf` (upstream already writes this)
- The client streams missing content from the CDN while you play, so the exact required set is a builder decision. It must produce an install the client accepts without the "CAS system was unable to initialize" error, and that doesn't stall in-game.
- Downloads must be verifiable (MD5/hash per file), resumable at file granularity, and restartable after a crash without corrupting the install.
- Size: about 67 GB of `Data/`, about 80 GB free space recommended. Blizzard's product config says `required_space` is 128 GB. Free-space checks should use a conservative figure.

### 6.3 Storage access on Android

- Writing tens of GB to an SD card at speed rules out the Storage Access Framework for the bulk data. Upstream already reads arbitrary folders.
- The builder must choose the permission model: `MANAGE_EXTERNAL_STORAGE` (sideloaded only, no Play Store) or app-specific external dirs. App-specific dirs are deleted on uninstall, which would wipe the 67 GB.
- The user's existing install lives at a user-chosen path on the SD card and must be adoptable.

### 6.4 Terms of service

- Using a third-party installer and launcher is a grey area under Blizzard's terms. Upstream already accepts this risk.
- Mitigations: ship no Blizzard files, use only the user's own account, download only from Blizzard's own CDN, and say clearly in the app that it is unofficial.
- CurseForge and Wago API terms apply to the addon features (attribution, no rehosting of files, and so on).

### 6.5 Hardware

- arm64, Snapdragon 8-series (Adreno 7xx/8xx), Android 10+, as in upstream.
- Primary test device: AYN Odin 3 (512 GB internal + 512 GB SD card), with the existing install on the SD card.

### 6.6 Decisions made by Claude (not the user; open to override)

- **Release order.** Before 4 Nov the priority is:
  1. per-install product record + `wowf` launch support + adoption of existing installs, so current users keep playing at launch;
  2. full installer;
  3. addon manager.
  The dashboard UI and first-run flow come in with the installer.
- **Installer feasibility is unproven.** Nobody has shown a CDN-only full install starting cleanly. The installer phase starts with a throwaway spike (e.g. extending `tools/download_wow_arm64.py` on a PC) that builds a full install and launches it, before Android installer work begins.
- **Beta → launch.** The data model is a list of installs, but the UI has one active install at a time, with a picker in Game files only when more than one exists. When a beta install is found after launch, offer to convert it to `wowf` by reusing its `Data/` archives (same `tpr/wow` CDN), so 67 GB isn't downloaded again. The user's SD-card install is a `wow_classic_beta` install (it is the only product upstream supports).
- **Locale.** A fresh install defaults to the device locale if Blizzard offers it, otherwise enUS, with a picker on the install screen. An adopted install keeps its own locale. Changing locale later is out of scope for version 1.
- **Upstream.** Fork-only for now. Changes to upstream files are thin shims. Clean pieces may be offered upstream later, but that is optional.

## 7. Left to the builder

- The internal architecture of the installer and addon modules, the Kotlin libraries used (HTTP, zip, DB), and how the installed-addon database is stored.
- How to merge duplicate addons across sources, and how to rank search results.
- The exact file set for a "complete" install (within §6.2's requirements), download concurrency, and the choice of CDN host.
- Visual styling beyond the approved layouts.
- Whether WoWInterface is supported in version 1, depending on how much Forever content it has.
- Test strategy. For example, the TACT/BLTE parsing and the addon TOC parsing/matching can be unit-tested against recorded fixtures.

## 8. Prior art

- Upstream: jaredgei/wow-forever-android (GPL-3.0).
- **WowUp** (open source, Electron/Angular): reference for multi-source addon resolution, CurseForge/Wago/GitHub/WoWInterface providers, fingerprint matching and the CF key split.
- **instawow** (Python): another multi-source resolver to use as a reference.
- TACT/CASC references: upstream's `download_wow_arm64.py` and `WowClientDownloader.kt`, CASCLib, TACT.Net.
