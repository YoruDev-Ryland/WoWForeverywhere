package app.gamenative.wow.ui

import androidx.compose.ui.res.stringResource
import app.gamenative.R
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.ui.screen.wow.GamePath
import app.gamenative.ui.screen.wow.WowClientDownloader
import app.gamenative.utils.StorageUtils
import app.gamenative.wow.install.InstallController
import app.gamenative.wow.install.InstallRequest
import app.gamenative.wow.install.InstallUiState
import app.gamenative.wow.product.ForeverTracks
import app.gamenative.wow.product.GameInstall
import app.gamenative.wow.product.GameRegion
import app.gamenative.wow.product.GameRegions
import app.gamenative.wow.product.GameVersion
import app.gamenative.wow.product.InstallScanner
import app.gamenative.wow.product.WowProduct
import app.gamenative.wow.ui.components.FwButton
import app.gamenative.wow.ui.components.FwButtonKind
import app.gamenative.wow.ui.components.FwChip
import app.gamenative.wow.ui.components.FwLink
import app.gamenative.wow.ui.components.FwProgress
import app.gamenative.wow.ui.components.FwRow
import app.gamenative.wow.ui.theme.Fw
import com.winlator.core.GPUInformation
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Step(val label: Int) { WELCOME(R.string.fw_welcome), FIND(R.string.fw_find_game), INSTALL(R.string.fw_install), SIGN_IN(R.string.fw_sign_in) }

/** First launch: Welcome → find an existing copy → install → sign in → home (spec §5.4). */
@Composable
fun FirstRunScreen(
    launcher: LauncherController,
    onEditLogin: () -> Unit,
    onDone: () -> Unit,
    startAtInstall: Boolean = false,
    onOpenSettings: () -> Unit = {},
    onOpenAddonMode: () -> Unit = {},
) {
    var step by remember { mutableStateOf(if (startAtInstall) Step.INSTALL else Step.WELCOME) }
    var installedFresh by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Fw.Ground)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Step.entries.forEach {
                FwStepPill(stringResource(it.label), it == step)
            }
            FwStepPill(stringResource(R.string.fw_home), false)
        }
        HorizontalDivider(color = Fw.Rule)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp).widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when (step) {
                Step.WELCOME -> Welcome(launcher.version, onNext = { step = Step.FIND }, onOpenSettings = onOpenSettings, onOpenAddonMode = onOpenAddonMode)
                Step.FIND -> FindGame(
                    launcher,
                    onUse = { step = Step.SIGN_IN },
                    onInstall = { step = Step.INSTALL },
                )
                Step.INSTALL -> InstallStep(launcher, onDone = {
                    installedFresh = true
                    step = Step.SIGN_IN
                }, onBack = { step = Step.FIND })
                Step.SIGN_IN -> SignIn(launcher, onEditLogin, onDone, installedFresh)
            }
        }
    }
}

