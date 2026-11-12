package app.gamenative.wow.ui

import androidx.compose.ui.res.stringResource
import app.gamenative.R
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
import app.gamenative.wow.addons.AddonMode
import app.gamenative.wow.install.InstallController
import app.gamenative.wow.install.InstallUiState
import app.gamenative.wow.product.GameRegion
import app.gamenative.wow.product.GameRegions
import app.gamenative.wow.product.GameAccounts
import app.gamenative.wow.ui.components.FwChip
import androidx.compose.foundation.horizontalScroll
import app.gamenative.wow.product.ForeverTrack
import app.gamenative.wow.product.GameVersion
import app.gamenative.wow.product.RenderSize
import app.gamenative.wow.product.RenderSizes
import app.gamenative.ui.screen.wow.fitScreenSizeToWindow
import app.gamenative.wow.ui.components.FwDialog
import app.gamenative.wow.ui.components.FwRow
import app.gamenative.wow.ui.components.GameIconImage
import app.gamenative.wow.ui.components.icon
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import app.gamenative.wow.FrameGeneration
import app.gamenative.wow.debug.DebugLog
import app.gamenative.wow.debug.DebugReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    FwPage(stringResource(R.string.fw_account), onBack) {
        FwSection(stringResource(R.string.fw_battle_net_login)) {
            Text(
                if (launcher.hasSavedLogin) {
                    stringResource(R.string.fw_your_email_and_password_are_saved_on)
                } else {
                    stringResource(R.string.fw_no_login_saved_save_one_to_skip)
                },
                style = Fw.Text,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FwButton(if (launcher.hasSavedLogin) stringResource(R.string.fw_change_login) else stringResource(R.string.fw_save_login), onEditLogin)
                if (launcher.hasSavedLogin) FwButton(stringResource(R.string.fw_forget_login), launcher::forgetLogin, kind = FwButtonKind.Danger)
            }
        }
        GameAccountSection()
        BlockedLoginSection()
        FwSection(stringResource(R.string.fw_how_it_works)) {
            Text(
                stringResource(R.string.fw_when_you_press_play_the_app_hands),
                style = Fw.Secondary,
            )
        }
    }
}

/**
 * Account, Can't log in: Blizzard locks direct game logins from a network the account hasn't used (BLZ51914400,
 * after any CAPTCHA), and one sign-in on the web lifts it. BLZ51914500 is a password that didn't match.
 */
@Composable
private fun BlockedLoginSection() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    FwSection(stringResource(R.string.fw_can_t_log_in)) {
        Text(
            stringResource(R.string.fw_login_blocked_hint),
            style = Fw.Secondary,
        )
        FwButton(stringResource(R.string.fw_fix_a_blocked_login), { open = true }, kind = FwButtonKind.Ghost)
    }
    if (open) {
        FwDialog(
            title = stringResource(R.string.fw_blocked_login),
            message = stringResource(R.string.fw_login_blocked_explanation),
            confirm = stringResource(R.string.fw_open_battle_net),
            onConfirm = {
                open = false
                Links.open(context, Links.BATTLE_NET)
            },
            onDismiss = { open = false },
            dismiss = stringResource(R.string.fw_close),
        ) {
            listOf(
                stringResource(R.string.fw_login_blocked_step_one),
                stringResource(R.string.fw_login_blocked_step_two),
                stringResource(R.string.fw_login_blocked_step_three),
                stringResource(R.string.fw_login_blocked_step_four),
            ).forEach { Text(it, style = Fw.Text) }
        }
    }
}

/** Account, Game account: which WoW account to play when the Battle.net login has several. */
@Composable
private fun GameAccountSection() {
    val context = LocalContext.current
    var current by remember { mutableStateOf(GameAccounts.chosen) }
    fun pick(number: Int?) {
        GameAccounts.save(context, number)
        current = number
    }
    FwSection(stringResource(R.string.fw_game_account)) {
        Text(
            stringResource(R.string.fw_game_account_explanation),
            style = Fw.Secondary,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FwChip(stringResource(R.string.fw_the_game_s_choice), current == null, { pick(null) })
            GameAccounts.NUMBERS.forEach { n -> FwChip(GameAccounts.name(n), current == n, { pick(n) }) }
        }
    }
}

