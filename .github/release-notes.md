Addon installs and updates you can actually watch, a graphics driver picker, and fixes for newer phones.

## Added

- **Addons install and update side by side, with progress.** Up to three download at once, and each addon shows Queued, how far along it is, Unpacking, or Failed. A bar over the list counts the batch, and when it's done it tells you what updated and what failed, with **Retry**. **Update all** is faster too: it no longer looks every addon up a second time.
- **The app update shows its download.** After **Update now** the dialog stays open with the download in MB, so a slow connection doesn't look like a failure. You can hide it and keep using the app; the Settings tile shows the percentage, and Android's install prompt opens when it's done.
- **Pick the graphics driver in Settings → Graphics driver** (#68). Automatic stays the default. You can also pick one of the built-in Turnip drivers, your device's own driver, or add an adrenotools driver zip. If the game won't start after a change, switch back to Automatic.
- **Sign out of Battle.net** under **Account → Sign-in method** when you use the sign-in page, so another account can sign in next time. **Forget login** signs out too.

## Fixed

- **Adreno 850 phones** (like the RedMagic 12 Pro) use the device's own driver, since the built-in Turnip doesn't recognise that GPU and WoW said the 3D accelerator wasn't supported (#76, #78).
- **Apps on an SD card set up as internal storage** now get a clear message at Play instead of the game closing as it opens. Move the app to the phone's own storage to play (#70).
- **QuestieDB installs under the right folder name,** fixing "QuestieDB not installed" in game. Thanks @donghyeon-dev (#75).
- **Re-importing an addon zip** no longer lists the addon twice, and importing one module of a multi-folder addon like DBM keeps the rest of it.
- **Shift+number keybinds work from MMO mouse side buttons** and other keypads.

## Install

Download **WoW-Foreverywhere.apk** below, or use **Settings → Update app** inside the app. If you installed an earlier version from this page, it installs as an update and keeps your settings, saved login and addons.

If Android says the app "conflicts with an existing package", your current copy came from somewhere other than this page. Uninstall it first, then install this one. Your game files and addons stay on your storage.

New to the app? See the [README](https://github.com/YoruDev-Ryland/WoWForeverywhere#getting-started) for setup.
