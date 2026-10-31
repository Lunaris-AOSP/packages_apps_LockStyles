/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.lunaris.lockstyles.ui

import android.content.Context
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lunaris.clockmaker.ClockDesign
import org.lunaris.clockmaker.ClockDesignPositioning
import org.lunaris.clockmaker.ClockElement
import org.lunaris.clockmaker.ClockElementKind
import org.lunaris.clockmaker.ClockFonts
import org.lunaris.clockmaker.ClockGlass
import org.lunaris.clockmaker.ClockImageFit
import org.lunaris.clockmaker.ClockImageShapes
import org.lunaris.lockstyles.ClockImageStore
import org.lunaris.lockstyles.ClockMakerStore
import org.lunaris.lockstyles.ClockStyle
import org.lunaris.lockstyles.CustomClocks
import org.lunaris.lockstyles.GalleryWallpaper
import org.lunaris.lockstyles.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockMakerScreen(initial: ClockStyle, wallpaper: GalleryWallpaper, draftKey: String,
    allowImport: Boolean, onCancel: () -> Unit, onSave: (ClockStyle) -> Unit) {
    val context = LocalContext.current
    val store = remember { ClockMakerStore(context) }
    val imageStore = remember { ClockImageStore(context) }
    val restored = remember(draftKey, initial) { store.draft(draftKey) ?: initial }
    var design by remember(draftKey, initial) { mutableStateOf(restored.design ?: ClockDesign()) }
    var tuning by remember(draftKey, initial) { mutableStateOf(restored.tuning ?: CustomClocks.Tuning.load(context)) }
    var color by remember(draftKey, initial) { mutableStateOf(restored.color) }
    var selectedId by remember(draftKey, initial) { mutableStateOf(design.elements.firstOrNull()?.id) }
    var tab by remember(draftKey) { mutableStateOf(0) }
    var dialog by remember(draftKey) { mutableStateOf<String?>(null) }
    var undo by remember(draftKey) { mutableStateOf(emptyList<ClockDesign>()) }
    var redo by remember(draftKey) { mutableStateOf(emptyList<ClockDesign>()) }
    var dragging by remember(draftKey) { mutableStateOf(false) }
    var finished by remember(draftKey) { mutableStateOf(false) }
    var fonts by remember(draftKey) { mutableStateOf(store.fonts) }
    val bundledFonts by produceState(emptyList<Pair<String, String>>()) {
        value = withContext(Dispatchers.IO) { bundledFontChoices(context) }
    }
    var library by remember(draftKey) { mutableStateOf(store.designs) }
    var importing by remember(draftKey) { mutableStateOf(false) }
    var fontTarget by rememberSaveable(draftKey) { mutableStateOf<String?>(null) }
    var error by remember(draftKey) { mutableStateOf(false) }
    var removeFont by remember(draftKey) { mutableStateOf<ClockMakerStore.ImportedFont?>(null) }
    var importingImage by remember(draftKey) { mutableStateOf(false) }
    var imageError by remember(draftKey) { mutableStateOf(false) }
    var imageTarget by rememberSaveable(draftKey) { mutableStateOf<String?>(null) }
    var imageDesign by rememberSaveable(draftKey) { mutableStateOf<String?>(null) }
    var previewAod by remember(draftKey) { mutableStateOf(false) }
    var fullScreenEditor by rememberSaveable(draftKey) { mutableStateOf(false) }
    var positioning by remember(draftKey) { mutableStateOf<ClockDesignPositioning?>(null) }
    val scope = rememberCoroutineScope()
    val clock = initial.copy(face = ClockDesign.STYLE, design = design, tuning = tuning, color = color)
    val latestClock by rememberUpdatedState(clock)
    fun change(value: ClockDesign, record: Boolean = true) {
        if (value == design) return
        if (record) undo = (undo + design).takeLast(50)
        redo = emptyList()
        design = value
    }
    LaunchedEffect(design.id, design.elements.map { it.id }) {
        if (selectedId != null && design.elements.none { it.id == selectedId }) {
            selectedId = design.elements.firstOrNull()?.id
        } else if (selectedId == null && design.elements.isNotEmpty()) {
            selectedId = design.elements.first().id
        }
    }
    fun edit(element: ClockElement) = change(design.copy(elements = design.elements.map {
        if (it.id == element.id) element else it
    }))
    fun cancel() {
        finished = true
        store.clearDraft(draftKey)
        onCancel()
    }
    BackHandler { cancel() }
    LaunchedEffect(clock) {
        delay(300)
        if (!finished) store.saveDraft(draftKey, clock)
    }
    DisposableEffect(draftKey) {
        onDispose { if (!finished) store.saveDraft(draftKey, latestClock) }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && allowImport) {
            error = false
            importing = true
            scope.launch {
                val font = withContext(Dispatchers.IO) { runCatching { store.importFont(uri) }.getOrNull() }
                importing = false
                if (font == null) error = true else {
                    fonts = store.fonts
                    design.elements.firstOrNull { it.id == fontTarget }?.let { edit(it.copy(font = font.family)) }
                }
            }
        }
    }
    val imageImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && allowImport) {
            imageError = false
            importingImage = true
            val target = imageTarget
            val targetDesign = imageDesign
            val keepImages = (listOf(design) + undo + redo).flatMap { it.elements }.map { it.image }.toSet()
            scope.launch {
                val imported = withContext(Dispatchers.IO) { runCatching { imageStore.importImage(uri, keepImages) }.getOrNull() }
                importingImage = false
                if (imported == null) imageError = true else if (design.id == targetDesign) {
                    if (target == null && design.elements.size < ClockDesign.MAX_ELEMENTS) {
                        val element = ClockElement.create(ClockElementKind.IMAGE).copy(image = imported.id,
                            imageAspectRatio = (imported.width.toFloat() / imported.height).coerceIn(16f / 720, 360f / 16))
                            .resizeImage(width = 120)
                        change(design.copy(elements = listOf(element) + design.elements))
                        selectedId = element.id
                        tab = 1
                    } else {
                        design.elements.firstOrNull { it.id == target && it.kind == ClockElementKind.IMAGE }?.let { element ->
                            val ratio = (imported.width.toFloat() / imported.height)
                                .coerceIn(16f / 720, 360f / 16)
                            val replacement = element.copy(image = imported.id, imageAspectRatio = ratio)
                            edit(if (element.imageAspectLocked) replacement.resizeImage(width = element.imageWidth)
                            else replacement)
                        }
                    }
                }
            }
        }
    }
    fun chooseImage(target: String?) {
        imageTarget = target
        imageDesign = design.id
        imageImporter.launch(arrayOf("image/*"))
    }

    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val layoutSpatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntSize>()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
        .statusBarsPadding().navigationBarsPadding()) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { cancel() }) {
                        Icon(Icons.Rounded.Close, stringResource(android.R.string.cancel))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.clock_maker_title),
                            style = MaterialTheme.typography.titleLarge)
                        Text(design.name.trim().ifEmpty { stringResource(R.string.clock_maker_new) },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    ExpressiveButton(stringResource(R.string.clock_maker_save),
                        enabled = design.elements.isNotEmpty() && !importing && !importingImage,
                        icon = Icons.Rounded.Save, onClick = {
                            val saved = design.copy(name = design.name.trim().ifEmpty { "Custom clock" })
                            store.save(clock.copy(design = saved))
                            library = store.designs
                            finished = true
                            store.clearDraft(draftKey)
                            onSave(clock.copy(design = saved))
                        })
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    ExpressiveIconButton(Icons.AutoMirrored.Rounded.Undo,
                        stringResource(R.string.clock_maker_undo), enabled = undo.isNotEmpty(), onClick = {
                            redo = redo + design
                            design = undo.last()
                            undo = undo.dropLast(1)
                        })
                    ExpressiveIconButton(Icons.AutoMirrored.Rounded.Redo,
                        stringResource(R.string.clock_maker_redo), enabled = redo.isNotEmpty(), onClick = {
                            undo = undo + design
                            design = redo.last()
                            redo = redo.dropLast(1)
                        })
                    Spacer(Modifier.weight(1f))
                    ExpressiveIconButton(if (fullScreenEditor) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        stringResource(if (fullScreenEditor) R.string.clock_maker_editor_split
                            else R.string.clock_maker_editor_fullscreen),
                        onClick = { fullScreenEditor = !fullScreenEditor })
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val preview: @Composable (Modifier) -> Unit = { pane ->
                BoxWithConstraints(pane.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    val aspect = context.resources.displayMetrics.widthPixels.toFloat() / context.resources.displayMetrics.heightPixels
                    val previewWidth = minOf(maxWidth, maxHeight * aspect)
                    val previewShape = MaterialTheme.shapes.extraLarge
                    Box(Modifier.size(previewWidth, previewWidth / aspect)
                        .clip(previewShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), previewShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
                        if (!previewAod) LiveWallpaper(wallpaper, Modifier.fillMaxSize())
                        else {
                            Box(Modifier.fillMaxSize().background(Color.Black))
                            Surface(
                                color = Color.Black.copy(alpha = 0.72f),
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                            ) {
                                Text(
                                    stringResource(R.string.clock_maker_preview_aod_paused),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                        MakerClockLayer(clock, tuning, wallpaper = wallpaper, dozing = previewAod, selectedId = selectedId, onSelect = { selectedId = it },
                            onMove = { id, x, y ->
                                if (!dragging) { undo = (undo + design).takeLast(50); dragging = true }
                                change(design.copy(elements = design.elements.map {
                                    if (it.id == id) it.copy(x = x, y = y) else it
                                }), record = false)
                            }, onDragFinished = { dragging = false },
                            onPositioningChanged = { positioning = it })
                    }
                }
            }
            val editor: @Composable (Modifier, Modifier) -> Unit = { pane, sheetHandle ->
                val dragHandleDescription = stringResource(R.string.clock_maker_drag_editor)
                Column(pane.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)) {
                    Box(Modifier.fillMaxWidth().height(36.dp).then(sheetHandle)
                        .semantics { contentDescription = dragHandleDescription },
                        contentAlignment = Alignment.Center) {
                        Box(Modifier.size(width = 32.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)))
                    }
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        ) {
                        Text(
                            stringResource(R.string.clock_maker_elements),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalButton(shapes = ButtonDefaults.shapes(), enabled = design.elements.size < ClockDesign.MAX_ELEMENTS && !importingImage,
                            onClick = { dialog = "add" }) {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(if (importingImage) R.string.clock_maker_importing else R.string.clock_maker_add))
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        design.elements.forEach { element ->
                            FilterChip(selected = selectedId == element.id, onClick = { selectedId = element.id },
                                label = { Text(elementLabel(element.kind)) })
                        }
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AssistChip(onClick = { dialog = "library" },
                            label = { Text(stringResource(R.string.clock_maker_designs)) })
                        AssistChip(onClick = { dialog = "global" },
                            label = { Text(stringResource(R.string.clock_maker_global)) })
                        AssistChip(onClick = { dialog = "name" },
                            label = { Text(design.name.trim().ifEmpty { stringResource(R.string.clock_maker_name) }) })
                        FilterChip(previewAod, { previewAod = !previewAod },
                            label = { Text(stringResource(R.string.clock_maker_preview_aod)) })
                    }
                    val selected = design.elements.firstOrNull { it.id == selectedId }
                    if (selected == null) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        ) {
                            Text(
                                stringResource(R.string.clock_maker_select),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else {
                        val tabs = if (selected.kind == ClockElementKind.IMAGE) listOf(
                            R.string.clock_maker_content, R.string.clock_maker_size_position,
                            R.string.clock_maker_shape, R.string.clock_maker_effects) else listOf(
                            R.string.clock_maker_content, R.string.clock_maker_style,
                            R.string.gallery_clock_colour, R.string.clock_maker_effects)
                        ExpressiveChoices(tabs.map { stringResource(it) }, tab, onSelect = { tab = it })
                        Column(Modifier.padding(bottom = 20.dp)
                            .animateContentSize(layoutSpatial), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            AnimatedContent(
                                targetState = tab to selected.id,
                                transitionSpec = {
                                    (fadeIn(effects) + scaleIn(spatial, initialScale = 0.98f)) togetherWith
                                        (fadeOut(effects) + scaleOut(spatial, targetScale = 0.98f))
                                },
                                label = "clock_maker_controls",
                            ) { (selectedTab, _) ->
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    when (selectedTab) {
                                        0 -> {
                                            if (selected.kind == ClockElementKind.IMAGE) {
                                                FilledTonalButton(shapes = ButtonDefaults.shapes(), enabled = allowImport && !importingImage,
                                                    onClick = { chooseImage(selected.id) }, modifier = Modifier.padding(horizontal = 20.dp)) {
                                                    Text(stringResource(if (importingImage) R.string.clock_maker_importing else R.string.clock_maker_replace_image))
                                                }
                                                Text(stringResource(R.string.clock_maker_image_summary),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 20.dp))
                                            } else ElementContent(selected) { edit(it) }
                                            TuningSwitch(stringResource(R.string.clock_maker_visible), selected.visible) { edit(selected.copy(visible = it)) }
                                            Row(Modifier.padding(horizontal = 12.dp)) {
                                                TextButton(enabled = design.elements.size < ClockDesign.MAX_ELEMENTS, onClick = {
                                                    val position = positioning?.move(selected.x, selected.y + 0.1f)
                                                        ?: (selected.x to (selected.y + 0.1f).coerceAtMost(ClockElement.MAX_POSITION))
                                                    val duplicate = selected.copy(id = java.util.UUID.randomUUID().toString(),
                                                        x = position.first, y = position.second)
                                                    change(design.copy(elements = design.elements + duplicate))
                                                    selectedId = duplicate.id
                                                }) { Text(stringResource(R.string.clock_maker_duplicate)) }
                                                TextButton(onClick = {
                                                    change(design.copy(elements = design.elements.filter { it.id != selected.id }))
                                                    selectedId = design.elements.firstOrNull()?.id
                                                }) { Text(stringResource(R.string.clock_maker_remove)) }
                                            }
                                            val index = design.elements.indexOfFirst { it.id == selected.id }
                                            Row(Modifier.padding(horizontal = 12.dp)) {
                                                TextButton(enabled = index > 0, onClick = {
                                                    val elements = design.elements.toMutableList()
                                                    elements.add(index - 1, elements.removeAt(index))
                                                    change(design.copy(elements = elements))
                                                }) { Text(stringResource(R.string.clock_maker_send_backward)) }
                                                TextButton(enabled = index in 0 until design.elements.lastIndex, onClick = {
                                                    val elements = design.elements.toMutableList()
                                                    elements.add(index + 1, elements.removeAt(index))
                                                    change(design.copy(elements = elements))
                                                }) { Text(stringResource(R.string.clock_maker_bring_forward)) }
                                            }
                                        }
                                        1 -> {
                                            if (selected.kind == ClockElementKind.IMAGE) {
                                                ImageSizeControls(selected) { edit(it) }
                                            } else {
                                                TextButton(onClick = { dialog = "fonts" }, modifier = Modifier.padding(horizontal = 12.dp)) {
                                                    Text(stringResource(R.string.clock_maker_font) + ": " +
                                                        (fonts.firstOrNull { it.family == selected.font }?.name ?:
                                                            bundledFonts.firstOrNull { it.first == selected.font }?.second ?:
                                                            if (selected.font.startsWith("import:")) stringResource(R.string.clock_maker_missing_font) else selected.font))
                                                }
                                                TuningSlider(stringResource(R.string.gallery_clock_size), selected.size, 8..200, "", ClockElement.create(selected.kind).size) { edit(selected.copy(size = it)) }
                                                TuningSlider(stringResource(R.string.clock_maker_weight), selected.weight, 100..900, "", 400) { edit(selected.copy(weight = it)) }
                                                TuningSwitch(stringResource(R.string.clock_maker_italic), selected.italic) { edit(selected.copy(italic = it)) }
                                                TuningSlider(stringResource(R.string.clock_maker_spacing), selected.letterSpacing, -10..50, "%", 0) { edit(selected.copy(letterSpacing = it)) }
                                            }
                                            positioning?.let { placement ->
                                                val position = placement.position(selected.x, selected.y)
                                                TuningSlider(stringResource(R.string.clock_maker_horizontal),
                                                    (position.first * 100).roundToInt(), 0..100, "%", 50) {
                                                    val moved = placement.move(selected.x, selected.y, horizontal = it / 100f)
                                                    edit(selected.copy(x = moved.first, y = moved.second))
                                                }
                                                TuningSlider(stringResource(R.string.clock_maker_vertical),
                                                    (position.second * 100).roundToInt(), 0..100, "%", 10) {
                                                    val moved = placement.move(selected.x, selected.y, vertical = it / 100f)
                                                    edit(selected.copy(x = moved.first, y = moved.second))
                                                }
                                            }
                                            TuningSlider(stringResource(R.string.custom_clock_rotation_title), selected.rotation, -180..180, "°", 0) { edit(selected.copy(rotation = it)) }
                                        }
                                        2 -> {
                                            if (selected.kind == ClockElementKind.IMAGE) {
                                                ShapePicker(selected.imageShape) { edit(selected.copy(imageShape = it)) }
                                                TuningSwitch(stringResource(R.string.clock_maker_border), selected.imageBorderWidth > 0) {
                                                    edit(selected.copy(imageBorderWidth = if (it) 2 else 0))
                                                }
                                                if (selected.imageBorderWidth > 0) {
                                                    TuningSlider(stringResource(R.string.clock_maker_border_width), selected.imageBorderWidth,
                                                        1..24, "", 2) { edit(selected.copy(imageBorderWidth = it)) }
                                                    Text(stringResource(R.string.clock_maker_border_colour), Modifier.padding(horizontal = 20.dp),
                                                        style = MaterialTheme.typography.titleSmall)
                                                    ClockColourPicker(ELEMENT_COLOURS.filterNotNull(), selected.imageBorderColor, wallpaper, allowImport) {
                                                        edit(selected.copy(imageBorderColor = it ?: selected.imageBorderColor))
                                                    }
                                                }
                                            } else {
                                                TuningSwitch(stringResource(R.string.clock_maker_inherit), selected.color == null && !selected.gradient && !selected.glass) {
                                                    edit(selected.copy(color = if (it) null else android.graphics.Color.WHITE, gradient = false, glass = false))
                                                }
                                                ClockColourPicker(ELEMENT_COLOURS, selected.color, wallpaper, allowImport) { edit(selected.copy(color = it)) }
                                                TuningSwitch(stringResource(R.string.clock_text_glass), selected.glass) {
                                                    edit(selected.copy(glass = it, gradient = if (it) false else selected.gradient))
                                                }
                                                if (selected.glass) {
                                                    TuningSlider(stringResource(R.string.clock_glass_intensity), selected.blurIntensity,
                                                        0..100, "%", 100) { edit(selected.copy(blurIntensity = it)) }
                                                    TuningSlider(stringResource(R.string.clock_glass_lift), selected.blurLift,
                                                        0..100, "%", ClockGlass.DEFAULT_LIFT) { edit(selected.copy(blurLift = it)) }
                                                }
                                                TuningSlider(stringResource(R.string.gallery_clock_opacity), selected.opacity, 0..100, "%", 100) { edit(selected.copy(opacity = it)) }
                                                TuningSwitch(stringResource(R.string.gallery_clock_colour_gradient), selected.gradient) { edit(selected.copy(gradient = it, glass = if (it) false else selected.glass)) }
                                                AnimatedVisibility(
                                                    visible = selected.gradient,
                                                    enter = fadeIn(effects) + expandVertically(layoutSpatial),
                                                    exit = fadeOut(effects) + shrinkVertically(layoutSpatial),
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        Text(stringResource(R.string.gallery_clock_gradient_start), Modifier.padding(horizontal = 20.dp))
                                                        ClockColourPicker(ELEMENT_COLOURS.filterNotNull(), selected.gradientStart, wallpaper, allowImport) { edit(selected.copy(gradientStart = it ?: selected.gradientStart)) }
                                                        Text(stringResource(R.string.gallery_clock_gradient_end), Modifier.padding(horizontal = 20.dp))
                                                        ClockColourPicker(ELEMENT_COLOURS.filterNotNull(), selected.gradientEnd, wallpaper, allowImport) { edit(selected.copy(gradientEnd = it ?: selected.gradientEnd)) }
                                                    }
                                                }
                                            }
                                        }
                                        3 -> {
                                            TuningSwitch(stringResource(R.string.gallery_clock_shadow), selected.shadow) { edit(selected.copy(shadow = it)) }
                                            AnimatedVisibility(
                                                visible = selected.shadow,
                                                enter = fadeIn(effects) + expandVertically(layoutSpatial),
                                                exit = fadeOut(effects) + shrinkVertically(layoutSpatial),
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    ClockColourPicker(ELEMENT_COLOURS.filterNotNull(), selected.shadowColor, wallpaper, allowImport) { edit(selected.copy(shadowColor = it ?: selected.shadowColor)) }
                                                    TuningSlider(stringResource(R.string.gallery_clock_shadow_softness), selected.shadowSoftness, 0..30, "", 8) { edit(selected.copy(shadowSoftness = it)) }
                                                    TuningSlider(stringResource(R.string.gallery_clock_shadow_opacity), selected.shadowOpacity, 0..100, "%", 35) { edit(selected.copy(shadowOpacity = it)) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            }
            if (maxWidth >= 600.dp || maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    preview(Modifier.weight(0.9f).fillMaxSize())
                    editor(Modifier.weight(1.1f).fillMaxSize(), Modifier)
                }
            } else if (!fullScreenEditor) {
                Column(Modifier.fillMaxSize()) {
                    preview(Modifier.weight(0.9f))
                    editor(Modifier.weight(1.1f), Modifier)
                }
            } else {
                val sheetHeight = maxHeight * 0.86f
                val peekHeight = minOf(sheetHeight - 24.dp,
                    maxOf(112.dp, maxHeight * 0.28f))
                val travel = sheetHeight - peekHeight
                val density = LocalDensity.current
                val travelPx = with(density) { travel.toPx() }
                var rawSheetOffset by rememberSaveable(draftKey) {
                    mutableFloatStateOf(Float.NaN)
                }
                var sheetDragging by remember(draftKey) { mutableStateOf(false) }
                LaunchedEffect(travelPx) {
                    rawSheetOffset = if (rawSheetOffset.isNaN()) travelPx
                    else rawSheetOffset.coerceIn(0f, travelPx)
                }
                val renderedSheetOffset by animateFloatAsState(
                    targetValue = if (rawSheetOffset.isNaN()) travelPx else rawSheetOffset,
                    animationSpec = if (sheetDragging) snap() else spatial,
                    label = "clock_maker_sheet_offset",
                )
                val dragState = rememberDraggableState { delta ->
                    rawSheetOffset = ((if (rawSheetOffset.isNaN()) travelPx else rawSheetOffset) + delta)
                        .coerceIn(0f, travelPx)
                }
                Box(Modifier.fillMaxSize()) {
                    preview(Modifier.fillMaxSize())
                    editor(
                        Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(sheetHeight)
                            .offset { IntOffset(0, renderedSheetOffset.roundToInt()) },
                        Modifier.draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStarted = { sheetDragging = true },
                            onDragStopped = {
                                sheetDragging = false
                                val expanded = travelPx * 0.28f
                                val lowered = travelPx * 0.78f
                                rawSheetOffset = when {
                                    rawSheetOffset <= expanded -> 0f
                                    rawSheetOffset >= lowered -> travelPx
                                    else -> travelPx * 0.5f
                                }
                            },
                        ),
                    )
                }
            }
        }
    }
    if (dialog == "global") {
        LockStylesBottomSheet(onDismissRequest = { dialog = null }) {
            ClockSheet(clock, tuning, wallpaper, allowImport, { color = it.color }, { tuning = it }, showStyles = false)
        }
    }
    if (dialog == "add") {
        AlertDialog(onDismissRequest = { dialog = null }, title = { Text(stringResource(R.string.clock_maker_add)) },
            text = { Column {
                ClockElementKind.entries.forEach { kind ->
                    TextButton(enabled = kind != ClockElementKind.IMAGE || (allowImport && !importingImage), onClick = {
                        if (kind == ClockElementKind.IMAGE) {
                            dialog = null
                            chooseImage(null)
                            return@TextButton
                        }
                        val element = ClockElement.create(kind)
                        change(design.copy(elements = design.elements + element))
                        selectedId = element.id
                        dialog = null
                    }) { Text(elementLabel(kind)) }
                }
            } }, confirmButton = {})
    }
    if (dialog == "name") {
        var name by remember { mutableStateOf(design.name) }
        AlertDialog(onDismissRequest = { dialog = null }, title = { Text(stringResource(R.string.clock_maker_name)) },
            text = { OutlinedTextField(name, { name = it.take(60) }, singleLine = true) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { change(design.copy(name = name.trim())); dialog = null }) {
                Text(stringResource(R.string.gallery_done))
            } })
    }
    if (dialog == "library") {
        AlertDialog(onDismissRequest = { dialog = null }, title = { Text(stringResource(R.string.clock_maker_designs)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = {
                    val fresh = ClockDesign()
                    change(fresh)
                    selectedId = fresh.elements.firstOrNull()?.id
                    dialog = null
                }) {
                    Text(stringResource(R.string.clock_maker_new))
                }
                if (library.isEmpty()) {
                    Text(
                        stringResource(R.string.clock_maker_designs_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                library.forEach { saved ->
                    val savedDesign = saved.design ?: return@forEach
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(savedDesign.name, Modifier.weight(1f).clickable {
                            change(savedDesign)
                            tuning = saved.tuning ?: tuning
                            color = saved.color
                            selectedId = savedDesign.elements.firstOrNull()?.id
                            dialog = null
                        }.padding(12.dp))
                        TextButton(onClick = { store.deleteDesign(savedDesign.id); library = store.designs }) {
                            Text(stringResource(R.string.clock_maker_remove))
                        }
                    }
                }
            } }, confirmButton = {})
    }
    if (dialog == "fonts") {
        var search by remember { mutableStateOf("") }
        val choices = remember(bundledFonts, fonts, search) {
            val system = ClockFonts.families.map { it to it }
            val bundled = bundledFonts.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.second })
            val imported = fonts.map { it.family to it.name }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.second })
            (system + bundled + imported)
                .distinctBy { it.first.lowercase(Locale.ROOT) }
                .filter { it.first.contains(search, ignoreCase = true) ||
                    it.second.contains(search, ignoreCase = true) }
        }
        LockStylesBottomSheet(onDismissRequest = { dialog = null }) {
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)
                    .imePadding().navigationBarsPadding().selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.clock_maker_font), style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = { dialog = null }) {
                            Icon(Icons.Rounded.Close, stringResource(android.R.string.cancel))
                        }
                    }
                }
                item {
                    OutlinedTextField(search, { search = it }, singleLine = true,
                        label = { Text(stringResource(R.string.clock_maker_search_fonts)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth())
                }
                item {
                    ExpressiveButton(stringResource(if (importing) R.string.clock_maker_importing else R.string.clock_maker_import_font),
                        enabled = allowImport && !importing, primary = false, icon = Icons.Rounded.UploadFile,
                        modifier = Modifier.fillMaxWidth(), onClick = {
                            fontTarget = selectedId
                            importer.launch(arrayOf("*/*"))
                        })
                }
                if (choices.isEmpty()) {
                    item {
                        Text(stringResource(if (search.isBlank()) R.string.clock_maker_fonts_empty
                            else R.string.clock_maker_fonts_no_match),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(16.dp))
                    }
                } else {
                    items(choices, key = { it.first }) { (family, name) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isSelected = design.elements.firstOrNull { it.id == selectedId }?.font == family
                            Box(Modifier.weight(1f).clip(MaterialTheme.shapes.large)
                                .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .selectable(isSelected, role = Role.RadioButton, onClick = {
                                    design.elements.firstOrNull { it.id == selectedId }?.let { edit(it.copy(font = family)) }
                                    dialog = null
                                })) {
                                FontOption(family, name, isSelected, Modifier.fillMaxWidth().padding(end = 28.dp))
                                if (isSelected) SelectionBadge(Modifier.align(Alignment.TopEnd).padding(12.dp))
                            }
                            fonts.firstOrNull { it.family == family }?.let { font ->
                                IconButton(onClick = { removeFont = font }) {
                                    Icon(Icons.Rounded.Delete, stringResource(R.string.clock_maker_remove_font),
                                        tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    removeFont?.let { font ->
        AlertDialog(onDismissRequest = { removeFont = null }, title = { Text(stringResource(R.string.clock_maker_remove_font)) },
            text = { Text(stringResource(R.string.clock_maker_remove_font_summary)) },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    val removed = withContext(Dispatchers.IO) { runCatching { store.removeFont(font) }.isSuccess }
                    if (removed) {
                        fonts = store.fonts
                        change(design.copy(elements = design.elements.map { if (it.font == font.family) it.copy(font = "sans-serif") else it }))
                    } else error = true
                    removeFont = null
                }
            }) { Text(stringResource(R.string.clock_maker_remove)) } },
            dismissButton = { TextButton(onClick = { removeFont = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
    if (error) {
        AlertDialog(onDismissRequest = { error = false }, title = { Text(stringResource(R.string.clock_maker_font_error)) },
            text = { Text(stringResource(R.string.clock_maker_font_error_summary)) },
            confirmButton = { TextButton(onClick = { error = false }) { Text(stringResource(android.R.string.ok)) } })
    }
    if (imageError) {
        AlertDialog(onDismissRequest = { imageError = false }, title = { Text(stringResource(R.string.clock_maker_image_error)) },
            text = { Text(stringResource(R.string.clock_maker_image_error_summary)) },
            confirmButton = { TextButton(onClick = { imageError = false }) { Text(stringResource(android.R.string.ok)) } })
    }
}

@Composable
private fun ElementContent(element: ClockElement, onChange: (ClockElement) -> Unit) {
    if (element.kind == ClockElementKind.QUICKLOOK) {
        val context = LocalContext.current
        val settings = remember { android.content.Intent("com.android.axion.quicklook.SETTINGS")
            .setPackage("com.android.axion.quicklook") }
        val settingsAvailable = remember { settings.resolveActivity(context.packageManager) != null }
        Text(stringResource(R.string.clock_maker_quicklook_summary), Modifier.padding(horizontal = 20.dp))
        TuningSwitch(stringResource(R.string.clock_maker_quicklook_date_fallback), element.quickLookDateFallback) {
            onChange(element.copy(quickLookDateFallback = it))
        }
        TextButton(enabled = settingsAvailable, onClick = { context.startActivity(settings) },
            modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(stringResource(R.string.clock_maker_quicklook_settings))
        }
        TextButton(onClick = { onChange(element.copy(kind = ClockElementKind.DATE)) },
            modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(stringResource(R.string.clock_maker_quicklook_restore_date))
        }
    } else if (element.kind == ClockElementKind.WEATHER) {
        Text(stringResource(R.string.clock_maker_weather_summary), Modifier.padding(horizontal = 20.dp))
    } else if (element.kind == ClockElementKind.TEXT) {
        OutlinedTextField(element.text, { onChange(element.copy(text = it.take(240))) },
            label = { Text(stringResource(R.string.clock_maker_text)) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
    } else {
        if (element.kind == ClockElementKind.DATE) {
            TextButton(onClick = { onChange(element.copy(kind = ClockElementKind.QUICKLOOK)) },
                modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.clock_maker_quicklook_replace_date))
            }
        }
        val presets = if (element.kind == ClockElementKind.TIME) listOf("", "HH:mm", "h:mm a", "HH:mm:ss", "HH\nmm")
            else listOf("", "EEE, MMM d", "EEEE", "dd/MM", "MM/dd", "yyyy-MM-dd")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { pattern ->
                FilterChip(element.format == pattern, { onChange(element.copy(format = pattern)) },
                    label = { Text(if (pattern.isEmpty()) stringResource(R.string.clock_maker_automatic) else pattern) })
            }
        }
        var format by remember(element.id, element.format) { mutableStateOf(element.format) }
        OutlinedTextField(format, {
            format = it.take(80)
            if (ClockElement.validFormat(format)) onChange(element.copy(format = format))
        }, label = { Text(stringResource(R.string.clock_maker_format)) }, isError = !ClockElement.validFormat(format),
            supportingText = { Text(stringResource(R.string.clock_maker_format_summary)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
    }
}

@Composable
private fun ImageSizeControls(element: ClockElement, onChange: (ClockElement) -> Unit) {
    TuningSwitch(stringResource(R.string.clock_maker_lock_aspect), element.imageAspectLocked) {
        onChange(element.copy(imageAspectLocked = it, imageAspectRatio = element.imageWidth.toFloat() / element.imageHeight))
    }
    TuningSlider(stringResource(R.string.clock_maker_image_width), element.imageWidth, 16..360, "", 120) {
        onChange(element.resizeImage(width = it))
    }
    TuningSlider(stringResource(R.string.clock_maker_image_height), element.imageHeight, 16..720, "", 120) {
        onChange(element.resizeImage(height = it))
    }
    TuningSlider(stringResource(R.string.gallery_clock_opacity), element.opacity, 0..100, "%", 100) {
        onChange(element.copy(opacity = it))
    }
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ClockImageFit.entries.forEach { fit ->
            FilterChip(element.imageFit == fit, { onChange(element.copy(imageFit = fit)) },
                label = { Text(stringResource(if (fit == ClockImageFit.CROP) R.string.clock_maker_crop else R.string.clock_maker_fit)) })
        }
    }
}

@Composable
private fun ShapePicker(shape: String, onChange: (String) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Adaptive(88.dp), modifier = Modifier.fillMaxWidth()
        .height(300.dp).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ClockImageShapes.ids, key = { it }) { id ->
            val isSelected = id == shape
            val tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            Surface(shape = MaterialTheme.shapes.large,
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.clip(MaterialTheme.shapes.large)
                    .selectable(isSelected, role = Role.RadioButton, onClick = { onChange(id) })) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(40.dp).drawWithCache {
                        val path = ClockImageShapes.path(id, size.width, size.height).asComposePath()
                        onDrawBehind { drawPath(path, tint) }
                    })
                    Text(shapeLabel(id), style = MaterialTheme.typography.labelMedium, color = tint,
                        textAlign = TextAlign.Center, minLines = 2)
                }
            }
        }
    }
}

@Composable
private fun FontOption(family: String, name: String, selected: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val face by produceState(Typeface.DEFAULT, context.userId, family) {
        value = withContext(Dispatchers.IO) { ClockFonts.load(context, context.userId, family) }
    }
    val color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, color = color, style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.clock_maker_font_sample), color = color,
            fontFamily = FontFamily(face), style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None))
    }
}

