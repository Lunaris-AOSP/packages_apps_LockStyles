/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.lockstyles.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.lunaris.clockmaker.ClockDesign
import org.lunaris.clockmaker.ClockFonts
import org.lunaris.clockmaker.DynamicClockSettings
import org.lunaris.lockstyles.ClockMakerStore
import org.lunaris.lockstyles.ClockStyle
import org.lunaris.lockstyles.CustomClocks
import org.lunaris.lockstyles.DynamicClockConfiguration
import org.lunaris.lockstyles.GalleryWallpaper
import org.lunaris.lockstyles.R
import org.lunaris.lockstyles.settings.ui.components.squishable
import org.lunaris.lockstyles.settings.ui.pickTile
import java.util.UUID

internal val dynamicClockSaver = Saver<DynamicClockConfiguration, String>(
    save = { it.toJson().toString() },
    restore = { runCatching { DynamicClockConfiguration.fromJson(JSONObject(it)) }.getOrNull() },
)
private val clockLayoutSaver = Saver<ClockStyle, String>(
    save = { it.toJson().toString() },
    restore = { runCatching { ClockStyle.fromJson(JSONObject(it)) }.getOrNull() },
)

@Composable
fun DynamicClockScreen(
    initial: DynamicClockConfiguration,
    wallpaper: GalleryWallpaper,
    draftKey: String,
    allowImport: Boolean,
    onCancel: () -> Unit,
    onSave: (DynamicClockConfiguration) -> Unit,
    initialSize: String? = null,
) {
    var configuration by rememberSaveable(draftKey, stateSaver = dynamicClockSaver) { mutableStateOf(initial) }
    var editingSize by rememberSaveable(draftKey) {
        mutableStateOf(initialSize?.takeIf { it == DynamicClockSettings.SMALL || it == DynamicClockSettings.LARGE })
    }
    val size = editingSize
    if (size != null) {
        val small = size == DynamicClockSettings.SMALL
        ClockLayoutEditor(if (small) configuration.small else configuration.large, small,
            wallpaper, "$draftKey:$size", allowImport,
            largeClock = configuration.large.takeIf { small },
            onCancel = { if (initialSize != null) onCancel() else editingSize = null },
            onSave = { clock ->
                configuration = if (small) configuration.copy(small = clock) else configuration.copy(large = clock)
                if (initialSize != null) onSave(configuration) else editingSize = null
            })
        return
    }
    BackHandler(onBack = onCancel)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ClockEditorHeader(stringResource(R.string.dynamic_clock_title), onCancel) { onSave(configuration) }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SwitchCard(stringResource(R.string.dynamic_clock_title), configuration.enabled,
                    { configuration = configuration.copy(enabled = it) },
                    subtitle = stringResource(R.string.dynamic_clock_summary), icon = Icons.Rounded.Schedule)
                ClockLayoutCard(configuration.large, false, wallpaper) { editingSize = DynamicClockSettings.LARGE }
                ClockLayoutCard(configuration.small, true, wallpaper) { editingSize = DynamicClockSettings.SMALL }
                Text(stringResource(R.string.dynamic_clock_aod_summary),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ClockLayoutCard(clock: ClockStyle, small: Boolean, wallpaper: GalleryWallpaper, onEdit: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    Surface(
        onClick = onEdit,
        interactionSource = interactions,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().squishable(interactions),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.width(92.dp).height(160.dp).clip(MaterialTheme.shapes.large)) {
                ClockLayoutPreview(clock, wallpaper)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (small) R.string.dynamic_clock_small_title else R.string.dynamic_clock_large_title),
                    style = MaterialTheme.typography.titleMedium)
                Text(stringResource(if (small) R.string.dynamic_clock_small_summary else R.string.dynamic_clock_large_summary),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        stringResource(R.string.dynamic_clock_edit),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp).size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ClockLayoutEditor(initial: ClockStyle, small: Boolean, wallpaper: GalleryWallpaper,
    draftKey: String, allowImport: Boolean, largeClock: ClockStyle?,
    onCancel: () -> Unit, onSave: (ClockStyle) -> Unit) {
    val context = LocalContext.current
    val makerStore = remember(context) { ClockMakerStore(context) }
    var clock by rememberSaveable(draftKey, stateSaver = clockLayoutSaver) {
        mutableStateOf(initial.copy(small = small, tuning = initial.tuning ?: CustomClocks.Tuning(),
            showOnHomeScreen = false, homeLayout = null))
    }
    var maker by rememberSaveable(draftKey) { mutableStateOf(false) }
    if (maker) {
        ClockMakerScreen(clock.copy(face = ClockDesign.STYLE), wallpaper, draftKey, allowImport,
            onCancel = { maker = false }, onSave = {
                clock = it.copy(small = small, showOnHomeScreen = false, homeLayout = null)
                maker = false
            }, layoutSize = small)
        return
    }
    BackHandler(onBack = onCancel)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ClockEditorHeader(stringResource(if (small) R.string.dynamic_clock_small_title
                else R.string.dynamic_clock_large_title), onCancel) { onSave(clock) }
            if (small && largeClock != null && largeClock.face == ClockDesign.STYLE && largeClock.design != null) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveButton(stringResource(R.string.dynamic_clock_use_big), onClick = {
                        clock = largeClock.copy(small = true,
                            design = largeClock.design?.copy(id = UUID.randomUUID().toString()),
                            tuning = largeClock.tuning ?: CustomClocks.Tuning(),
                            showOnHomeScreen = false, homeLayout = null)
                        makerStore.clearDraft(draftKey)
                        maker = true
                    })
                    Text(stringResource(R.string.dynamic_clock_use_big_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(Modifier.fillMaxWidth().weight(0.42f).padding(12.dp), contentAlignment = Alignment.Center) {
                val aspect = CustomClocks.geometry(LocalContext.current).aspect
                Box(Modifier.aspectRatio(aspect, matchHeightConstraintsFirst = true).clip(MaterialTheme.shapes.large)) {
                    ClockLayoutPreview(clock, wallpaper)
                }
            }
            Box(Modifier.fillMaxWidth().weight(0.58f)) {
                ClockSheet(clock, clock.tuning ?: CustomClocks.Tuning(), wallpaper, allowImport,
                    onChange = { clock = it.copy(small = small,
                        design = it.design ?: if (it.face == ClockDesign.STYLE) ClockDesign() else null) },
                    onTuning = { clock = clock.copy(tuning = it) }, onMaker = { maker = true }, layoutSize = small)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClockEditorHeader(title: String, onBack: () -> Unit, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(android.R.string.cancel))
        }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        ExpressiveButton(stringResource(R.string.gallery_done), onSave)
    }
}

@Composable
internal fun ClockLayoutPreview(clock: ClockStyle, wallpaper: GalleryWallpaper) {
    LockScreenPreviewLayout(Modifier.fillMaxSize(), depthWallpaper = wallpaper, showDepth = true) {
        LockScreenWallpaper(wallpaper, Modifier.fillMaxSize(), live = false)
        CustomClockLayer(clock, wallpaper = wallpaper)
        DepthWallpaperLayer(wallpaper)
    }
}

@Composable
internal fun ClockLayoutFontPicker(clock: ClockStyle, allowImport: Boolean, onChange: (ClockStyle) -> Unit) {
    val context = LocalContext.current
    val store = remember { ClockMakerStore(context) }
    val scope = rememberCoroutineScope()
    var revision by remember { mutableStateOf(0) }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val fonts by produceState(emptyList<Pair<String?, String>>(), revision) {
        value = withContext(Dispatchers.IO) {
            listOf(null to context.getString(R.string.clock_font_default)) +
                ClockFonts.families.map { it to it } + ClockFonts.bundled(context) +
                store.fonts.map { it.family to it.name }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && allowImport) scope.launch {
            importing = true
            val imported = withContext(Dispatchers.IO) { runCatching { store.importFont(uri) }.getOrNull() }
            importing = false
            error = imported == null
            if (imported != null) {
                revision++
                onChange(clock.copy(font = imported.family))
            }
        }
    }
    Column(Modifier.padding(horizontal = LocalCardInset.current), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.gallery_clock_font))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(fonts, key = { it.first ?: "default" }) { (family, name) ->
                val face by produceState<android.graphics.Typeface?>(null, family) {
                    if (family != null) value = withContext(Dispatchers.IO) { ClockFonts.load(context, context.userId, family) }
                }
                Column(Modifier.width(104.dp).clip(MaterialTheme.shapes.large)
                    .selectable(clock.font == family, role = Role.RadioButton,
                        onClick = { onChange(clock.copy(font = family)) }), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(88.dp).pickTile(clock.font == family), contentAlignment = Alignment.Center) {
                        Text("12", fontFamily = face?.let { FontFamily(it) }, style = MaterialTheme.typography.headlineLarge)
                    }
                    Text(name, maxLines = 2, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (allowImport) ExpressiveButton(stringResource(R.string.dynamic_clock_import_font),
            enabled = !importing, onClick = { importer.launch(arrayOf("font/*", "application/octet-stream")) })
        if (error) Text(stringResource(R.string.dynamic_clock_font_failed), color = MaterialTheme.colorScheme.error)
    }
}
