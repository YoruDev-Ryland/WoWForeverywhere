package app.gamenative.wow.ui.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.wow.addons.AddonFlavor
import app.gamenative.wow.addons.AddonListing
import app.gamenative.wow.ui.FwPage
import app.gamenative.wow.ui.LauncherController
import app.gamenative.wow.ui.components.FwButton
import app.gamenative.wow.ui.components.FwButtonKind
import app.gamenative.wow.ui.components.FwDialog
import app.gamenative.wow.ui.components.FwRow
import app.gamenative.wow.ui.theme.Fw
import java.io.File

@Composable
fun AddonsRoute(launcher: LauncherController, addons: AddonsController?, onBack: () -> Unit) {
    if (addons == null) {
        FwPage("Addons", onBack) {
            Text("Install or find WoW Forever first. Addons go into the game's folder.", style = Fw.Text)
        }
        return
    }
    AddonsScreen(addons, onBack)
}

@Composable
fun AddonsScreen(c: AddonsController, onBack: () -> Unit) {
    var showUrlDialog by remember { mutableStateOf(false) }
    LaunchedEffect(c) { if (c.installed == null) c.refreshInstalled() }

    Column(Modifier.fillMaxSize().background(Fw.Ground)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FwButton("Back", onBack, kind = FwButtonKind.Ghost)
            Segment("Installed (${c.installed?.size ?: "…"})", c.mode == AddonsController.Mode.INSTALLED) { c.switchMode(AddonsController.Mode.INSTALLED) }
            Segment("Browse", c.mode == AddonsController.Mode.BROWSE) { c.switchMode(AddonsController.Mode.BROWSE) }
            SearchField(c.query, c::search, Modifier.weight(1f))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Segment(if (c.includeClassic) "Forever + Classic" else "Forever only", false, c::toggleClassic)
            Segment(c.source?.let(::sourceName) ?: "All sources", false, c::cycleSource)
            Box(Modifier.weight(1f))
            FwButton("Install from link", { showUrlDialog = true }, kind = FwButtonKind.Ghost)
            c.updates?.takeIf { it > 0 }?.let { FwButton("Update all ($it)", c::updateAll, enabled = c.working == null) }
        }
        (c.working ?: c.message)?.let {
            Text(it, style = Fw.Secondary.copy(color = if (c.working != null) Fw.Brass else Fw.Ash), modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        HorizontalDivider(color = Fw.Rule)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            var detailOpen by remember { mutableStateOf(false) }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    AddonList(c, Modifier.weight(1.25f).fillMaxHeight(), onOpen = {})
                    VerticalDivider(color = Fw.Rule)
                    c.selected?.let { Detail(c, it, Modifier.weight(1f).fillMaxHeight()) } ?: Box(Modifier.weight(1f))
                }
            } else {
                AddonList(c, Modifier.fillMaxSize(), onOpen = { detailOpen = true })
                val sel = c.selected
                if (detailOpen && sel != null) {
                    Column(Modifier.fillMaxSize().background(Fw.Ground)) {
                        FwButton("Back to list", { detailOpen = false }, kind = FwButtonKind.Ghost, modifier = Modifier.padding(16.dp))
                        Detail(c, sel, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    if (showUrlDialog) {
        var url by remember { mutableStateOf("") }
        FwDialog(
            title = "Install from a link",
            message = "Paste a GitHub repository link, or a direct link to an addon .zip.",
            confirm = "Install",
            onConfirm = {
                showUrlDialog = false
                if (url.isNotBlank()) c.installFromUrl(url)
            },
            onDismiss = { showUrlDialog = false },
        ) {
            OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("Link") }, textStyle = Fw.Text)
        }
    }
}

@Composable
private fun AddonList(c: AddonsController, modifier: Modifier, onOpen: () -> Unit) {
    Box(modifier) {
        when {
            c.loading -> Text("Searching…", style = Fw.Secondary, modifier = Modifier.padding(20.dp))
            c.listings.isEmpty() -> Text(
                when (c.mode) {
                    AddonsController.Mode.INSTALLED -> if (c.installed == null) "Reading your AddOns folder…" else "No addons installed yet. Switch to Browse to find some."
                    AddonsController.Mode.BROWSE -> "Nothing found. Try another name, or include Classic addons."
                },
                style = Fw.Secondary,
                modifier = Modifier.padding(20.dp),
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(c.listings, key = { "${it.remote.provider}:${it.remote.projectId}" }) { listing ->
                    FwRow(
                        selected = c.selected == listing,
                        onClick = {
                            c.selected = listing
                            onOpen()
                        },
                        onFocus = { if (c.selected != listing) c.selected = listing },
                    ) {
                        FlavorMark(listing.bestFlavor)
                        Column(Modifier.weight(1f)) {
                            Text(listing.remote.name, style = Fw.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    listing.sources.joinToString(" · ", transform = ::sourceName).takeIf { it.isNotBlank() },
                                    listing.remote.downloads.takeIf { it > 0 }?.let { "${compact(it)} downloads" },
                                ).joinToString(" · "),
                                style = Fw.Data,
                                maxLines = 1,
                            )
                        }
                        val (state, color) = when {
                            listing.updateAvailable -> "Update" to Fw.Warn
                            listing.installed != null -> "Installed" to Fw.Ok
                            else -> "Get" to Fw.Ash
                        }
                        Text(state, style = Fw.Data.copy(color = color))
                    }
                    HorizontalDivider(color = Fw.Rule)
                }
            }
        }
    }
}

@Composable
private fun Detail(c: AddonsController, listing: AddonListing, modifier: Modifier) {
    val remote = listing.remote
    val file = remote.latest[listing.bestFlavor] ?: remote.latest.values.firstOrNull()
    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FlavorMark(listing.bestFlavor, size = 40)
            Text(remote.name, style = Fw.Title)
        }
        Text(flavorLabel(listing.bestFlavor), style = Fw.Secondary.copy(color = if (listing.bestFlavor == AddonFlavor.FOREVER) Fw.Brass else Fw.Ash))
        if (remote.summary.isNotBlank()) Text(remote.summary, style = Fw.Text)
        Facts(
            listOfNotNull(
                remote.author.takeIf { it.isNotBlank() }?.let { "By" to it },
                file?.let { "Latest" to it.version },
                listing.installed?.version?.let { "Installed" to it },
                "Source" to listing.sources.joinToString(", ", transform = ::sourceName).ifBlank { "This device" },
                file?.dependencies?.takeIf { it.isNotEmpty() }?.let { "Also installs" to "${it.size} required libraries" },
                listing.installed?.folders?.let { "Folders" to it.joinToString(", ") },
            ),
        )
        remote.websiteUrl?.let { Text(it, style = Fw.Data) }
        if (file != null && !file.installable) {
            Text("The author doesn't allow downloads through other apps. Get it from the website above.", style = Fw.Secondary.copy(color = Fw.Warn))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val idle = c.working == null
            when {
                listing.installed == null -> FwButton("Install", { c.install(listing) }, enabled = idle && file?.installable == true)
                listing.updateAvailable -> FwButton(
                    if (listing.installed.flavor == AddonFlavor.CLASSIC && listing.bestFlavor == AddonFlavor.FOREVER) "Switch to Forever version" else "Update",
                    { c.update(listing) },
                    enabled = idle,
                )
                else -> FwButton("Installed", {}, enabled = false)
            }
            if (listing.installed != null) FwButton("Remove", { c.remove(listing) }, kind = FwButtonKind.Danger, enabled = idle)
        }
    }
}