@Composable
private fun FwStepPill(label: String, current: Boolean) {
    Text(
        label,
        style = Fw.Label.copy(fontSize = 13.sp, color = if (current) Fw.Ground else Fw.Ash),
        modifier = Modifier.background(if (current) Fw.Bone else Fw.Stone, Fw.Pill).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Welcome(version: GameVersion, onNext: () -> Unit, onOpenSettings: () -> Unit, onOpenAddonMode: () -> Unit) {
    val context = LocalContext.current
    val check = remember { DeviceCheck.run(context) }
    Text(stringResource(R.string.fw_device_welcome, version.displayName), style = Fw.Hero.copy(fontSize = 40.sp, lineHeight = 44.sp))
    Text(
        if (version == GameVersion.FOREVER) {
            stringResource(R.string.fw_this_app_installs_blizzard_s_wow_forever)
        } else {
            "This app installs Blizzard's ${version.displayName} client, keeps it updated, manages your addons and starts the game. Versions other than WoW Forever are experimental. Nothing here comes from Blizzard: it's an unofficial community app."
        },
        style = Fw.Text,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        check.forEach { (label, ok) ->
            Text((if (ok) "✓  " else "!  ") + label, style = Fw.Text.copy(color = if (ok) Fw.Ok else Fw.Warn))
        }
    }
    if (check.any { !it.second }) {
        Text(stringResource(R.string.fw_the_game_may_not_run_well_on), style = Fw.Secondary)
    }
    // Side by side when they fit, stacked on a narrow (portrait) screen.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FwButton(stringResource(R.string.fw_get_started), onNext)
        FwButton(stringResource(R.string.fw_playing_another_version_of_wow), onOpenSettings, kind = FwButtonKind.Ghost)
    }
    FwLink(stringResource(R.string.fw_already_play_wow_another_way_use_the), onOpenAddonMode)
}

object DeviceCheck {
    fun run(context: Context): List<Pair<String, Boolean>> {
        val adreno = runCatching { GPUInformation.isAdrenoGPU(context) }.getOrDefault(false)
        val renderer = runCatching { GPUInformation.getRenderer(context) }.getOrNull().orEmpty()
        val is64 = Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a")
        return listOf(
            (if (adreno) "Adreno GPU ($renderer)" else context.getString(R.string.fw_device_incompatible_gpu)) to adreno,
            context.getString(R.string.fw_64_bit_arm_processor) to is64,
            "Android ${Build.VERSION.RELEASE}" to (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q),
        )
    }
}

@Composable
private fun FindGame(launcher: LauncherController, onUse: () -> Unit, onInstall: () -> Unit) {
    val context = LocalContext.current
    var found by remember { mutableStateOf<List<GameInstall>?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Rescans whenever the screen comes back, e.g. after granting "All files access" in Settings.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        val job = scope.launch { found = withContext(Dispatchers.IO) { InstallScanner.scan(InstallScanner.storageRoots(context)) } }
        onPauseOrDispose { job.cancel() }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val path = StorageUtils.getPathFromTreeUri(context, uri) ?: return@rememberLauncherForActivityResult
        val root = GamePath.findGameRoot(File(path))
        if (root == null) {
            launcher.errorMessage = "No ${launcher.version.displayName} install in $path."
        } else {
            launcher.useGameRoot(root.absolutePath)
            onUse()
        }
    }

    Text(stringResource(R.string.fw_where_s_the_game), style = Fw.Title)
    if (!StorageUtils.hasStoragePermission(context, "/storage/emulated/0")) {
        Text(stringResource(R.string.fw_find_game_permission), style = Fw.Text)
        FwButton(stringResource(R.string.fw_allow_file_access), launcher::openStorageSettings)
    }
    when (val installs = found) {
        null -> Text(stringResource(R.string.fw_looking_on_internal_storage_and_sd_cards), style = Fw.Secondary)
        else -> {
            if (installs.isEmpty()) {
                Text(stringResource(R.string.fw_no_copy_of_1_s_found_on, launcher.version.displayName), style = Fw.Text)
            } else {
                Text(stringResource(R.string.fw_found_installs, installs.size), style = Fw.Text)
                Column {
                    installs.forEach { install ->
                        FwRow(selected = false, onClick = {
                            launcher.selectInstall(install)
                            onUse()
                        }) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("${install.product.displayName} · ${install.version}", style = Fw.Text)
                                Text(install.root, style = Fw.Data)
                            }
                        }
                    }
                }
            }
        }
    }
    launcher.errorMessage?.let { Text(it, style = Fw.Secondary.copy(color = Fw.Warn)) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FwButton(stringResource(R.string.fw_install_fresh), onInstall, kind = if (found.isNullOrEmpty()) FwButtonKind.Primary else FwButtonKind.Ghost)
        FwButton(stringResource(R.string.fw_choose_folder), { picker.launch(null) }, kind = FwButtonKind.Ghost)
    }
}