@Composable
fun SettingsScreen(
    launcher: LauncherController,
    onBack: () -> Unit,
    onVersionChanged: () -> Unit = {},
    onOpenControllerTest: () -> Unit = {},
    /** The folder addon manager mode manages, or null when the app is the full launcher. */
    addonFolder: AddonMode.Folder? = null,
    onOpenAddonMode: () -> Unit = {},
    onDisableAddonMode: () -> Unit = {},
) {
    FwPage(stringResource(R.string.fw_settings), onBack) {
        FwSection(stringResource(R.string.fw_app)) {
            Text("WoW Foreverywhere v${BuildConfig.VERSION_NAME}", style = Fw.Text)
            val update = launcher.appUpdate
            if (update == null) {
                Text(
                    if (launcher.appUpdateCheckFailed) stringResource(R.string.fw_couldn_t_reach_github_to_check_for) else stringResource(R.string.fw_no_app_update_found),
                    style = Fw.Secondary,
                )
                FwButton(stringResource(R.string.fw_check_for_app_update), launcher::checkAppUpdate, kind = FwButtonKind.Ghost)
            } else {
                Text(stringResource(R.string.fw_version_1_s_is_available, update.version), style = Fw.Text.copy(color = Fw.Brass))
                if (update.notes.isNotBlank()) Text(renderReleaseNotes(update.notes), style = Fw.Secondary)
                if (launcher.isDownloadingAppUpdate) {
                    Text(stringResource(R.string.fw_app_update_progress, (launcher.appUpdateProgress * 100).toInt()), style = Fw.Data)
                    FwProgress(launcher.appUpdateProgress)
                } else {
                    FwButton(if (launcher.downloadedApk?.exists() == true) stringResource(R.string.fw_install_update) else stringResource(R.string.fw_update_app), launcher::performAppUpdate)
                }
            }
        }
        if (addonFolder != null) AddonModeSection(addonFolder, onOpenAddonMode, onDisableAddonMode)
        if (addonFolder == null) {
            GameVersionSection(launcher, onVersionChanged)
            RegionSection(launcher)
        }
        FwSection(stringResource(R.string.fw_support_foreverywhere)) {
            val context = LocalContext.current
            Text(stringResource(R.string.fw_foreverywhere_is_free_and_open_source_if), style = Fw.Text)
            FwButton(stringResource(R.string.fw_support_on_ko_fi), { Links.open(context, Links.KOFI) }, kind = FwButtonKind.Ghost, icon = Icons.Filled.LocalCafe)
        }
        FwSection(stringResource(R.string.fw_addon_sources)) {
            ProviderLine("CurseForge", true)
            ProviderLine("Wago Addons", BuildConfig.WAGO_API_KEY.isNotBlank())
            ProviderLine("WoWInterface", true)
            ProviderLine(stringResource(R.string.fw_github_addon_catalog), true)
            Text(stringResource(R.string.fw_wago_needs_an_api_key_built_into), style = Fw.Secondary)
        }
        if (addonFolder == null) {
            FwSection(stringResource(R.string.fw_device)) {
                Text(stringResource(R.string.fw_gpu_profile, launcher.gpu.edition), style = Fw.Data)
            }
            ResolutionSection(launcher)
            FrameGenerationSection()
            TroubleshootingSection(launcher, onOpenControllerTest)
            AddonModeSection(null, onOpenAddonMode, onDisableAddonMode)
        }
        FwSection(stringResource(R.string.fw_about)) {
            Text(
                stringResource(R.string.fw_about_project),
                style = Fw.Secondary,
            )
            val context = LocalContext.current
            FwButton(stringResource(R.string.fw_source_code_on_github), { Links.open(context, Links.REPO) }, kind = FwButtonKind.Ghost, icon = Icons.AutoMirrored.Filled.OpenInNew)
        }
    }
}

/** Settings, Addon manager mode: offered when the app is the full launcher, and the way back out of it. */
@Composable
private fun AddonModeSection(folder: AddonMode.Folder?, onOpen: () -> Unit, onDisable: () -> Unit) {
    FwSection(stringResource(R.string.fw_addon_manager_mode)) {
        if (folder == null) {
            Text(
                stringResource(R.string.fw_addon_mode_description),
                style = Fw.Secondary,
            )
            FwButton(stringResource(R.string.fw_use_addon_manager_mode), onOpen, kind = FwButtonKind.Ghost)
        } else {
            Text(stringResource(R.string.fw_managed_addons_folder, folder.version?.displayName ?: ""), style = Fw.Text)
            Text(folder.addonsDir.path, style = Fw.Data)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FwButton(stringResource(R.string.fw_change_folder), onOpen, kind = FwButtonKind.Ghost)
                FwButton(stringResource(R.string.fw_turn_off_addon_manager_mode), onDisable)
            }
            Text(stringResource(R.string.fw_addon_mode_disable_description), style = Fw.Secondary)
        }
    }
}

