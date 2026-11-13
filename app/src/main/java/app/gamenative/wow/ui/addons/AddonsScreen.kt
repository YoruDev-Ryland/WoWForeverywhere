package app.gamenative.wow.ui.addons

import androidx.compose.ui.res.stringResource
import app.gamenative.R
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
import app.gamenative.wow.addons.AddonCategory
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.wow.addons.AddonFlavor
import app.gamenative.wow.product.GameVersion
import app.gamenative.wow.addons.AddonListing
import app.gamenative.wow.ui.FwPage
import app.gamenative.wow.ui.LauncherController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import app.gamenative.wow.ui.components.ArtCache
import app.gamenative.wow.ui.components.ArtImage
import app.gamenative.wow.ui.components.GameIcon
import app.gamenative.wow.ui.components.icon
import app.gamenative.wow.ui.components.Links
import app.gamenative.wow.ui.components.ForeverArt
import app.gamenative.wow.ui.components.FwButton
import app.gamenative.wow.ui.components.FwChip
import app.gamenative.wow.ui.components.FwButtonKind
import app.gamenative.wow.ui.components.FwDialog
import app.gamenative.wow.ui.components.FwRow
import app.gamenative.wow.ui.theme.Fw
import java.io.File

@Composable
fun AddonsRoute(launcher: LauncherController, addons: AddonsController?, onBack: () -> Unit) {
    if (addons == null) {
        FwPage(stringResource(R.string.fw_addons), onBack) {
            Text(stringResource(R.string.fw_addons_require_game, launcher.version.displayName), style = Fw.Text)
        }
        return
    }
    AddonsScreen(addons, onBack)
}

