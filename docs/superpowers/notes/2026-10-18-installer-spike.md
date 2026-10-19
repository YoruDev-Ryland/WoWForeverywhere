# Installer spike findings (2026-10-18)

**Outcome (a): a CDN-only minimal install boots, and the client builds its own local storage.**

Tested with `wowf` 1.60.1.70245 (x64 `Wow.exe` under Wine 11.20 + DXVK on a desktop PC). The client reached the WoW Forever login screen in about 30 s with no CASC error.

## What the client needs

| Removed | Result |
|---|---|
| nothing (full minimal set + Config.wtf) | login screen |
| `Data/indices` | login screen: the client downloaded all 1506 indices itself |
| all of `Data/` | login screen: the client fetched configs and indices from the CDN in `.build.info` |
| `<flavor>/WTF/Config.wtf` | hangs on a modal region/language picker, with nothing in the logs |
| `<flavor>/.flavor.info` | crash: `ERROR #134 ... Invalid schema ... StartupFiles` (it loads retail data) |

The minimum is `.build.info`, `<flavor>/.flavor.info`, `<flavor>/WTF/Config.wtf` (portal + locale), and the install-manifest files for Windows + arch + locale + region (39 files, about 345 MB for x64). Configs and indices only pre-seed what the client would fetch anyway. No local CASC writer is needed.

On first start the client creates `Data/data/` (shmem, `.idx` buckets, `data.000`...) and keeps streaming in the background at about 7 MB/s, even at the login screen. It reuses and keeps growing that storage on later starts.

## Gotchas built into the installer

- Install-manifest paths are relative to the flavor dir.
- Names mix case (`UTILS\` and `Utils\`), so the installer keeps the first casing seen per folder (`CaseFolder`).
- Tag selection: for every tag type that holds a wanted tag, an entry must carry one (`InstallManifest.select`).
- Patch-archive indices aren't under `/data/`. Archive-group indices are built locally. Both are left to the client.
- Every selected file is needed, not only `.exe`/`.dll` (`.dat`, `.pak`, `.bin`, the `D3D12/` Agility SDK).
- `Config.wtf` must set the portal (`"US"` worked) and the locale before first boot.

## Size

The download manifest for Windows + arm64 + enUS + US is 107.43 GB in total. That includes 39.88 GB tagged HighRes and 0.56 GB tagged Alternate, both optional; without them it's **66.99 GB**. By priority: 0 is 0.42 GB, 1 is 55.51 GB, 2 is 11.06 GB.

## Consequence for the design

The app downloads about 0.5 GB. The game then streams the rest itself. Playing before the stream completes is untested, because it needs a login. Pre-filling local storage from the app would need a CASC local-storage writer: possible, but not built.
