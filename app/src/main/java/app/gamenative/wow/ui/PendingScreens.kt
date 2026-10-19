package app.gamenative.wow.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.gamenative.wow.ui.theme.Fw

// Temporary placeholders, replaced by Tasks 18 and 19.

@Composable
fun FirstRunScreen(launcher: LauncherController, onEditLogin: () -> Unit, onDone: () -> Unit) {
    FwPage("Set up", onDone) { Text("First-run setup is being built.", style = Fw.Text) }
}

@Composable
fun AddonsRoute(launcher: LauncherController, onBack: () -> Unit) {
    FwPage("Addons", onBack) { Text("The addon browser is being built.", style = Fw.Text) }
}