/**
 * The addon browser. With [onSettings], it's the app's main screen in addon manager mode: a Settings
 * button takes the place of Back.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AddonsScreen(c: AddonsController, onBack: () -> Unit, onSettings: (() -> Unit)? = null) {
    val localSource = stringResource(R.string.fw_this_device)
    var showUrlDialog by remember { mutableStateOf(false) }
    // One bar needs room for the buttons and a usable search box; a 4:3 handheld (about 620dp) doesn't have it.
    val wideBar = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 760
    LaunchedEffect(c) { if (c.installed == null) c.refreshInstalled() }
    val zipPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) c.installFromFile(uri) }
    // Some file managers label zips as generic binary files, so that type is offered too.
    val pickZip = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }

    Column(Modifier.fillMaxSize().background(Fw.Ground)) {
        val back: @Composable () -> Unit = {
            if (onSettings != null) FwButton(stringResource(R.string.fw_settings), onSettings, kind = FwButtonKind.Ghost) else FwButton(stringResource(R.string.fw_back_2), onBack, kind = FwButtonKind.Ghost)
        }
        val modes: @Composable () -> Unit = {
            FwChip(stringResource(R.string.fw_addons_installed_count, c.installed?.size?.toString() ?: "…"), c.mode == AddonsController.Mode.INSTALLED, { c.switchMode(AddonsController.Mode.INSTALLED) })
            FwChip(stringResource(R.string.fw_browse), c.mode == AddonsController.Mode.BROWSE, { c.switchMode(AddonsController.Mode.BROWSE) })
        }
        val updateAll: @Composable () -> Unit = {
            c.updates?.takeIf { it > 0 }?.let { FwButton(stringResource(R.string.fw_update_all_count, it), c::updateAll, enabled = c.working == null) }
        }
        val addAddon: @Composable () -> Unit = { FwButton(stringResource(R.string.fw_add_addon), { showUrlDialog = true }, kind = FwButtonKind.Ghost) }
        if (wideBar) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                back()
                modes()
                SearchField(c.query, c::search, searchHint(c.version), Modifier.weight(1f))
                updateAll()
                addAddon()
            }
        } else {
            // Narrower screens: navigation on top (wrapping if even that doesn't fit), then search with Add addon.
            androidx.compose.foundation.layout.FlowRow(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Each item centred in its line (FlowRow's itemVerticalAlignment needs Compose 1.8; release is on 1.7)
                Box(Modifier.align(Alignment.CenterVertically)) { back() }
                Box(Modifier.align(Alignment.CenterVertically)) { modes() }
                Box(Modifier.align(Alignment.CenterVertically)) { updateAll() }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SearchField(c.query, c::search, searchHint(c.version), Modifier.weight(1f))
                addAddon()
            }
        }
        // One scrolling row of filters: game version, source, then categories.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 10.dp),
        ) {
            // Only WoW Forever has a fallback version (Classic Era) to include or leave out.
            if (c.version == GameVersion.FOREVER) item { FwChip(if (c.includeClassic) "Forever + Classic" else stringResource(R.string.fw_forever_only), false, c::toggleClassic) }
            item { FwChip(c.source?.let { sourceName(it, localSource) } ?: stringResource(R.string.fw_all_sources), false, c::cycleSource) }
            item { Box(Modifier.width(1.dp).height(42.dp).background(Fw.Rule)) }
            item { FwChip(stringResource(R.string.fw_all_categories), c.category == null, { c.selectCategory(null) }) }
            items(AddonCategory.entries) { cat -> FwChip(categoryLabel(cat), c.category == cat, { c.selectCategory(cat) }) }
        }
        (c.working ?: c.message)?.let {
            Text(it, style = Fw.Secondary.copy(color = if (c.working != null) Fw.Brass else Fw.Ash), modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            val wide = maxWidth >= 600.dp
            var detailOpen by remember { mutableStateOf(false) }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    AddonList(c, Modifier.weight(1.25f).fillMaxHeight(), onOpen = {})
                    Box(Modifier.width(14.dp))
                    c.selected?.let { Detail(c, it, Modifier.weight(1f).fillMaxHeight()) } ?: Box(Modifier.weight(1f))
                }
            } else {
                AddonList(c, Modifier.fillMaxSize(), onOpen = { detailOpen = true })
                val sel = c.selected
                if (detailOpen && sel != null) {
                    Column(Modifier.fillMaxSize().background(Fw.Ground)) {
                        FwButton(stringResource(R.string.fw_back_to_list), { detailOpen = false }, kind = FwButtonKind.Ghost, modifier = Modifier.padding(16.dp))
                        Detail(c, sel, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    if (showUrlDialog) {
        var url by remember { mutableStateOf("") }
        FwDialog(
            title = stringResource(R.string.fw_add_an_addon),
            message = stringResource(R.string.fw_add_addon_explanation),
            confirm = stringResource(R.string.fw_install),
            onConfirm = {
                showUrlDialog = false
                if (url.isNotBlank()) c.installFromUrl(url)
            },
            onDismiss = { showUrlDialog = false },
        ) {
            OutlinedTextField(url, { url = it }, singleLine = true, label = { Text(stringResource(R.string.fw_link)) }, textStyle = Fw.Text)
            FwButton(stringResource(R.string.fw_choose_a_zip_file), {
                showUrlDialog = false
                pickZip()
            }, kind = FwButtonKind.Ghost, enabled = c.working == null)
        }
    }
}

@Composable
private fun AddonList(c: AddonsController, modifier: Modifier, onOpen: () -> Unit) {
    val localSource = stringResource(R.string.fw_this_device)
    Box(modifier) {
        when {
            c.loading -> Text(stringResource(R.string.fw_searching), style = Fw.Secondary, modifier = Modifier.padding(20.dp))
            c.listings.isEmpty() -> Text(
                when (c.mode) {
                    AddonsController.Mode.INSTALLED -> if (c.installed == null) stringResource(R.string.fw_reading_your_addons_folder) else stringResource(R.string.fw_no_addons_installed_yet_switch_to_browse)
                    AddonsController.Mode.BROWSE ->
                        if (c.version == GameVersion.FOREVER) stringResource(R.string.fw_nothing_found_try_another_name_or_include) else stringResource(R.string.fw_nothing_found_try_another_name)
                },
                style = Fw.Secondary,
                modifier = Modifier.padding(20.dp),
            )
            else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(c.listings, key = { "${it.remote.provider}:${it.remote.projectId}" }) { listing ->
                    FwRow(
                        selected = c.selected == listing,
                        onClick = {
                            c.selected = listing
                            onOpen()
                        },
                        onFocus = { if (c.selected != listing) c.selected = listing },
                    ) {
                        AddonIcon(listing, 44, c.version)
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(listing.remote.name, style = Fw.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                FlavorMark(listing.bestFlavor, c.version, size = 20)
                            }
                            Text(
                                listOfNotNull(
                                    listing.remote.categories.firstOrNull()?.let { categoryLabel(it) },
                                    listing.sources.joinToString(" · ", transform = { sourceName(it, localSource) }).takeIf { it.isNotBlank() },
                                    listing.remote.downloads.takeIf { it > 0 }?.let { stringResource(R.string.fw_downloads_count, compact(it)) },
                                ).joinToString(" · "),
                                style = Fw.Data,
                                maxLines = 1,
                            )
                        }
                        val (state, color) = when {
                            listing.updateAvailable -> stringResource(R.string.fw_update) to Fw.Warn
                            listing.installed != null -> stringResource(R.string.fw_installed) to Fw.Ok
                            else -> stringResource(R.string.fw_get) to Fw.Ash
                        }
                        Text(state, style = Fw.Data.copy(color = color))
                    }
                }
            }
        }
    }
}

@Composable
private fun Detail(c: AddonsController, listing: AddonListing, modifier: Modifier) {
    val localSource = stringResource(R.string.fw_this_device)
    val remote = listing.remote
    val file = remote.latest[listing.bestFlavor] ?: remote.latest.values.firstOrNull()
    LaunchedEffect(c.keyOf(listing)) { c.loadDescription(listing) }
    val description = c.descriptions[c.keyOf(listing)]
    Column(modifier.clip(Fw.TileShape).background(Fw.Stone).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(150.dp).background(Fw.Stone2)) {
            val banner = remote.screenshots.firstOrNull()
            if (banner != null) {
                ArtImage(banner, Modifier.fillMaxSize(), fitIfNarrowerThan = 640, keepOnDisk = false)
            } else {
                ArtImage(ForeverArt.INTERFACE, Modifier.fillMaxSize())
            }
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AddonIcon(listing, 56, c.version)
                Column(Modifier.weight(1f)) {
                    Text(remote.name, style = Fw.Title)
                    if (remote.author.isNotBlank()) Text(stringResource(R.string.fw_by_1_s, remote.author), style = Fw.Secondary)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlavorMark(listing.bestFlavor, c.version, size = 22)
                Text(flavorLabel(listing.bestFlavor, c.version), style = Fw.Secondary.copy(color = if (listing.bestFlavor == AddonFlavor.MAIN) Fw.Gem else Fw.Ash))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val idle = c.working == null
                when {
                    listing.installed == null -> FwButton(stringResource(R.string.fw_install), { c.install(listing) }, enabled = idle && file?.installable == true)
                    listing.updateAvailable -> FwButton(
                        if (listing.installed.flavor == AddonFlavor.FALLBACK && listing.bestFlavor == AddonFlavor.MAIN) stringResource(R.string.fw_switch_to_forever_version) else stringResource(R.string.fw_update),
                        { c.update(listing) },
                        enabled = idle,
                    )
                    else -> FwButton(stringResource(R.string.fw_installed), {}, enabled = false)
                }
                if (listing.installed != null) FwButton(stringResource(R.string.fw_remove), { c.remove(listing) }, kind = FwButtonKind.Danger, enabled = idle)
            }
            if (file != null && !file.installable) {
                Text(stringResource(R.string.fw_the_author_doesn_t_allow_downloads_through), style = Fw.Secondary.copy(color = Fw.Warn))
            }
            if (listing.pages.isNotEmpty()) {
                val context = LocalContext.current
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listing.pages.entries.take(3).forEach { (provider, url) ->
                        FwButton(
                            stringResource(R.string.fw_open_on_1_s, sourceName(provider, localSource)),
                            { Links.open(context, url) },
                            kind = FwButtonKind.Ghost,
                            icon = Icons.AutoMirrored.Filled.OpenInNew,
                        )
                    }
                }
            }
            if (remote.categories.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    remote.categories.take(3).forEach { cat ->
                        Text(categoryLabel(cat), style = Fw.Label.copy(fontSize = 12.sp, color = Fw.Ash), modifier = Modifier.background(Fw.Stone2, Fw.Pill).padding(horizontal = 12.dp, vertical = 5.dp))
                    }
                }
            }
            Text(
                when {
                    description != null -> description
                    c.keyOf(listing) in c.descriptions -> remote.summary.ifBlank { stringResource(R.string.fw_no_description) }
                    else -> remote.summary.ifBlank { stringResource(R.string.fw_loading_description) }
                },
                style = Fw.Text.copy(color = Fw.Bone),
                maxLines = 40,
                overflow = TextOverflow.Ellipsis,
            )
            if (remote.screenshots.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(remote.screenshots.drop(1).take(8)) { shot ->
                        Box(Modifier.size(width = 168.dp, height = 96.dp).clip(Fw.Shape).background(Fw.Stone2)) {
                            ArtImage(shot, Modifier.fillMaxSize(), fitIfNarrowerThan = 320, keepOnDisk = false)
                        }
                    }
                }
            }
            Facts(
                listOfNotNull(
                    file?.let { stringResource(R.string.fw_latest) to it.version },
                    listing.installed?.version?.let { stringResource(R.string.fw_installed) to it },
                    stringResource(R.string.fw_source) to listing.sources.joinToString(", ", transform = { sourceName(it, localSource) }).ifBlank { stringResource(R.string.fw_this_device) },
                    file?.dependencies?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.fw_also_installs) to stringResource(R.string.fw_required_libraries_count, it.size) },
                    listing.installed?.folders?.let { stringResource(R.string.fw_folders) to it.joinToString(", ") },
                ),
            )
        }
    }
}

/** The addon's own icon from its source; falls back to the Forever/Classic mark. */
@Composable
private fun AddonIcon(listing: AddonListing, size: Int, version: GameVersion) {
    val url = listing.remote.iconUrl
    Box(Modifier.size(size.dp).clip(Fw.Shape).background(Fw.Stone2), contentAlignment = Alignment.Center) {
        if (url == null) FlavorMark(listing.bestFlavor, version, size = (size * 0.6f).toInt()) else ArtImage(url, Modifier.fillMaxSize(), keepOnDisk = false)
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

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier) {
    Box(modifier.widthIn(min = 160.dp).background(Fw.Stone, Fw.Pill).padding(horizontal = 18.dp, vertical = 12.dp)) {
        if (value.isEmpty()) Text(hint, style = Fw.Text.copy(color = Fw.Ash), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicTextField(value, onChange, singleLine = true, textStyle = Fw.Text, cursorBrush = SolidColor(Fw.Brass), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun searchHint(version: GameVersion) = stringResource(R.string.fw_search_version_addons, version.displayName)

/**
 * Game version marker: Blizzard's round icon for the version an addon is made for ([version]'s own, or
 * Classic for Forever's fallback). Until it loads (or offline) a simple stand-in shows: a teal square for
 * the selected version, an outlined circle otherwise.
 */
@Composable
fun FlavorMark(flavor: AddonFlavor, version: GameVersion = GameVersion.FOREVER, size: Int = 28) {
    val icon = when (flavor) {
        AddonFlavor.MAIN -> version.icon
        AddonFlavor.FALLBACK -> GameIcon.CLASSIC
        AddonFlavor.OTHER -> null
    }
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.dp.roundToPx() }
    val image by produceState<ImageBitmap?>(null, icon, px) { value = icon?.let { ArtCache.loadSvg(context, it, px) } }
    val base = Modifier.size(size.dp)
    image?.let {
        Image(it, contentDescription = flavorLabel(flavor, version), modifier = base)
        return
    }
    when (flavor) {
        AddonFlavor.MAIN -> Box(base.background(Fw.Gem, Fw.Shape))
        else -> Box(base.border(2.dp, Fw.Ash, CircleShape))
    }
}

@Composable
fun flavorLabel(flavor: AddonFlavor, version: GameVersion = GameVersion.FOREVER) = when {
    version == GameVersion.FOREVER -> when (flavor) {
        AddonFlavor.MAIN -> stringResource(R.string.fw_addon_for_forever)
        AddonFlavor.FALLBACK -> stringResource(R.string.fw_addon_for_classic)
        AddonFlavor.OTHER -> stringResource(R.string.fw_addon_version_unknown_forever)
    }
    flavor == AddonFlavor.OTHER -> stringResource(R.string.fw_addon_version_unknown, version.displayName)
    else -> stringResource(R.string.fw_addon_for_version, version.displayName)
}

@Composable
private fun categoryLabel(category: AddonCategory): String = stringResource(when (category) {
    AddonCategory.QUESTS -> R.string.fw_category_quests
    AddonCategory.MAP -> R.string.fw_category_map
    AddonCategory.RAIDS -> R.string.fw_category_raids
    AddonCategory.COMBAT -> R.string.fw_category_combat
    AddonCategory.UNIT_FRAMES -> R.string.fw_category_unit_frames
    AddonCategory.ACTION_BARS -> R.string.fw_category_action_bars
    AddonCategory.BUFFS -> R.string.fw_category_buffs
    AddonCategory.BAGS -> R.string.fw_category_bags
    AddonCategory.AUCTION -> R.string.fw_category_auction
    AddonCategory.PROFESSIONS -> R.string.fw_category_professions
    AddonCategory.CHAT -> R.string.fw_category_chat
    AddonCategory.TOOLTIPS -> R.string.fw_category_tooltips
    AddonCategory.INTERFACE -> R.string.fw_category_interface
    AddonCategory.CLASS -> R.string.fw_category_class
    AddonCategory.PVP -> R.string.fw_category_pvp
    AddonCategory.PETS -> R.string.fw_category_pets
    AddonCategory.LIBRARIES -> R.string.fw_category_libraries
    AddonCategory.OTHER -> R.string.fw_category_other
})

private fun sourceName(id: String, localSource: String) = when (id) {
    "curseforge" -> "CurseForge"
    "wago" -> "Wago"
    "wowinterface" -> "WoWInterface"
    "github" -> "GitHub"
    "local" -> localSource
    else -> id
}

private fun compact(n: Long) = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.0fK".format(n / 1_000.0)
    else -> n.toString()
}

internal fun addonsControllerFor(root: String, product: app.gamenative.wow.product.WowProduct?, scope: kotlinx.coroutines.CoroutineScope, context: android.content.Context): AddonsController? =
    product?.let { AddonsController(File(root), it, scope, context) }
