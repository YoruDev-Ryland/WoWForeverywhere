Fewer login disconnects, the on-screen keyboard in every language, and a home screen that fits 4:3 handhelds.

## Fixed

- **Logins dropped with BLZ51914400.** Every copy of the app showed Blizzard the same Windows machine ID and computer name. Each device now gets its own identity, created the first time you start the game after updating and kept from then on. Thanks to @dienstag0815 for the fix (#12, #37).
- **The on-screen keyboard only typed US letters.** Cyrillic, accented and other non-US letters now type into the game (#38).
- **On 4:3 handhelds like the Retroid Pocket Nova, the Play button shrank to a thin strip.** The home screen now puts Play beside the other tiles on any landscape screen, and fits short screens in portrait too (#39).
- **Settings → Update app could say no update was found.** GitHub limits how often one internet address can check, and phones on the same mobile network share an address. When that limit runs out, the app now checks the releases page instead, and says so if GitHub can't be reached.
- **Switching region didn't fully move the install.** The next update downloaded the new region's build, but the game still recorded the old region. It now moves to the new one, and switching region checks for that region's game update straight away.

## Changed

- Game downloads, repairs, updates and version checks now use HTTPS, and downloaded game configs are checked against their checksums before use. Ported from wow-forever-android, based on work by Rbm3267 and danieleforte92.

## Install

Download **WoW-Foreverywhere.apk** below, or use **Settings → Update app** inside the app. If you installed an earlier version from this page, it installs as an update and keeps your settings, saved login and addons.

If Android says the app "conflicts with an existing package", your current copy came from somewhere other than this page. Uninstall it first, then install this one. Your game files and addons stay on your storage.

New to the app? See the [README](https://github.com/YoruDev-Ryland/WoWForeverywhere#getting-started) for setup.