/**
 * The clock-maker font library is a static resource dependency. Keep a small resource fallback
 * here because a stale resource table can make ClockFonts.bundled() return an empty list even
 * though the bundled font array is present in the application.
 */
private fun bundledFontChoices(context: Context): List<Pair<String, String>> {
    val bundled = ClockFonts.bundled(context)
    val fallback = runCatching {
        val arrayId = sequenceOf("org.lunaris.clockmaker.fonts", context.packageName)
            .map { packageName -> context.resources.getIdentifier("clock_maker_fonts", "array", packageName) }
            .firstOrNull { it != 0 } ?: 0
        if (arrayId == 0) {
            emptyList()
        } else {
            val array = context.resources.obtainTypedArray(arrayId)
            try {
                (0 until array.length()).mapNotNull { index ->
                    array.getResourceId(index, 0).takeIf { it != 0 }?.let { resourceId ->
                        runCatching {
                            val entry = context.resources.getResourceEntryName(resourceId)
                            "bundled:$entry" to entry.replace('_', ' ').replace('-', ' ')
                        }.getOrNull()
                    }
                }.distinctBy { it.first.lowercase(Locale.ROOT) }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.second })
            } finally {
                array.recycle()
            }
        }
    }.getOrDefault(emptyList())
    return (bundled + fallback)
        .distinctBy { it.first.lowercase(Locale.ROOT) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.second })
}

