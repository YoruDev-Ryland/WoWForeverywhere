# Other WoW versions: design

## Intent

Some players want to use Foreverywhere for official WoW versions other than WoW Forever. This adds a **Game version** setting that switches the whole app (install, play, game files, addons) to one other official version. It is an experimental, lightly supported extra. Anyone who never touches the setting must see and get exactly today's app.

## Goals

- Settings gets a **Game version** choice: WoW Forever (default), Classic Era, Burning Crusade Anniversary, Mists of Pandaria Classic, Retail.
- Picking anything other than WoW Forever first shows a warning that other versions are highly experimental and may not run well or at all; the user confirms to switch. Switching back to Forever needs no warning.
- One version at a time. The chosen version drives First run, Home, Play, Game files, the installer, updates, sign-in and the addon browser.
- Each version keeps its own install. Switching back to Forever finds the Forever install exactly as it was.
- The addon browser shows addons for the chosen version only. In Forever mode it is unchanged (Forever first, Classic Era optional via the existing chip). Addons for Retail/Mists/Anniversary never appear in Forever mode.

## Non-goals

- Private servers, PTRs, betas other than the existing Forever beta, regions other than US.
- Per-version artwork. The Forever art and the app name stay.
- Running more than one version side by side, or a quick switcher on Home.
- Tuning performance for Retail.

## Anti-goals (failure even if it works)

- Any visible or behavioural change for a user who never changes the setting: same screens, same text, same addon results, same install paths, same files on disk.
- Losing or mixing a Forever install, its addons, or its addon database when switching.
- Shipping the CurseForge key (all CurseForge traffic still goes through the proxy; no new endpoints beyond the five the proxy allows).

## Facts verified from Blizzard (2026-10-22)

| Version | Product code | Flavor folder | ARM program | Live build |
| :--- | :--- | :--- | :--- | :--- |
| WoW Forever | `wowf` (+ beta `wow_classic_beta`) | `_forever_` | `Wow-arm64.exe` (manifest: `Wow-ARM64.exe`) | 1.60.1 |
| Classic Era | `wow_classic_era` | `_classic_era_` | `WowClassic-arm64.exe` | 1.15.9 |
| Burning Crusade Anniversary | `wow_anniversary` | `_anniversary_` | `WowClassic-arm64.exe` | 2.5.6 |
| Mists of Pandaria Classic | `wow_classic` | `_classic_` | `WowClassic-arm64.exe` | 5.5.4 |
| Retail | `wow` | `_retail_` | `Wow-ARM64.exe` | 12.1.5 |

All five install manifests carry `Windows`/`arm64` tags, so the existing installer (tag-type selection, minimal install) applies unchanged. Flavor folders come from each product's product config. Blizzard's round icons: Retail `https://forge.akamaized.net/icons/world-of-warcraft.svg`; the Classic family uses the existing Classic icon.

CurseForge game version type ids (from `/v1/games/1/version-types`): Forever 88568, Classic Era 67408, Burning Crusade Classic 73246 (used for Anniversary), Mists Classic 79434, Retail 517.

## How it works (decided)

- **Edition.** A "game version" groups products: Forever = {`wowf`, `wow_classic_beta`} with today's beta-until-launch logic; each other version is one product. The setting is stored in SharedPreferences, default Forever. Missing or unknown value means Forever.
- **Detection.** Wherever the app picks "the product in this folder" (`detectProduct`, `BuildInfo.products`, `InstallScanner`, `GamePath.status`), it only considers the selected version's products. A shared `World of Warcraft` folder with several flavors resolves to the selected one.
- **Installs.** The install registry remembers the active install per version. Default install folder: Forever keeps `WoW Forever`; the other versions use `World of Warcraft`, where they share `Data/` the way Blizzard's launcher does.
- **Launch.** Executable, flavor folder, `.flavor.info`, `Config.wtf` (portal = Branch region, `agentUID` = product code, locale), `login.txt` and the stall/Hotfix-log watcher all follow the selected product. The existing `-d3d11` argument stays.
- **Beta conversion** (Game files → Launch version) only exists in Forever mode.
- **Addons.** Each version defines its addon target: a main flavor and, for Forever only, a Classic Era fallback. Providers take the target and pick files by it:
  - CurseForge: type ids above.
  - WoWInterface: compatibility versions by major: 1.60+ (or name contains forever/camelot) Forever; other 1.x Classic Era; 2.x Anniversary; 5.x Mists; 10+ Retail.
  - Wago: its game-version string per version.
  - GitHub catalog: catalog files per version, from release metadata flavors and zip-name suffixes (BigWigs packager names: `-classic`, `-bcc`, `-mists`, `-mainline` or none).
  - Installed addon scanning: TOC suffixes and interface numbers per version (`-Mainline`, `-Vanilla`/`-Classic`, `-TBC`/`-BCC`, `-Mists`, `-Forever`; interface 1xxxx Classic family with 160xx Forever, 2xxxx Anniversary, 5xxxx Mists, 100000+ Retail).
  - Each version's addons live in its own flavor folder with its own addon database, as today.
- **UI.**
  - Settings: a Game version section listing the five versions, current one marked. Picking another one shows the experimental warning dialog (styled like the app's other dialogs), then switches.
  - Off Forever, Home shows the chosen version's name and round icon, and a small "Experimental" tag; "Set up WoW Forever to start" and similar text use the chosen version's name.
  - The addon browser's flavor marks and labels use the chosen version's name and icon; the "Forever + Classic" chip only exists in Forever mode.
- **Switching.** Not allowed while an install or update is running (the choice is disabled with a short note). After switching, the app re-checks game files for the new version, the same as on launch.

## Calls made without the owner (review these)

- **Reaching the setting before anything is installed.** The app opens First run when there's no install, and First run has no way to Settings. The Welcome screen gets one quiet ghost-style link, "Playing another version of WoW?", that opens Settings. After a switch from there, First run restarts for the new version. This is the only change a default user can see; remove it if unwanted.
- **After switching versions:** if the new version has an install, go to Home; if not, run First run (Find, Install, Sign in) for it, same as a fresh launch. The back stack is cleared either way.
- **Shared folders.** Other versions may install into a folder that already holds Forever (for example `World of Warcraft`), sharing `Data/` exactly as Blizzard's launcher does. The installer and repair must keep every other product's `.build.info` row and never delete files they don't own; this is a hard requirement with a test.
- **Auto sign-in on other versions** is attempted the same way and is not guaranteed. If a client ignores `login.txt`, the player signs in inside the game. This is acceptable for an experimental feature and doesn't block the work.

## Left to the builder

- Exact code structure (new types, how the target reaches providers, prefs key names).
- Confirming Wago's game-version strings and the GitHub catalog builder changes, including keeping today's `forever`/`classic` catalog keys working for released apps.
- Which `Config.wtf` graphics defaults apply to non-Forever versions (keep the Classic defaults for the Classic family; for Retail only write what is needed to start).
- Wording of the warning and labels, following the README voice: plain, professional, no em dashes.
- Tests for the new rules (detection per version, flavor rules, provider file picking, persistence default).
