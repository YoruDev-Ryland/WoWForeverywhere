Fixes for the character select crash with frame generation, accented letters and L2/R2, plus a Chinese launcher.

## Added

- **The launcher in Simplified and Traditional Chinese.** It follows your phone's language. Thanks to @AreChen for the translation (#45).

## Fixed

- **With frame generation on, the game crashed at character select.** Since November 11, Blizzard's servers send WoW Forever a new setting that makes the game rebuild its graphics the first time it arrives, and frame generation didn't survive the rebuild. The app now sets it up ahead of time, so frame generation can stay on (#46).
- **Settings → Region could show Americas while the game logged into another region.** After reinstalling over a game folder that had played elsewhere, the game kept logging into that region, where you had no characters. That showed the ruleset picker, then an expired subscription or a disconnect. Settings now shows the region the game actually logs into, and tapping your region fixes it (#46).
- **Accented letters from the on-screen keyboard didn't reach the game.** Typing "não" gave "no". Letters like ã, é, ñ and ç now type (#38).
- **L2/R2 did nothing on some Bluetooth controllers.** Controllers that send their triggers as plain buttons now work, and analog triggers work as before (#44).

## Install

Download **WoW-Foreverywhere.apk** below, or use **Settings → Update app** inside the app. If you installed an earlier version from this page, it installs as an update and keeps your settings, saved login and addons.

If Android says the app "conflicts with an existing package", your current copy came from somewhere other than this page. Uninstall it first, then install this one. Your game files and addons stay on your storage.

New to the app? See the [README](https://github.com/YoruDev-Ryland/WoWForeverywhere#getting-started) for setup.
