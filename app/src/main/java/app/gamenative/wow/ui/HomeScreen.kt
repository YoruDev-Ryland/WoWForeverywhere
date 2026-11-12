package app.gamenative.wow.ui

import androidx.compose.ui.res.stringResource
import app.gamenative.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.BuildConfig
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.gamenative.wow.ui.components.ForeverArt
import app.gamenative.wow.ui.components.GameIcon
import app.gamenative.wow.ui.components.GameIconImage
import app.gamenative.wow.ui.components.icon
import app.gamenative.wow.ui.components.Links
import app.gamenative.wow.ui.components.FwActionBar
import app.gamenative.wow.ui.components.FwButton
import app.gamenative.wow.ui.components.FwButtonKind
import app.gamenative.wow.ui.components.FwTile
import app.gamenative.wow.ui.theme.Fw
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
            // Side by side on any landscape screen, 4:3 handhelds included (#39), and on wide tablets in portrait.
            val wide = maxWidth > maxHeight || maxWidth >= 600.dp
            val play: @Composable (Modifier) -> Unit = { m -> PlayTile(launcher, onOpenGame, m.focusRequester(playFocus)) }
            // Narrow side-by-side (a 4:3 handheld): the small tiles are too slim for the full title size.
            val tileTitle = if (wide && maxWidth < 720.dp) Fw.Title.copy(fontSize = 20.sp, lineHeight = 24.sp) else Fw.Title
            val tiles: @Composable (Modifier) -> Unit = { m ->
                SmallTiles(launcher, addonUpdates, addonCount, onOpenAddons, onOpenGame, onOpenAccount, onOpenSettings, tileTitle, m)
            }
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    play(Modifier.weight(1f).fillMaxHeight())
                    tiles(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                // Portrait: Play on top and the four small tiles below, never more than half the height so Play keeps its room.
                val tilesHeight = minOf(340.dp, (maxHeight - 14.dp) / 2)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    play(Modifier.fillMaxWidth().weight(1f))
                    tiles(Modifier.fillMaxWidth().height(tilesHeight))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.fw_a_select_b_back), style = Fw.Data, modifier = Modifier.weight(1f))
            SupportLink()
        }
    }
}

/** A quiet Ko-fi link in the hint row: muted, small, and only bronze when focused. */
@Composable
private fun SupportLink() {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier
            .border(if (focused) 2.dp else 0.dp, if (focused) Fw.Brass else Color.Transparent, Fw.Pill)
            .clickable(interactionSource = interaction, indication = null) { Links.open(context, Links.KOFI) }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.LocalCafe, contentDescription = null, tint = if (focused) Fw.Brass else Fw.Ash, modifier = Modifier.size(14.dp))
        Text(stringResource(R.string.fw_support_on_ko_fi), style = Fw.Data.copy(color = if (focused) Fw.Brass else Fw.Ash))
    }
}

