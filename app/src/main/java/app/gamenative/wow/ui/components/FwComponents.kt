package app.gamenative.wow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.wow.ui.theme.Fw

/** Shared focus-aware border: brass and thick when focused, a hairline otherwise. */
@Composable
private fun Modifier.fwFrame(interaction: MutableInteractionSource, base: Color = Fw.Rule): Modifier {
    val focused by interaction.collectIsFocusedAsState()
    return border(if (focused) 3.dp else 1.dp, if (focused) Fw.Brass else base, Fw.Shape)
}

/** A large stone tile: the home screen's building block. [footer] sits at the bottom edge. */
@Composable
fun FwTile(
    title: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    titleStyle: androidx.compose.ui.text.TextStyle = Fw.Title,
    footer: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .background(Fw.Stone, Fw.Shape)
            .fwFrame(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
            Column {
                Text(title, style = titleStyle)
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
            }
            footer?.invoke()
        }
        badge?.let {
            Text(
                it,
                style = Fw.Data.copy(color = Fw.BrassInk, fontSize = 13.sp),
                modifier = Modifier.align(Alignment.TopEnd).background(Fw.Warn).padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

/** The brass "press here" bar used inside the Play tile. Not focusable itself: the tile is. */
@Composable
fun FwActionBar(text: String, enabled: Boolean = true) {
    Box(
        Modifier.background(if (enabled) Fw.Brass else Fw.Stone2, Fw.Shape).padding(horizontal = 36.dp, vertical = 14.dp),
    ) {
        Text(text.uppercase(), style = Fw.Label.copy(color = if (enabled) Fw.BrassInk else Fw.Ash, fontSize = 18.sp, letterSpacing = 3.sp))
    }
}

enum class FwButtonKind { Primary, Ghost, Danger }

@Composable
fun FwButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: FwButtonKind = FwButtonKind.Primary,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val (bg, fg, edge) = when (kind) {
        FwButtonKind.Primary -> Triple(Fw.Brass, Fw.BrassInk, Fw.Brass)
        FwButtonKind.Ghost -> Triple(Color.Transparent, Fw.Bone, Fw.Rule)
        FwButtonKind.Danger -> Triple(Color.Transparent, Fw.Warn, Fw.Warn)
    }
    Box(
        modifier
            .heightIn(min = 48.dp)
            .background(if (enabled) bg else Fw.Stone2, Fw.Shape)
            .border(if (focused) 3.dp else 1.dp, if (focused) Fw.Bone else if (enabled) edge else Fw.Rule, Fw.Shape)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = Fw.Label.copy(color = if (enabled) fg else Fw.Ash))
    }
}

/** A selectable list row with the same focus treatment as tiles. */
@Composable
fun FwRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    if (focused && onFocus != null) onFocus()
    Row(
        modifier
            .fillMaxWidth()
            .background(if (selected || focused) Fw.Stone2 else Color.Transparent)
            .border(if (focused) 3.dp else 0.dp, if (focused) Fw.Brass else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun FwTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(PaddingValues(horizontal = 20.dp, vertical = 14.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FwButton("Back", onBack, kind = FwButtonKind.Ghost)
        Text(title, style = Fw.Title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun FwProgress(fraction: Float?, modifier: Modifier = Modifier) {
    if (fraction == null) {
        LinearProgressIndicator(modifier.fillMaxWidth(), color = Fw.Brass, trackColor = Fw.Stone2)
    } else {
        LinearProgressIndicator(progress = { fraction }, modifier = modifier.fillMaxWidth(), color = Fw.Brass, trackColor = Fw.Stone2)
    }
}

@Composable
fun FwDialog(
    title: String,
    message: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String = "Cancel",
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = Fw.Title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message, style = Fw.Text.copy(color = Fw.Ash))
                extra()
            }
        },
        confirmButton = { FwButton(confirm, onConfirm) },
        dismissButton = { FwButton(dismiss, onDismiss, kind = FwButtonKind.Ghost) },
        containerColor = Fw.Stone,
        shape = Fw.Shape,
    )
}
