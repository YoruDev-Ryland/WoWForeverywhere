package app.gamenative.wow.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.gamenative.ui.screen.wow.GamePath
import app.gamenative.utils.StorageUtils
import app.gamenative.wow.product.GameInstall
import app.gamenative.wow.product.InstallScanner
import app.gamenative.wow.ui.components.FwButton
import app.gamenative.wow.ui.components.FwButtonKind
import app.gamenative.wow.ui.components.FwRow
import app.gamenative.wow.ui.theme.Fw
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GameFilesScreen(launcher: LauncherController, onBack: () -> Unit, onInstall: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<GameInstall>?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val path = StorageUtils.getPathFromTreeUri(context, uri) ?: return@rememberLauncherForActivityResult
        val root = GamePath.findGameRoot(File(path))
        if (root == null) {
            launcher.errorMessage = "No ${launcher.version.displayName} install in $path. Pick the folder that holds .build.info and Data."
        } else {
            launcher.useGameRoot(root.absolutePath)
        }
    }

    FwPage("Game files", onBack) {
        FwSection("Current install") {
            val files = launcher.files
            if (launcher.filesMissing) {
                Text("No game files at ${launcher.gamePath}.", style = Fw.Text)
            } else {
                Text(files.product?.displayName ?: "Unknown product", style = Fw.Text)
                Text(launcher.gamePath, style = Fw.Data)
                launcher.versionStatus?.let { Text("Version ${it.localVersion}", style = Fw.Data) }
                if (!files.exeExists) Text("The game program downloads when you press Play.", style = Fw.Secondary)
            }
            launcher.errorMessage?.let { Text(it, style = Fw.Secondary.copy(color = Fw.Warn)) }
            if (launcher.isUpdating) Text(launcher.updateStatusText, style = Fw.Text)
        }

        if (launcher.canConvertToLaunch) {
            FwSection("Launch version") {
                Text(
                    "WoW Forever has launched. Switch this beta install to the launch version. Your game data, settings and addons are reused, so only the new game program downloads.",
                    style = Fw.Text,
                )
                FwButton("Switch to launch version", launcher::convertToLaunch, enabled = !launcher.busy)
            }
        }

        FwSection("Install or repair") {
            Text(
                "Downloads ${launcher.version.displayName} from Blizzard's servers. Repair checks every file and only downloads what's missing or damaged.",
                style = Fw.Secondary,
            )
            FwButton(if (launcher.filesMissing) "Install ${launcher.version.displayName}" else "Install or repair", onInstall, enabled = !launcher.busy)
        }

        FwSection("Find an existing copy") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FwButton(
                    if (scanning) "Searching…" else "Search this device",
                    {
                        scanning = true
                        scope.launch {
                            found = withContext(Dispatchers.IO) { InstallScanner.scan(InstallScanner.storageRoots(context)) }
                            scanning = false
                        }
                    },
                    kind = FwButtonKind.Ghost,
                    enabled = !scanning,
                )
                FwButton("Choose folder", { picker.launch(null) }, kind = FwButtonKind.Ghost)
            }
            found?.let { installs ->
                if (installs.isEmpty()) Text("No installs found on internal storage or SD cards.", style = Fw.Secondary)
                Column {
                    installs.forEach { install ->
                        FwRow(selected = install.root == launcher.gamePath, onClick = { launcher.selectInstall(install) }) {
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
}
