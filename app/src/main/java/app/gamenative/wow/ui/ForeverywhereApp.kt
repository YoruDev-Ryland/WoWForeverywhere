package app.gamenative.wow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.gamenative.PluviaApp
import app.gamenative.events.AndroidEvent
import app.gamenative.ui.screen.wow.BattleNetCredentialDialog
import app.gamenative.wow.ui.components.FwDialog
import app.gamenative.wow.ui.theme.Fw
import app.gamenative.wow.ui.theme.ForeverywhereTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Routes {
    const val HOME = "home"
    const val ADDONS = "addons"
    const val GAME = "game"
    const val ACCOUNT = "account"
    const val SETTINGS = "settings"
    const val FIRST_RUN = "firstrun"
}

/** The app's own screens. Hosted by upstream's NavHost in place of `WoWForeverScreen`. */
@Composable
fun ForeverywhereApp(onLaunch: (appId: String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = remember { LauncherController(context, scope, onLaunch) }
    val nav = rememberNavController()
    var showLoginDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { launcher.start() }
    LifecycleResumeEffect(launcher.gamePath) {
        launcher.onResume()
        onPauseOrDispose { launcher.onPause() }
    }
    DisposableEffect(Unit) {
        val reset: (Any) -> Unit = { launcher.onGameEnded() }
        PluviaApp.events.on<AndroidEvent.ForceCloseApp, Unit>(reset)
        PluviaApp.events.on<AndroidEvent.GuestProgramTerminated, Unit>(reset)
        onDispose {
            PluviaApp.events.off<AndroidEvent.ForceCloseApp, Unit>(reset)
            PluviaApp.events.off<AndroidEvent.GuestProgramTerminated, Unit>(reset)
        }
    }

    ForeverywhereTheme {
        Box(Modifier.fillMaxSize().background(Fw.Ground)) {
            NavHost(nav, startDestination = if (launcher.registry.active() == null && launcher.filesMissing) Routes.FIRST_RUN else Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        launcher = launcher,
                        addonUpdates = null,
                        addonCount = null,
                        onOpenAddons = { nav.navigate(Routes.ADDONS) },
                        onOpenGame = { nav.navigate(Routes.GAME) },
                        onOpenAccount = { nav.navigate(Routes.ACCOUNT) },
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    )
                }
                composable(Routes.GAME) { GameFilesScreen(launcher, onBack = { nav.popBackStack() }, onInstall = { nav.navigate(Routes.FIRST_RUN) }) }
                composable(Routes.ACCOUNT) {
                    AccountScreen(launcher, onBack = { nav.popBackStack() }, onEditLogin = { showLoginDialog = true })
                }
                composable(Routes.SETTINGS) { SettingsScreen(launcher, onBack = { nav.popBackStack() }) }
                composable(Routes.ADDONS) { AddonsRoute(launcher, onBack = { nav.popBackStack() }) }
                composable(Routes.FIRST_RUN) {
                    FirstRunScreen(
                        launcher = launcher,
                        onEditLogin = { showLoginDialog = true },
                        onDone = { nav.navigate(Routes.HOME) { popUpTo(Routes.FIRST_RUN) { inclusive = true } } },
                    )
                }
            }
        }

        if (showLoginDialog) {
            BattleNetCredentialDialog(
                onDismiss = { showLoginDialog = false },
                onConfirm = {
                    showLoginDialog = false
                    launcher.onLoginSaved(it)
                },
            )
        }
        if (launcher.needsStoragePermission) {
            FwDialog(
                title = "Allow file access",
                message = "Android needs \"All files access\" so the app can install and update the game in this folder. Turn on \"Allow access to manage all files\" on the next screen.",
                confirm = "Open settings",
                onConfirm = launcher::openStorageSettings,
                onDismiss = { launcher.needsStoragePermission = false },
            )
        }
        if (launcher.needsInstallPermission) {
            FwDialog(
                title = "Allow app installs",
                message = "Android needs permission to install the update. Turn on \"Allow from this source\" on the next screen.",
                confirm = "Open settings",
                onConfirm = launcher::openInstallPermissionSettings,
                onDismiss = { launcher.needsInstallPermission = false },
            )
        }
    }
}
