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
import app.gamenative.BuildConfig
import app.gamenative.utils.renderReleaseNotes
import app.gamenative.wow.ui.components.FwButton
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
        Text(title.uppercase(), style = Fw.Data.copy(color = Fw.Brass))
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
fun SettingsScreen(launcher: LauncherController, onBack: () -> Unit) {
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
        FwSection("Addon sources") {
            ProviderLine("CurseForge", BuildConfig.CURSEFORGE_API_KEY.isNotBlank())
            ProviderLine("Wago Addons", BuildConfig.WAGO_API_KEY.isNotBlank())
            ProviderLine("WoWInterface", true)
            ProviderLine("GitHub (install from link)", true)
            Text("CurseForge and Wago need an API key built into the app. Builds without one still use the other sources.", style = Fw.Secondary)
        }
        FwSection("Device") {
            Text("${launcher.gpu.edition} profile · render size ${launcher.gpu.screenSize}", style = Fw.Data)
        }
        FwSection("About") {
            Text(
                "Unofficial community project, not affiliated with Blizzard Entertainment. The game is downloaded from Blizzard's own servers and needs your own Battle.net account. Based on jaredgei/wow-forever-android (GPL-3.0).",
                style = Fw.Secondary,
            )
        }
    }
}

@Composable
private fun ProviderLine(name: String, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, style = Fw.Text)
        Text(if (enabled) "On" else "Off: no API key", style = Fw.Data.copy(color = if (enabled) Fw.Ok else Fw.Ash))
    }
}
