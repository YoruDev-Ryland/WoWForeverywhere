A way past the "You have been disconnected" / BLZ51914400 login block, plus a pile of keyboard and login fixes.

## Added

- **Sign in through Battle.net's own page.** If the game keeps rejecting your saved login with BLZ51914400, go to **Account settings → Sign-in method** and pick **Battle.net sign-in page**. When you press Play, Battle.net's sign-in page opens, where you can confirm the device or enter a code, and the game then starts with a one-time session the way the Battle.net app starts it on a PC. Your password isn't used for this, but a saved one stays on the device until you tap **Forget login**. Only for WoW Forever, and still new, so if it fails please post `_forever_/Logs/BrowserLogin.log` from your game folder.
- **The game keeps running when you switch apps,** like to an authenticator app for a code. A quiet "World of Warcraft is running" notification shows while it's open.
- **Pick the game's language in Settings → Game language.** The app downloads that language's files with a repair (#63).
- **Type your game account name** when Battle.net has several WoW accounts on one login (#25).

## Fixed

- **Left Shift stays held for the game,** so Shift keybinds no longer only worked with right Shift (#61).
- **Shift+number keybinds work on a physical keyboard.** Shift+1 used to reach the game as a different key.
- **MMO mouse side buttons** and other key devices that aren't full keyboards now reach the game.
- **The game's saved account list is cleared when no account is picked,** which fixes logging into the wrong account (#62, #56).
- **Adreno 722 devices** (like the Retroid Pocket Duo) use the device's own graphics driver, so the game finds the GPU (#65).
- **Smaller download.** Proton ships without its unused 32-bit Windows files, about 60 MB less. Those were also what some antivirus apps flagged.

## Install

Download **WoW-Foreverywhere.apk** below, or use **Settings → Update app** inside the app. If you installed an earlier version from this page, it installs as an update and keeps your settings, saved login and addons.

If Android says the app "conflicts with an existing package", your current copy came from somewhere other than this page. Uninstall it first, then install this one. Your game files and addons stay on your storage.

New to the app? See the [README](https://github.com/YoruDev-Ryland/WoWForeverywhere#getting-started) for setup.