@Composable
private fun elementLabel(kind: ClockElementKind): String = stringResource(when (kind) {
    ClockElementKind.TIME -> R.string.clock_maker_time
    ClockElementKind.DATE -> R.string.clock_maker_date
    ClockElementKind.WEATHER -> R.string.gallery_clock_weather
    ClockElementKind.TEXT -> R.string.clock_maker_text
    ClockElementKind.IMAGE -> R.string.clock_maker_image
    ClockElementKind.QUICKLOOK -> R.string.clock_maker_quicklook
})

@Composable
private fun shapeLabel(id: String): String = stringResource(when (id) {
    "Circle" -> R.string.clock_maker_shape_circle
    "Square" -> R.string.clock_maker_shape_square
    "Slanted" -> R.string.clock_maker_shape_slanted
    "Arch" -> R.string.clock_maker_shape_arch
    "Fan" -> R.string.clock_maker_shape_fan
    "Arrow" -> R.string.clock_maker_shape_arrow
    "SemiCircle" -> R.string.clock_maker_shape_semicircle
    "Oval" -> R.string.clock_maker_shape_oval
    "Pill" -> R.string.clock_maker_shape_pill
    "Triangle" -> R.string.clock_maker_shape_triangle
    "Diamond" -> R.string.clock_maker_shape_diamond
    "ClamShell" -> R.string.clock_maker_shape_clamshell
    "Pentagon" -> R.string.clock_maker_shape_pentagon
    "Gem" -> R.string.clock_maker_shape_gem
    "Sunny" -> R.string.clock_maker_shape_sunny
    "VerySunny" -> R.string.clock_maker_shape_verysunny
    "Cookie4Sided" -> R.string.clock_maker_shape_cookie4sided
    "Cookie6Sided" -> R.string.clock_maker_shape_cookie6sided
    "Cookie7Sided" -> R.string.clock_maker_shape_cookie7sided
    "Cookie9Sided" -> R.string.clock_maker_shape_cookie9sided
    "Cookie12Sided" -> R.string.clock_maker_shape_cookie12sided
    "Ghostish" -> R.string.clock_maker_shape_ghostish
    "Clover4Leaf" -> R.string.clock_maker_shape_clover4leaf
    "Clover8Leaf" -> R.string.clock_maker_shape_clover8leaf
    "Burst" -> R.string.clock_maker_shape_burst
    "SoftBurst" -> R.string.clock_maker_shape_softburst
    "Boom" -> R.string.clock_maker_shape_boom
    "SoftBoom" -> R.string.clock_maker_shape_softboom
    "Flower" -> R.string.clock_maker_shape_flower
    "Puffy" -> R.string.clock_maker_shape_puffy
    "PuffyDiamond" -> R.string.clock_maker_shape_puffydiamond
    "PixelCircle" -> R.string.clock_maker_shape_pixelcircle
    "PixelTriangle" -> R.string.clock_maker_shape_pixeltriangle
    "Bun" -> R.string.clock_maker_shape_bun
    "Heart" -> R.string.clock_maker_shape_heart
    else -> R.string.clock_maker_shape_none
})

private val ELEMENT_COLOURS: List<Int?> = listOf(null, 0xFFFFFFFF.toInt(), 0xFF000000.toInt(),
    0xFFFFD60A.toInt(), 0xFFFF9F0A.toInt(), 0xFFFF6482.toInt(), 0xFFBF5AF2.toInt(),
    0xFF64D2FF.toInt(), 0xFF30D158.toInt())
