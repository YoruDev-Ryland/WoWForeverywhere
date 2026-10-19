package app.gamenative.wow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gamenative.BuildConfig
import app.gamenative.wow.ui.components.FwButton
import app.gamenative.wow.ui.components.FwButtonKind
import app.gamenative.wow.ui.components.FwTile
import app.gamenative.wow.ui.theme.Fw
import app.gamenative.wow.ui.theme.focusRing
import java.io.File

@Composable
fun HomeScreen(
    launcher: LauncherController,
    addonUpdates: Int?,
    addonCount: Int?,
    onOpenAddons: () -> Unit,
    onOpenGame: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }

    Column(Modifier.fillMaxSize().background(Fw.Ground).padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header(launcher)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 600.dp
            val play: @Composable (Modifier) -> Unit = { m -> PlayTile(launcher, onOpenGame, m.focusRequester(playFocus)) }
            val tiles: @Composable (Modifier) -> Unit = { m ->
                SmallTiles(launcher, addonUpdates, addonCount, onOpenAddons, onOpenGame, onOpenAccount, onOpenSettings, m)
            }
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    play(Modifier.weight(1f).fillMaxHeight())
                    tiles(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    play(Modifier.fillMaxWidth().height(220.dp))
                    tiles(Modifier.fillMaxWidth().height(320.dp))
                }
            }
        }
        Text("A  Select     B  Back", style = Fw.Data)
    }
}

@Composable
private fun Header(launcher: LauncherController) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Foreverywhere", style = Fw.Title, modifier = Modifier.weight(1f))
        val version = launcher.versionStatus
        val (text, color) = when {
            launcher.filesMissing -> "Game not installed" to Fw.Ash
            launcher.isCheckingVersion -> "Checking for game updates…" to Fw.Ash
            version == null -> "Game version unknown (offline?)" to Fw.Ash
            version.isOutdated -> "Game update ${version.remoteVersion} available" to Fw.Warn
            else -> "Game up to date" to Fw.Ok
        }
        Text(text, style = Fw.Data.copy(color = color))
    }
    launcher.errorMessage?.let { Text(it, style = Fw.Secondary.copy(color = Fw.Warn)) }
}

@Composable
private fun PlayTile(launcher: LauncherController, onOpenGame: () -> Unit, modifier: Modifier) {
    val files = launcher.files
    val version = launcher.versionStatus
    FwTile(
        title = if (launcher.filesMissing) "Install" else "Play",
        modifier = modifier,
        onClick = {
            when {
                launcher.filesMissing -> onOpenGame()
                version?.isOutdated == true -> launcher.performUpdate()
                launcher.canPlay -> launcher.launchGame()
            }
        },
    ) {
        Text(
            files.product?.let { "${it.displayName} · ${version?.localVersion ?: ""}" } ?: "Set up WoW Forever to start",
            style = Fw.Data,
        )
        Text(File(launcher.gamePath).path, style = Fw.Data, maxLines = 1, overflow = TextOverflow.Ellipsis)
        when {
            launcher.busy -> Text(if (launcher.isUpdating) launcher.updateStatusText else launcher.statusText, style = Fw.Text)
            launcher.filesMissing -> Text("Download the game, or find the copy already on this device.", style = Fw.Secondary)
            version?.isOutdated == true -> {
                Text("Live servers need ${version.remoteVersion}. Press A to update.", style = Fw.Secondary.copy(color = Fw.Warn))
                FwButton("Launch anyway", launcher::launchGame, kind = FwButtonKind.Ghost, modifier = Modifier.focusRing())
            }
            !launcher.hasSavedLogin -> Text("No saved login: you'll sign in inside the game.", style = Fw.Secondary)
        }
    }
}

@Composable
private fun SmallTiles(
    launcher: LauncherController,
    addonUpdates: Int?,
    addonCount: Int?,
    onOpenAddons: () -> Unit,
    onOpenGame: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FwTile("Addons", Modifier.weight(1f).fillMaxHeight(), badge = addonUpdates?.takeIf { it > 0 }?.toString(), onClick = onOpenAddons) {
                Text(
                    when {
                        addonCount == null -> "Browse and install addons"
                        addonUpdates != null && addonUpdates > 0 -> "$addonCount installed · $addonUpdates updates"
                        else -> "$addonCount installed"
                    },
                    style = Fw.Secondary,
                )
            }
            FwTile("Game files", Modifier.weight(1f).fillMaxHeight(), onClick = onOpenGame) {
                Text(if (launcher.filesMissing) "Not installed" else launcher.files.product?.displayName.orEmpty(), style = Fw.Secondary)
                if (launcher.canConvertToLaunch) Text("Launch version available", style = Fw.Secondary.copy(color = Fw.Brass))
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FwTile("Account", Modifier.weight(1f).fillMaxHeight(), onClick = onOpenAccount) {
                Text(if (launcher.hasSavedLogin) "Login saved on this device" else "No login saved", style = Fw.Secondary)
            }
            FwTile(
                "Settings",
                Modifier.weight(1f).fillMaxHeight(),
                badge = launcher.appUpdate?.let { "NEW" },
                onClick = onOpenSettings,
            ) {
                Text(launcher.appUpdate?.let { "App ${it.version} available" } ?: "App v${BuildConfig.VERSION_NAME}", style = Fw.Secondary)
            }
        }
    }
}
