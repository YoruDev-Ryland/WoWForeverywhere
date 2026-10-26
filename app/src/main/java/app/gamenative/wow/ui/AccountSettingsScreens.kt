package app.gamenative.wow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.gamenative.wow.install.InstallController
import app.gamenative.wow.install.InstallUiState
import app.gamenative.wow.product.GameRegion
import app.gamenative.wow.product.GameRegions
import app.gamenative.wow.product.GameVersion
import app.gamenative.wow.ui.components.FwDialog
import app.gamenative.wow.ui.components.FwRow
import app.gamenative.wow.ui.components.GameIconImage
import app.gamenative.wow.ui.components.icon
import app.gamenative.BuildConfig
import app.gamenative.utils.renderReleaseNotes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.ui.platform.LocalContext
import app.gamenative.wow.ui.components.FwButton
import app.gamenative.wow.ui.components.Links
import app.gamenative.wow.ui.components.FwButtonKind
import app.gamenative.wow.ui.components.FwProgress
import app.gamenative.wow.ui.components.FwTopBar
import app.gamenative.wow.ui.theme.Fw

@Composable
internal fun FwPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(Fw.Ground)) {
        FwTopBar(title, onBack)
        HorizontalDivider(color = Fw.Rule)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp).widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) { content() }
    }
}

@Composable
internal fun FwSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = Fw.Label.copy(color = Fw.Brass, fontSize = 12.sp, letterSpacing = 1.6.sp))
        content()
    }
}

@Composable
fun AccountScreen(launcher: LauncherController, onBack: () -> Unit, onEditLogin: () -> Unit) {
    FwPage("Account", onBack) {
        FwSection("Battle.net login") {
            Text(
                if (launcher.hasSavedLogin) {
                    "Your email and password are saved on this device, encrypted with the Android keystore. The game logs you in automatically. Authenticator codes are still typed in the game."
                } else {
                    "No login saved. Save one to skip the login screen, or sign in inside the game each time."
                },
                style = Fw.Text,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FwButton(if (launcher.hasSavedLogin) "Change login" else "Save login", onEditLogin)
                if (launcher.hasSavedLogin) FwButton("Forget login", launcher::forgetLogin, kind = FwButtonKind.Danger)
            }
        }
        FwSection("How it works") {
            Text(
                "When you press Play the app hands your login to the game in a temporary file, then wipes it as soon as the game has read it, or after three minutes at most.",
                style = Fw.Secondary,
            )
        }
    }
}

@Composable
fun SettingsScreen(launcher: LauncherController, onBack: () -> Unit, onVersionChanged: () -> Unit = {}) {
    FwPage("Settings", onBack) {
        FwSection("App") {
            Text("WoW Foreverywhere v${BuildConfig.VERSION_NAME}", style = Fw.Text)
            val update = launcher.appUpdate
            if (update == null) {
                Text("No app update found.", style = Fw.Secondary)
                FwButton("Check for app update", launcher::checkAppUpdate, kind = FwButtonKind.Ghost)
            } else {
                Text("Version ${update.version} is available.", style = Fw.Text.copy(color = Fw.Brass))
                if (update.notes.isNotBlank()) Text(renderReleaseNotes(update.notes), style = Fw.Secondary)
                if (launcher.isDownloadingAppUpdate) {
                    Text("Downloading ${(launcher.appUpdateProgress * 100).toInt()}%", style = Fw.Data)
                    FwProgress(launcher.appUpdateProgress)
                } else {
                    FwButton(if (launcher.downloadedApk?.exists() == true) "Install update" else "Update app", launcher::performAppUpdate)
                }
            }
        }
        GameVersionSection(launcher, onVersionChanged)
        RegionSection(launcher)
        FwSection("Support Foreverywhere") {
            val context = LocalContext.current
            Text("Foreverywhere is free and open source. If it saves you some time, you can support its development on Ko-fi.", style = Fw.Text)
            FwButton("Support on Ko-fi", { Links.open(context, Links.KOFI) }, kind = FwButtonKind.Ghost, icon = Icons.Filled.LocalCafe)
        }
        FwSection("Addon sources") {
            ProviderLine("CurseForge", true)
            ProviderLine("Wago Addons", BuildConfig.WAGO_API_KEY.isNotBlank())
            ProviderLine("WoWInterface", true)
            ProviderLine("GitHub (addon catalog and links)", true)
            Text("Wago needs an API key built into the app. Builds without one still use the other sources.", style = Fw.Secondary)
        }
        FwSection("Device") {
            Text("${launcher.gpu.edition} profile · render size ${launcher.gpu.screenSize}", style = Fw.Data)
        }
        FwSection("About") {
            Text(
                "Unofficial community project, not affiliated with Blizzard Entertainment. The game is downloaded from Blizzard's own servers and needs your own Battle.net account. Based on jaredgei/wow-forever-android (GPL-3.0).",
                style = Fw.Secondary,
            )
            val context = LocalContext.current
            FwButton("Source code on GitHub", { Links.open(context, Links.REPO) }, kind = FwButtonKind.Ghost, icon = Icons.AutoMirrored.Filled.OpenInNew)
        }
    }
}