/** Storage choice, product, language, then live progress from [InstallController]. */
@Composable
fun InstallStep(launcher: LauncherController, onDone: () -> Unit, onBack: () -> Unit, repair: GameInstall? = null) {
    val context = LocalContext.current
    val state by InstallController.state.collectAsState()
    val roots = remember { InstallScanner.storageRoots(context) }
    var rootIndex by remember { mutableStateOf(if (roots.size > 1) 1 else 0) }
    var product by remember { mutableStateOf<WowProduct?>(null) }
    var region by remember { mutableStateOf(GameRegions.effective(null)) }
    val locale = remember { deviceLocale() }
    // Android 13+ hides the progress notification unless the app may post notifications. The download
    // starts either way; this only asks once per screen visit.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun start(req: InstallRequest) {
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        InstallController.start(context, req)
    }

    LaunchedEffect(Unit) {
        // WoW Forever installs the chosen track; the beta only if Blizzard lists no launch build yet.
        product = launcher.version.products.singleOrNull() ?: ForeverTracks.selected.product.takeIf { it == WowProduct.BETA }
            ?: withContext(Dispatchers.IO) {
                if (runCatching { WowClientDownloader.isProductLive(WowProduct.FOREVER) }.getOrDefault(false)) WowProduct.FOREVER else WowProduct.BETA
            }
    }
    LaunchedEffect(state) {
        // A finished full download belongs to Game files, not to this install.
        (state as? InstallUiState.Done)?.takeIf { !it.full }?.let {
            launcher.selectInstall(it.install)
            InstallController.reset()
            onDone()
        }
    }

    Text(stringResource(R.string.fw_install_1_s, launcher.version.displayName), style = Fw.Title)
    when (val s = state) {
        is InstallUiState.Running -> {
            Text(stringResource(R.string.fw_downloading_to, s.request.root), style = Fw.Text)
            val p = s.progress
            Text(
                if (p.totalBytes > 0) p.sizeText else stringResource(R.string.fw_reading_the_build_manifest),
                style = Fw.Data.copy(color = Fw.Bone),
            )
            FwProgress(p.fraction)
            p.stepText?.let { Text(it, style = Fw.Data) }
            Text(stringResource(R.string.fw_streaming_note), style = Fw.Secondary)
            FwButton(stringResource(R.string.fw_pause), { InstallController.pause(context) }, kind = FwButtonKind.Ghost)
        }
        is InstallUiState.Paused -> {
            Text(s.reason ?: stringResource(R.string.fw_download_paused_everything_downloaded_so_far_is), style = Fw.Text)
            s.progress?.let { FwProgress(it.fraction) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FwButton(stringResource(R.string.fw_resume), { InstallController.resume(context) })
                FwButton(stringResource(R.string.fw_cancel_download), { InstallController.cancel(context) }, kind = FwButtonKind.Ghost)
                FwButton(stringResource(R.string.fw_back_2), { InstallController.cancel(context); onBack() }, kind = FwButtonKind.Ghost)
            }
        }
        is InstallUiState.Failed -> {
            Text(stringResource(R.string.fw_the_download_stopped_1_s, s.message), style = Fw.Text.copy(color = Fw.Warn))
            Text(stringResource(R.string.fw_files_already_downloaded_are_kept_and_checked), style = Fw.Secondary)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FwButton(stringResource(R.string.fw_try_again), { InstallController.resume(context) })
                FwButton(stringResource(R.string.fw_back_2), { InstallController.cancel(context); onBack() }, kind = FwButtonKind.Ghost)
            }
        }
        else -> if (repair != null) {
            Text(stringResource(R.string.fw_repair_at, repair.root), style = Fw.Text)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FwButton(stringResource(R.string.fw_check_and_repair), { start(InstallRequest(File(repair.root), repair.product, locale)) })
                FwButton(stringResource(R.string.fw_back_2), onBack, kind = FwButtonKind.Ghost)
            }
        } else {
            Text(stringResource(R.string.fw_choose_where_to_put_the_game_it), style = Fw.Text)
            Column {
                roots.forEachIndexed { i, root ->
                    val free = root.usableSpace
                    FwRow(selected = i == rootIndex, onClick = { rootIndex = i }) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(if (i == 0) stringResource(R.string.fw_internal_storage) else stringResource(R.string.fw_sd_card_external_storage), style = Fw.Text)
                            Text("${File(root, launcher.version.defaultFolder)}", style = Fw.Data)
                        }
                        Text(stringResource(R.string.fw_free_space, free / 1e9), style = Fw.Data.copy(color = if (free > 80e9) Fw.Ok else Fw.Warn))
                        if (i == rootIndex) Text(stringResource(R.string.fw_selected), style = Fw.Data.copy(color = Fw.Brass))
                    }
                }
            }
            Text(stringResource(R.string.fw_version_and_language, product?.displayName ?: stringResource(R.string.fw_checking), locale), style = Fw.Data)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.fw_region), style = Fw.Text)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameRegion.entries.forEach { r -> FwChip("${regionLabel(r)} (${r.portal})", r == region, { region = r }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FwButton(
                    stringResource(R.string.fw_download_and_install),
                    {
                        val p = product ?: return@FwButton
                        GameRegions.save(context, region)
                        start(InstallRequest(File(roots[rootIndex], launcher.version.defaultFolder), p, locale, region = region.code))
                    },
                    enabled = product != null && roots.isNotEmpty(),
                )
                FwButton(stringResource(R.string.fw_back_2), onBack, kind = FwButtonKind.Ghost)
            }
        }
    }
}

/** The device language if WoW ships it, else English (spec §6.6). */
private fun deviceLocale(): String {
    val tag = Locale.getDefault().let { it.language + it.country }
    return WOW_LOCALES.firstOrNull { it.equals(tag, ignoreCase = true) } ?: "enUS"
}

private val WOW_LOCALES = listOf("enUS", "enGB", "deDE", "esES", "esMX", "frFR", "itIT", "koKR", "ptBR", "ruRU", "zhCN", "zhTW")

@Composable
private fun SignIn(launcher: LauncherController, onEditLogin: () -> Unit, onDone: () -> Unit, installedFresh: Boolean) {
    Text(stringResource(R.string.fw_sign_in), style = Fw.Title)
    Text(
        stringResource(R.string.fw_save_your_battle_net_email_and_password),
        style = Fw.Text,
    )
    if (launcher.hasSavedLogin) Text(stringResource(R.string.fw_login_saved), style = Fw.Text.copy(color = Fw.Ok))
    if (installedFresh) Text(stringResource(R.string.fw_first_start_note), style = Fw.Secondary)
    Text(stringResource(R.string.fw_foreverywhere_is_free_if_you_d_like), style = Fw.Secondary)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (launcher.hasSavedLogin) {
            FwButton(stringResource(R.string.fw_continue), onDone)
        } else {
            FwButton(stringResource(R.string.fw_save_login), onEditLogin)
            FwButton(stringResource(R.string.fw_skip_for_now), onDone, kind = FwButtonKind.Ghost)
        }
    }
}