/** Settings, Troubleshooting: the controller test, debug logging and a report to attach to an issue. */
@Composable
private fun TroubleshootingSection(launcher: LauncherController, onOpenControllerTest: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logging by remember { mutableStateOf(DebugLog.isEnabled(context)) }
    var building by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    FwSection(stringResource(R.string.fw_troubleshooting)) {
        Text(
            stringResource(R.string.fw_if_a_button_doesn_t_work_in),
            style = Fw.Secondary,
        )
        FwButton(stringResource(R.string.fw_controller_test), onOpenControllerTest, kind = FwButtonKind.Ghost)
        Text(
            stringResource(R.string.fw_debug_report_explanation),
            style = Fw.Secondary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(false to stringResource(R.string.fw_debug_logging_off), true to stringResource(R.string.fw_debug_logging_on)).forEach { (value, label) ->
                FwRow(selected = logging == value, onClick = {
                    DebugLog.setEnabled(context, value)
                    logging = value
                }) {
                    Text(label, style = Fw.Text, modifier = Modifier.weight(1f))
                    if (logging == value) Text(stringResource(R.string.fw_selected), style = Fw.Data.copy(color = Fw.Brass))
                }
            }
        }
        FwButton(if (building) stringResource(R.string.fw_preparing_report) else stringResource(R.string.fw_share_debug_report), {
            building = true
            failed = false
            scope.launch {
                val zip = withContext(Dispatchers.IO) {
                    runCatching { DebugReport.build(context, launcher.debugSummary(), launcher.gameFolder(), launcher.debugSecrets()) }.getOrNull()
                }
                building = false
                if (zip != null) DebugReport.share(context, zip) else failed = true
            }
        }, kind = FwButtonKind.Ghost, enabled = !building)
        if (failed) Text(stringResource(R.string.fw_couldn_t_put_the_report_together_try), style = Fw.Secondary.copy(color = Fw.Warn))
    }
}

/** Settings, Frame generation: the player's own Lossless.dll, which turns on the in-game LSFG tab. */
@Composable
private fun FrameGenerationSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasDll by remember { mutableStateOf(FrameGeneration.hasDll(context)) }
    var enabled by remember { mutableStateOf(FrameGeneration.isEnabled(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
                    ?.let { FrameGeneration.import(context, it) } ?: FrameGeneration.ImportResult.FAILED
            }
            message = when (result) {
                FrameGeneration.ImportResult.OK -> null
                FrameGeneration.ImportResult.NOT_A_DLL -> context.getString(R.string.fw_lossless_invalid)
                FrameGeneration.ImportResult.NO_SHADERS -> context.getString(R.string.fw_lossless_no_shaders)
                FrameGeneration.ImportResult.FAILED -> context.getString(R.string.fw_lossless_read_failed)
            }
            hasDll = FrameGeneration.hasDll(context)
            enabled = FrameGeneration.isEnabled(context)
        }
    }
    FwSection(stringResource(R.string.fw_frame_generation)) {
        Text(
            stringResource(R.string.fw_frame_generation_explanation),
            style = Fw.Secondary,
        )
        if (hasDll) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(true to stringResource(R.string.fw_on), false to stringResource(R.string.fw_off)).forEach { (value, label) ->
                    FwRow(selected = enabled == value, onClick = {
                        FrameGeneration.setEnabled(context, value)
                        enabled = value
                    }) {
                        Text(label, style = Fw.Text, modifier = Modifier.weight(1f))
                        if (enabled == value) Text(stringResource(R.string.fw_selected), style = Fw.Data.copy(color = Fw.Brass))
                    }
                }
            }
            val usable = remember(hasDll, message) { FrameGeneration.hasShaders(context) }
            if (usable) {
                Text(stringResource(R.string.fw_lossless_dll_added_a_change_applies_the), style = Fw.Data)
            } else {
                Text(
                    stringResource(R.string.fw_frame_generation_missing_shaders),
                    style = Fw.Secondary.copy(color = Fw.Warn),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FwButton(stringResource(R.string.fw_choose_another_file), { picker.launch(arrayOf("*/*")) }, kind = FwButtonKind.Ghost)
                FwButton(stringResource(R.string.fw_remove), {
                    FrameGeneration.remove(context)
                    hasDll = false
                    enabled = FrameGeneration.isEnabled(context)
                }, kind = FwButtonKind.Ghost)
            }
        } else {
            FwButton(stringResource(R.string.fw_choose_lossless_dll), { picker.launch(arrayOf("*/*")) }, kind = FwButtonKind.Ghost)
        }
        message?.let { Text(it, style = Fw.Secondary.copy(color = Fw.Warn)) }
    }
}