/** Settings, Region: the Battle.net region the game plays in, written as its portal at the next launch. */
@Composable
private fun RegionSection(launcher: LauncherController) {
    val context = LocalContext.current
    val branch = remember(launcher.gamePath, launcher.files.product) {
        app.gamenative.wow.product.BuildInfo.read(java.io.File(launcher.gamePath))?.active(launcher.files.product)?.get("Branch")
    }
    var current by remember(branch) { mutableStateOf(GameRegions.effective(branch)) }
    FwSection("Region") {
        Text("The Battle.net region you play in. A change applies the next time you start the game.", style = Fw.Secondary)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GameRegion.entries.forEach { r ->
                FwRow(selected = r == current, onClick = {
                    GameRegions.save(context, r)
                    current = r
                }) {
                    Text(r.label, style = Fw.Text, modifier = Modifier.weight(1f))
                    Text(r.portal, style = Fw.Data.copy(color = Fw.Ash))
                    if (r == current) Text("Selected", style = Fw.Data.copy(color = Fw.Brass))
                }
            }
        }
    }
}

/** Settings, Game version: WoW Forever or one of the experimental official versions. */
@Composable
private fun GameVersionSection(launcher: LauncherController, onVersionChanged: () -> Unit) {
    val install by InstallController.state.collectAsState()
    val downloading = install is InstallUiState.Running || install is InstallUiState.Paused
    var pending by remember { mutableStateOf<GameVersion?>(null) }
    fun switch(to: GameVersion) {
        pending = null
        if (launcher.switchVersion(to)) onVersionChanged()
    }
    FwSection("Game version") {
        Text("Foreverywhere is made for WoW Forever. You can also try other official versions of WoW, which are experimental.", style = Fw.Secondary)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GameVersion.entries.forEach { v ->
                val current = v == launcher.version
                FwRow(
                    selected = current,
                    onClick = {
                        when {
                            current || downloading || launcher.busy -> Unit
                            v.isExperimental -> pending = v
                            else -> switch(v)
                        }
                    },
                ) {
                    GameIconImage(v.icon, 24.dp) { Box(Modifier.size(24.dp)) }
                    Text(v.displayName, style = Fw.Text, modifier = Modifier.weight(1f))
                    if (v.isExperimental) Text("Experimental", style = Fw.Data.copy(color = Fw.Ash))
                    if (current) Text("Selected", style = Fw.Data.copy(color = Fw.Brass))
                }
            }
        }
        when {
            downloading -> Text("Finish or cancel the current download before switching.", style = Fw.Secondary.copy(color = Fw.Warn))
            launcher.busy -> Text("Wait for the game to start or the update to finish before switching.", style = Fw.Secondary.copy(color = Fw.Warn))
        }
    }
    pending?.let { v ->
        FwDialog(
            title = "Other WoW versions are experimental",
            message = "Foreverywhere is made for WoW Forever. Other versions may run poorly or not start at all, and they get little support. Your WoW Forever install and addons stay as they are, and you can switch back any time.",
            confirm = "Switch to ${v.displayName}",
            onConfirm = { switch(v) },
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun ProviderLine(name: String, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, style = Fw.Text)
        Text(if (enabled) "On" else "Off: no API key", style = Fw.Data.copy(color = if (enabled) Fw.Ok else Fw.Ash))
    }
}