@Composable
private fun Facts(rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { (k, v) ->
            Row {
                Text(k, style = Fw.Secondary, modifier = Modifier.width(110.dp))
                Text(v, style = Fw.Data.copy(color = Fw.Bone))
            }
        }
    }
}

/** A toggle-style ghost button; the current choice gets a raised fill and a brass underline. */
@Composable
private fun Segment(text: String, on: Boolean, onClick: () -> Unit) {
    Box {
        FwButton(text, onClick, kind = FwButtonKind.Ghost, modifier = if (on) Modifier.background(Fw.Stone2) else Modifier)
        if (on) {
            Box(Modifier.matchParentSize()) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Fw.Brass))
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    Box(modifier.widthIn(min = 160.dp).border(1.dp, Fw.Rule, Fw.Shape).padding(horizontal = 12.dp, vertical = 12.dp)) {
        if (value.isEmpty()) Text("Search Forever addons…", style = Fw.Text.copy(color = Fw.Ash), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicTextField(value, onChange, singleLine = true, textStyle = Fw.Text, cursorBrush = SolidColor(Fw.Brass), modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Forever vs Classic marker. The shape carries the meaning (solid square vs open circle), not only the
 * colour. Classic uses "CL" in the body face: a serif "C" in a circle reads as a copyright sign.
 */
@Composable
fun FlavorMark(flavor: AddonFlavor, size: Int = 28) {
    val base = Modifier.size(size.dp)
    when (flavor) {
        AddonFlavor.FOREVER -> Box(base.background(Fw.Brass, Fw.Shape), contentAlignment = Alignment.Center) {
            Text("F", style = Fw.Title.copy(fontSize = (size * 0.62f).sp, lineHeight = (size * 0.62f).sp, color = Fw.BrassInk))
        }
        else -> Box(base.border(2.dp, Fw.Ash, CircleShape), contentAlignment = Alignment.Center) {
            Text(
                if (flavor == AddonFlavor.CLASSIC) "CL" else "?",
                style = Fw.Label.copy(fontSize = (size * 0.3f).sp, lineHeight = (size * 0.3f).sp, letterSpacing = 0.sp, color = Fw.Ash),
            )
        }
    }
}

fun flavorLabel(flavor: AddonFlavor) = when (flavor) {
    AddonFlavor.FOREVER -> "Made for WoW Forever"
    AddonFlavor.CLASSIC -> "Made for Classic. It may not work in Forever."
    AddonFlavor.OTHER -> "No game version listed. It may not work in Forever."
}

private fun sourceName(id: String) = when (id) {
    "curseforge" -> "CurseForge"
    "wago" -> "Wago"
    "wowinterface" -> "WoWInterface"
    "github" -> "GitHub"
    "local" -> "This device"
    else -> id
}

private fun compact(n: Long) = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.0fK".format(n / 1_000.0)
    else -> n.toString()
}

internal fun addonsControllerFor(root: String, product: app.gamenative.wow.product.WowProduct?, scope: kotlinx.coroutines.CoroutineScope): AddonsController? =
    product?.let { AddonsController(File(root), it, scope) }