/** Settings, Resolution: the game's screen size, from the device profile or picked here. */
@Composable
private fun ResolutionSection(launcher: LauncherController) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(RenderSizes.selected) }
    FwSection(stringResource(R.string.fw_resolution)) {
        Text(stringResource(R.string.fw_the_size_the_game_runs_at_higher), style = Fw.Secondary)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RenderSize.entries.forEach { r ->
                val result = remember(r) { fitScreenSizeToWindow(context, launcher.gpu.screenSize, r) }
                FwRow(selected = r == current, onClick = {
                    RenderSizes.save(context, r)
                    current = r
                }) {
                    Text(if (r == RenderSize.AUTO) stringResource(R.string.fw_automatic_for_this_device) else r.label, style = Fw.Text, modifier = Modifier.weight(1f))
                    Text(result, style = Fw.Data.copy(color = Fw.Ash))
                    if (r == current) Text(stringResource(R.string.fw_selected), style = Fw.Data.copy(color = Fw.Brass))
                }
            }
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
    FwSection(stringResource(R.string.fw_region)) {
        Text(stringResource(R.string.fw_the_battle_net_region_you_play_in), style = Fw.Secondary)
        // An update in progress downloads the region it started with, so the region can't change under it.
        if (launcher.isUpdating) Text(stringResource(R.string.fw_you_can_change_the_region_once_the), style = Fw.Secondary.copy(color = Fw.Warn))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GameRegion.entries.forEach { r ->
                FwRow(selected = r == current, onClick = {
                    if (launcher.isUpdating || r == current) return@FwRow
                    GameRegions.save(context, r)
                    current = r
                    // Regions can be on different builds, so the install may now need an update.
                    launcher.checkVersionStatus()
                }) {
                    Text(regionLabel(r), style = Fw.Text, modifier = Modifier.weight(1f))
                    Text(r.portal, style = Fw.Data.copy(color = Fw.Ash))
                    if (r == current) Text(stringResource(R.string.fw_selected), style = Fw.Data.copy(color = Fw.Brass))
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
    FwSection(stringResource(R.string.fw_game_version)) {
        Text(stringResource(R.string.fw_foreverywhere_is_made_for_wow_forever_you), style = Fw.Secondary)
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
                    if (v.isExperimental) Text(stringResource(R.string.fw_experimental), style = Fw.Data.copy(color = Fw.Ash))
                    if (current) Text(stringResource(R.string.fw_selected), style = Fw.Data.copy(color = Fw.Brass))
                }
            }
        }
        if (launcher.version == GameVersion.FOREVER) {
            Text(stringResource(R.string.fw_wow_forever_build), style = Fw.Text)
            Text(stringResource(R.string.fw_which_of_blizzard_s_wow_forever_releases), style = Fw.Secondary)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ForeverTrack.entries.forEach { t ->
                    FwRow(selected = t == launcher.foreverTrack, onClick = { if (!downloading) launcher.switchForeverTrack(t) }) {
                        Text(t.label, style = Fw.Text, modifier = Modifier.weight(1f))
                        Text(t.product.code, style = Fw.Data.copy(color = Fw.Ash))
                        if (t == launcher.foreverTrack) Text(stringResource(R.string.fw_selected), style = Fw.Data.copy(color = Fw.Brass))
                    }
                }
            }
        }
        when {
            downloading -> Text(stringResource(R.string.fw_finish_or_cancel_the_current_download_before), style = Fw.Secondary.copy(color = Fw.Warn))
            launcher.busy -> Text(stringResource(R.string.fw_wait_for_the_game_to_start_or), style = Fw.Secondary.copy(color = Fw.Warn))
        }
    }
    pending?.let { v ->
        FwDialog(
            title = stringResource(R.string.fw_other_wow_versions_are_experimental),
            message = stringResource(R.string.fw_other_versions_warning),
            confirm = stringResource(R.string.fw_switch_to_1_s, v.displayName),
            onConfirm = { switch(v) },
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun ProviderLine(name: String, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, style = Fw.Text)
        Text(if (enabled) stringResource(R.string.fw_on) else stringResource(R.string.fw_off_no_api_key), style = Fw.Data.copy(color = if (enabled) Fw.Ok else Fw.Ash))
    }
}

@Composable
internal fun regionLabel(region: GameRegion): String = stringResource(when (region) {
    GameRegion.US -> R.string.fw_region_us
    GameRegion.EU -> R.string.fw_region_eu
    GameRegion.KR -> R.string.fw_region_kr
    GameRegion.TW -> R.string.fw_region_tw
})