@Composable
private fun Header(launcher: LauncherController) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameIconImage(launcher.version.icon, 34.dp) { Box(Modifier.size(34.dp)) }
            Text("Foreverywhere", style = Fw.Title.copy(fontSize = 21.sp), maxLines = 1, softWrap = false)
            if (launcher.version.isExperimental) {
                Text(launcher.version.displayName, style = Fw.Data, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(stringResource(R.string.fw_experimental), style = Fw.Data.copy(color = Fw.Warn), modifier = Modifier.background(Fw.Stone, Fw.Pill).padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
        val version = launcher.versionStatus
        val (text, color) = when {
            launcher.filesMissing -> stringResource(R.string.fw_game_not_installed) to Fw.Ash
            launcher.canConvertToLaunch -> stringResource(R.string.fw_wow_forever_is_out_switch_in_game) to Fw.Warn
            launcher.isCheckingVersion || !launcher.versionChecked -> stringResource(R.string.fw_checking_for_game_updates) to Fw.Ash
            version == null -> stringResource(R.string.fw_game_version_unknown_offline) to Fw.Ash
            version.isOutdated -> stringResource(R.string.fw_game_update_1_s_available, version.remoteVersion) to Fw.Warn
            else -> stringResource(R.string.fw_game_up_to_date) to Fw.Ok
        }
        // The title is measured first and keeps its line; a long status wraps inside the pill instead.
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Text(
                text,
                style = Fw.Data.copy(color = color),
                textAlign = TextAlign.End,
                modifier = Modifier.background(Fw.Stone, Fw.Pill).padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
    launcher.errorMessage?.let { Text(it, style = Fw.Secondary.copy(color = Fw.Warn)) }
}

@Composable
private fun PlayTile(launcher: LauncherController, onOpenGame: () -> Unit, modifier: Modifier) {
    val files = launcher.files
    val version = launcher.versionStatus
    FwTile(
        title = if (launcher.filesMissing) stringResource(R.string.fw_install) else stringResource(R.string.fw_play),
        modifier = modifier,
        art = ForeverArt.KEY_ART,
        titleStyle = Fw.Hero,
        footer = {
            when {
                launcher.busy -> FwActionBar(stringResource(R.string.fw_working), enabled = false)
                launcher.filesMissing -> FwActionBar(stringResource(R.string.fw_set_up))
                version?.isOutdated == true -> FwActionBar(stringResource(R.string.fw_update))
                else -> FwActionBar(stringResource(R.string.fw_play), enabled = launcher.canPlay)
            }
        },
        onClick = {
            when {
                launcher.filesMissing -> onOpenGame()
                version?.isOutdated == true -> launcher.performUpdate()
                launcher.canPlay -> launcher.launchGame()
            }
        },
    ) {
        Text(
            files.product?.let { listOfNotNull(it.displayName, version?.localVersion).joinToString(" · ") } ?: stringResource(R.string.fw_set_up_1_s_to_start, launcher.version.displayName),
            style = Fw.Data,
        )
        when {
            launcher.busy -> Text(if (launcher.isUpdating) launcher.updateStatusText else launcher.statusText, style = Fw.Text)
            launcher.filesMissing -> Text(stringResource(R.string.fw_download_the_game_or_find_the_copy), style = Fw.Secondary)
            version?.isOutdated == true -> {
                Text(stringResource(R.string.fw_live_servers_need_1_s_press_a, version.remoteVersion), style = Fw.Secondary.copy(color = Fw.Warn))
                FwButton(stringResource(R.string.fw_launch_anyway), launcher::launchGame, kind = FwButtonKind.Ghost)
            }
            !launcher.hasSavedLogin -> Text(stringResource(R.string.fw_no_saved_login_you_ll_sign_in), style = Fw.Secondary)
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
    titleStyle: TextStyle,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FwTile(stringResource(R.string.fw_addons), Modifier.weight(1f).fillMaxHeight(), art = ForeverArt.INTERFACE, titleStyle = titleStyle, badge = addonUpdates?.takeIf { it > 0 }?.toString(), onClick = onOpenAddons) {
                Text(
                    when {
                        addonCount == null -> stringResource(R.string.fw_browse_and_install_addons)
                        addonUpdates != null && addonUpdates > 0 -> stringResource(R.string.fw_1_s_installed_2_s_updates, addonCount, addonUpdates)
                        else -> stringResource(R.string.fw_1_s_installed, addonCount)
                    },
                    style = Fw.Secondary,
                )
            }
            FwTile(stringResource(R.string.fw_game_files), Modifier.weight(1f).fillMaxHeight(), art = ForeverArt.ASHENVALE, titleStyle = titleStyle, onClick = onOpenGame) {
                Text(if (launcher.filesMissing) stringResource(R.string.fw_not_installed) else launcher.files.product?.displayName.orEmpty(), style = Fw.Secondary)
                if (launcher.canConvertToLaunch) Text(stringResource(R.string.fw_launch_version_available), style = Fw.Secondary.copy(color = Fw.Brass))
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FwTile(stringResource(R.string.fw_account), Modifier.weight(1f).fillMaxHeight(), art = ForeverArt.SKYBORNE, titleStyle = titleStyle, onClick = onOpenAccount) {
                Text(if (launcher.hasSavedLogin) stringResource(R.string.fw_login_saved_on_this_device) else stringResource(R.string.fw_no_login_saved), style = Fw.Secondary)
            }
            FwTile(
                stringResource(R.string.fw_settings),
                Modifier.weight(1f).fillMaxHeight(),
                art = ForeverArt.CAMP,
                titleStyle = titleStyle,
                badge = launcher.appUpdate?.let { "NEW" },
                onClick = onOpenSettings,
            ) {
                Text(launcher.appUpdate?.let { stringResource(R.string.fw_app_1_s_available, it.version) } ?: stringResource(R.string.fw_app_v_1_s, BuildConfig.VERSION_NAME), style = Fw.Secondary)
            }
        }
    }
}
