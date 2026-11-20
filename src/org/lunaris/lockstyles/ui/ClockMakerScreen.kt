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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lunaris.clockmaker.ClockDesign
import org.lunaris.clockmaker.ClockDesignEdit
import org.lunaris.clockmaker.ClockDesignEditMode
import org.lunaris.clockmaker.ClockDesignPositioning
import org.lunaris.clockmaker.ClockElement
import org.lunaris.clockmaker.ClockElementKind
import org.lunaris.clockmaker.ClockFonts
import org.lunaris.clockmaker.ClockHourFormat
import org.lunaris.clockmaker.ClockTimePart
import org.lunaris.lockstyles.ClockImageStore
import org.lunaris.lockstyles.ClockMakerStore
import org.lunaris.lockstyles.ClockStyle
import org.lunaris.lockstyles.CustomClocks
import org.lunaris.lockstyles.GalleryWallpaper
import org.lunaris.lockstyles.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockMakerScreen(initial: ClockStyle, wallpaper: GalleryWallpaper, draftKey: String,
    allowImport: Boolean, onCancel: () -> Unit, onSave: (ClockStyle) -> Unit, layoutSize: Boolean? = null) {
    val context = LocalContext.current
    val store = remember { ClockMakerStore(context) }
    val imageStore = remember { ClockImageStore(context) }
    val restored = remember(draftKey, initial) { store.draft(draftKey) ?: initial }
    var baseClock by remember(draftKey, initial) { mutableStateOf(restored) }
    var design by remember(draftKey, initial) { mutableStateOf(restored.design ?: ClockDesign()) }
    var tuning by remember(draftKey, initial) { mutableStateOf(restored.tuning ?: CustomClocks.Tuning.load(context)) }
    var color by remember(draftKey, initial) { mutableStateOf(restored.color) }
    var selectedId by remember(draftKey, initial) { mutableStateOf(design.elements.firstOrNull()?.id) }
    var tab by remember(draftKey) { mutableStateOf(0) }
    var dialog by remember(draftKey) { mutableStateOf<String?>(null) }
    var undo by remember(draftKey) { mutableStateOf(emptyList<ClockDesignEdit>()) }
    var redo by remember(draftKey) { mutableStateOf(emptyList<ClockDesignEdit>()) }
    var aodUndo by remember(draftKey) { mutableStateOf(emptyList<ClockDesignEdit>()) }
    var aodRedo by remember(draftKey) { mutableStateOf(emptyList<ClockDesignEdit>()) }
    var dragging by remember(draftKey) { mutableStateOf(false) }
    var finished by remember(draftKey) { mutableStateOf(false) }
    var fonts by remember(draftKey) { mutableStateOf(store.fonts) }
    val bundledFonts by produceState(emptyList<Pair<String, String>>()) {
        value = withContext(Dispatchers.IO) { bundledFontChoices(context) }
    }
    var library by remember(draftKey) { mutableStateOf(store.designs) }
    var importing by remember(draftKey) { mutableStateOf(false) }
    var fontTarget by rememberSaveable(draftKey) { mutableStateOf<String?>(null) }
    var fontTargetAod by rememberSaveable(draftKey) { mutableStateOf(false) }
    var error by remember(draftKey) { mutableStateOf(false) }
    var removeFont by remember(draftKey) { mutableStateOf<ClockMakerStore.ImportedFont?>(null) }
    var importingImage by remember(draftKey) { mutableStateOf(false) }
    var imageError by remember(draftKey) { mutableStateOf(false) }
    var imageTarget by rememberSaveable(draftKey) { mutableStateOf<String?>(null) }
    var imageDesign by rememberSaveable(draftKey) { mutableStateOf<String?>(null) }
    var editingAod by rememberSaveable(draftKey) { mutableStateOf(false) }
    var fullScreenEditor by rememberSaveable(draftKey) { mutableStateOf(false) }
    var editorHidden by rememberSaveable(draftKey) { mutableStateOf(false) }
    var pinchBase by remember(draftKey) { mutableStateOf<ClockElement?>(null) }
    var pinchRecorded by remember(draftKey) { mutableStateOf(false) }
    var positioning by remember(draftKey) { mutableStateOf<ClockDesignPositioning?>(null) }
    val scope = rememberCoroutineScope()
    val clock = baseClock.copy(face = ClockDesign.STYLE, design = design, tuning = tuning, color = color)
    val latestClock by rememberUpdatedState(clock)
    fun change(value: ClockDesign, record: Boolean = true,
        mode: ClockDesignEditMode = ClockDesignEditMode.LOCKSCREEN, coalesce: Boolean = true) {
        val existingIds = design.elements.map { it.id }.toSet()
        val next = if (design.elements.any { it.aodLayoutConfigured }) value.copy(elements = value.elements.map {
            if (it.id !in existingIds && !it.aodLayoutConfigured) it.configureAod() else it
        }) else value
        if (next == design) return
        if (record || coalesce) {
            val history = if (mode == ClockDesignEditMode.AOD) aodUndo else undo
            val entry = ClockDesignEdit(design, next, mode)
            val updated = if (!record && history.lastOrNull()?.after == design)
                history.dropLast(1) + history.last().copy(after = next)
            else (history + entry).takeLast(50)
            if (mode == ClockDesignEditMode.AOD) {
                aodUndo = updated
                aodRedo = emptyList()
            } else {
                undo = updated
                redo = emptyList()
            }
        }
        design = next
    }
    fun undoEdit(mode: ClockDesignEditMode = ClockDesignEditMode.LOCKSCREEN) {
        val history = if (mode == ClockDesignEditMode.AOD) aodUndo else undo
        val entry = history.lastOrNull()?.retainAod(design) ?: return
        design = entry.undo(design)
        if (mode == ClockDesignEditMode.AOD) {
            aodUndo = history.dropLast(1)
            aodRedo = (aodRedo + entry).takeLast(50)
        } else {
            undo = history.dropLast(1)
            redo = (redo + entry).takeLast(50)
        }
    }
    fun redoEdit(mode: ClockDesignEditMode = ClockDesignEditMode.LOCKSCREEN) {
        val history = if (mode == ClockDesignEditMode.AOD) aodRedo else redo
        val entry = history.lastOrNull()?.retainAod(design) ?: return
        design = entry.redo(design)
        if (mode == ClockDesignEditMode.AOD) {
            aodRedo = history.dropLast(1)
            aodUndo = (aodUndo + entry).takeLast(50)
        } else {
            redo = history.dropLast(1)
            undo = (undo + entry).takeLast(50)
        }
    }
    fun openDesign(value: ClockStyle) {
        baseClock = value
        design = requireNotNull(value.design)
        tuning = value.tuning ?: tuning
        color = value.color
        undo = emptyList()
        redo = emptyList()
        aodUndo = emptyList()
        aodRedo = emptyList()
        selectedId = design.elements.firstOrNull()?.id
        dialog = null
    }
    LaunchedEffect(design.id, design.elements.map { it.id }) {
        if (selectedId != null && design.elements.none { it.id == selectedId }) {
            selectedId = design.elements.firstOrNull()?.id
        } else if (selectedId == null && design.elements.isNotEmpty()) {
            selectedId = design.elements.first().id
        }
    }
    fun edit(element: ClockElement, mode: ClockDesignEditMode = ClockDesignEditMode.LOCKSCREEN) = change(design.copy(elements = design.elements.map {
        if (it.id == element.id) element else it
    }), mode = mode)
    fun editAod(element: ClockElement, record: Boolean = true) = change(design.copy(
        elements = design.elements.map { if (it.id == element.id) it.editAod(element) else it }), record,
        mode = ClockDesignEditMode.AOD)
    fun cancel() {
        finished = true
        store.clearDraft(draftKey)
        onCancel()
    }
    fun save() {
        val saved = clock.copy(design = design.copy(name = design.name.trim().ifEmpty { "Custom clock" }))
        store.save(saved)
        library = store.designs
        finished = true
        store.clearDraft(draftKey)
        onSave(saved)
    }
    BackHandler { cancel() }
    BackHandler(enabled = fullScreenEditor) { fullScreenEditor = false }
    ImmersiveEffect(fullScreenEditor)
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
                    design.elements.firstOrNull { it.id == fontTarget }?.let { edit(it.copy(font = font.family),
                        if (fontTargetAod) ClockDesignEditMode.AOD else ClockDesignEditMode.LOCKSCREEN) }
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
            val keepImages = (listOf(design) + (undo + redo + aodUndo + aodRedo)
                .flatMap { listOf(it.before, it.after) }).flatMap { it.elements }.map { it.image }.toSet()
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
        imageImporter.launch(arrayOf("image/*", "application/json", "text/json", "text/plain", "application/octet-stream"))
    }

    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val layoutSpatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntSize>()
    if (editingAod && layoutSize == null) {
        val selected = design.elements.firstOrNull { it.id == selectedId }
        val fontName = selected?.let { element ->
            fonts.firstOrNull { it.family == element.font }?.name ?:
                bundledFonts.firstOrNull { it.first == element.font }?.second ?:
                if (element.font.startsWith("import:")) stringResource(R.string.clock_maker_missing_font)
                else element.font
        }.orEmpty()
        ClockMakerAodScreen(clock, wallpaper, allowImport, selectedId, fontName,
            canUndo = aodUndo.isNotEmpty(), canRedo = aodRedo.isNotEmpty(),
            onClose = { editingAod = false }, onSelect = { selectedId = it },
            onEdit = { element, record -> editAod(element, record) },
            onChooseFont = { dialog = "fonts" },
            onUndo = { undoEdit(ClockDesignEditMode.AOD) },
            onRedo = { redoEdit(ClockDesignEditMode.AOD) },
            onReset = { id ->
                change(design.copy(elements = design.elements.map { if (it.id == id) it.resetAod() else it }),
                    mode = ClockDesignEditMode.AOD)
            },
            onAnimateOutline = { id, enabled ->
                change(design.copy(elements = design.elements.map {
                    if (it.id == id) it.configureAod(it.aodStyle.copy(animateOutline = enabled)) else it
                }), mode = ClockDesignEditMode.AOD)
            })
    } else Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
        .statusBarsPadding().navigationBarsPadding()) {
        if (!fullScreenEditor) Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { cancel() }) {
                        Icon(Icons.Rounded.Close, stringResource(android.R.string.cancel))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(when (layoutSize) {
                            true -> R.string.dynamic_clock_small_maker
                            false -> R.string.dynamic_clock_large_maker
                            null -> R.string.clock_maker_title
                        }),
                            style = MaterialTheme.typography.titleLarge)
                        Text(design.name.trim().ifEmpty { stringResource(R.string.clock_maker_new) },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    ExpressiveButton(stringResource(R.string.clock_maker_save),
                        enabled = design.elements.isNotEmpty() && !importing && !importingImage,
                        icon = Icons.Rounded.Save, onClick = { save() })
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    ExpressiveIconButton(Icons.AutoMirrored.Rounded.Undo,
                        stringResource(R.string.clock_maker_undo), enabled = undo.isNotEmpty(), onClick = {
                            undoEdit()
                        })
                    ExpressiveIconButton(Icons.AutoMirrored.Rounded.Redo,
                        stringResource(R.string.clock_maker_redo), enabled = redo.isNotEmpty(), onClick = {
                            redoEdit()
                        })
                    Spacer(Modifier.weight(1f))
                    ClockArchiveActions(clock, store, allowImport && !importing && !importingImage) { imported ->
                        openDesign(imported)
                        fonts = store.fonts
                        library = store.designs
                    }
                    ExpressiveIconButton(if (editorHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        stringResource(if (editorHidden) R.string.clock_maker_editor_split
                            else R.string.clock_maker_editor_fullscreen),
                        onClick = { editorHidden = !editorHidden })
                    ExpressiveIconButton(if (fullScreenEditor) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        stringResource(if (fullScreenEditor) R.string.clock_maker_editor_split
                            else R.string.clock_maker_editor_fullscreen),
                        onClick = { fullScreenEditor = true })
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val preview: @Composable (Modifier) -> Unit = { pane ->
                BoxWithConstraints(pane.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    val aspect = CustomClocks.geometry(context).aspect
                    val previewWidth = minOf(maxWidth, maxHeight * aspect)
                    val previewShape = MaterialTheme.shapes.extraLarge
                    Box(Modifier.size(previewWidth, previewWidth / aspect)
                        .clip(previewShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), previewShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
                        LockScreenPreviewLayout(Modifier.fillMaxSize()) {
                            LockScreenWallpaper(wallpaper, Modifier.fillMaxSize())
                            MakerClockLayer(clock, tuning, wallpaper = wallpaper, selectedId = selectedId, onSelect = { selectedId = it },
                                onMove = { id, x, y ->
                                    change(design.copy(elements = design.elements.map {
                                        if (it.id == id) it.copy(x = x, y = y) else it
                                    }), record = !dragging)
                                    dragging = true
                                }, onDragFinished = { dragging = false },
                                onPositioningChanged = { positioning = it },
                                onPinchStart = if (!fullScreenEditor) null else { {
                                    pinchBase = design.elements.firstOrNull { it.id == selectedId }
                                    pinchRecorded = false
                                } },
                                onPinch = if (!fullScreenEditor) null else { { factor ->
                                    pinchBase?.let { base ->
                                        val scaled = if (base.kind == ClockElementKind.IMAGE) {
                                            base.resizeImage(
                                                width = (base.imageWidth * factor).roundToInt().coerceIn(16, 360),
                                                height = (base.imageHeight * factor).roundToInt().coerceIn(16, 720))
                                        } else base.copy(size = (base.size * factor).roundToInt()
                                            .coerceIn(ClockElement.MIN_TEXT_SIZE, ClockElement.MAX_TEXT_SIZE))
                                        val next = design.copy(elements = design.elements.map {
                                            if (it.id == scaled.id) scaled else it
                                        })
                                        if (next != design) {
                                            change(next, record = !pinchRecorded)
                                            pinchRecorded = true
                                        }
                                    }
                                } },
                                onPinchEnd = if (!fullScreenEditor) null else { { pinchBase = null } })
                        }
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
                                label = { Text(elementLabel(element)) })
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
                        if (layoutSize == null) AssistChip(onClick = {
                            change(design.prepareAod(), record = false, coalesce = false)
                            editingAod = true
                        },
                            label = { Text(stringResource(R.string.clock_maker_edit_aod)) })
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
                                                        x = position.first, y = position.second,
                                                        aod = selected.aod?.copy(
                                                            x = (selected.aodStyle.x + position.first - selected.x)
                                                                .coerceIn(ClockElement.MIN_POSITION, ClockElement.MAX_POSITION),
                                                            y = (selected.aodStyle.y + position.second - selected.y)
                                                                .coerceIn(ClockElement.MIN_POSITION, ClockElement.MAX_POSITION)))
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
                                            val fontName = fonts.firstOrNull { it.family == selected.font }?.name ?:
                                                bundledFonts.firstOrNull { it.first == selected.font }?.second ?:
                                                if (selected.font.startsWith("import:")) stringResource(R.string.clock_maker_missing_font)
                                                else selected.font
                                            ElementLayoutControls(selected, positioning, fontName,
                                                onChooseFont = { dialog = "fonts" }, showAodFontHint = layoutSize == null,
                                                onChange = { edit(it) })
                                        }
                                        2 -> {
                                            if (selected.kind == ClockElementKind.IMAGE) {
                                                ElementImageShapeControls(selected, wallpaper, allowImport) { edit(it) }
                                            } else {
                                                TuningSwitch(stringResource(R.string.clock_maker_inherit), selected.color == null && !selected.gradient && !selected.glass) {
                                                    edit(selected.copy(color = if (it) null else android.graphics.Color.WHITE, gradient = false, glass = false))
                                                }
                                                ClockColourPicker(ELEMENT_COLOURS, selected.color, wallpaper, allowImport) { edit(selected.copy(color = it)) }
                                                TuningSwitch(stringResource(R.string.clock_text_glass), selected.glass) {
                                                    edit(selected.copy(glass = it, gradient = if (it) false else selected.gradient))
                                                }
                                                if (selected.glass) {
                                                    ClockGlassControls(selected.glassStyle,
                                                        date = selected.kind == ClockElementKind.DATE) {
                                                        edit(selected.copy(glassStyle = it))
                                                    }
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
                                            ElementTextEffectsControls(selected, wallpaper, allowImport) { edit(it) }
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
            if (!fullScreenEditor && (maxWidth >= 600.dp || maxWidth > maxHeight)) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    preview(Modifier.weight(if (editorHidden) 1f else 0.9f).fillMaxSize())
                    AnimatedVisibility(!editorHidden, Modifier.weight(1.1f).fillMaxSize(),
                        enter = fadeIn(effects), exit = fadeOut(effects)) {
                        editor(Modifier.fillMaxSize(), Modifier)
                    }
                }
            } else if (!fullScreenEditor) {
                Column(Modifier.fillMaxSize()) {
                    preview(Modifier.weight(if (editorHidden) 1f else 0.9f))
                    AnimatedVisibility(!editorHidden, Modifier.weight(1.1f),
                        enter = fadeIn(effects) + expandVertically(layoutSpatial),
                        exit = fadeOut(effects) + shrinkVertically(layoutSpatial)) {
                        editor(Modifier.fillMaxSize(), Modifier)
                    }
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    preview(Modifier.fillMaxSize())
                    Row(Modifier.align(Alignment.TopEnd).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExpressiveIconButton(Icons.AutoMirrored.Rounded.Undo,
                            stringResource(R.string.clock_maker_undo), enabled = undo.isNotEmpty(), onClick = {
                                undoEdit()
                            })
                        ExpressiveIconButton(Icons.AutoMirrored.Rounded.Redo,
                            stringResource(R.string.clock_maker_redo), enabled = redo.isNotEmpty(), onClick = {
                                redoEdit()
                            })
                        ExpressiveIconButton(Icons.Rounded.FullscreenExit,
                            stringResource(R.string.clock_maker_editor_split),
                            onClick = { fullScreenEditor = false })
                    }
                }
            }
        }
    }
    if (dialog == "global") {
        LockStylesBottomSheet(onDismissRequest = { dialog = null }) {
            ClockSheet(clock, tuning, wallpaper, allowImport, {
                color = it.color
                baseClock = baseClock.copy(showOnHomeScreen = it.showOnHomeScreen,
                    homeLayout = it.homeLayout)
            }, { tuning = it }, showStyles = false, layoutSize = layoutSize)
        }
    }
    if (dialog == "add") {
        AlertDialog(onDismissRequest = { dialog = null }, title = { Text(stringResource(R.string.clock_maker_add)) },
            text = { Column {
                TextButton(enabled = design.elements.size + 5 <= ClockDesign.MAX_ELEMENTS, onClick = {
                    val digits = ClockElement.createTimeDigits()
                    change(design.copy(elements = design.elements + digits))
                    selectedId = digits.first().id
                    tab = 0
                    dialog = null
                }) { Text(stringResource(R.string.clock_maker_separate_digits)) }
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
                    openDesign(clock.copy(design = ClockDesign()))
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
                            openDesign(saved)
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
                            fontTargetAod = editingAod
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
                                    design.elements.firstOrNull { it.id == selectedId }?.let { edit(it.copy(font = family),
                                        if (editingAod) ClockDesignEditMode.AOD else ClockDesignEditMode.LOCKSCREEN) }
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
                        change(design.copy(elements = design.elements.map { if (it.font == font.family) it.copy(font = "sans-serif") else it }),
                            mode = if (editingAod) ClockDesignEditMode.AOD else ClockDesignEditMode.LOCKSCREEN)
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
    if (element.kind == ClockElementKind.TIME) {
        Text(stringResource(R.string.clock_maker_time_part), Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.titleSmall)
        val partRows = listOf(listOf(ClockTimePart.FULL)) + ClockTimePart.entries.drop(1).chunked(2)
        partRows.forEach { parts ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                parts.forEach { part ->
                    FilterChip(element.timePart == part, { onChange(element.copy(timePart = part)) },
                        label = { Text(timePartLabel(part)) })
                }
            }
        }
        Text(stringResource(R.string.clock_maker_time_part_summary), Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (element.timePart != ClockTimePart.FULL) {
            if (element.timePart.isHour) {
                Text(stringResource(R.string.clock_maker_hour_format), Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClockHourFormat.entries.forEach { format ->
                        FilterChip(element.hourFormat == format, { onChange(element.copy(hourFormat = format)) },
                            label = { Text(stringResource(when (format) {
                                ClockHourFormat.SYSTEM -> R.string.clock_maker_automatic
                                ClockHourFormat.H12 -> R.string.clock_maker_hour_12
                                ClockHourFormat.H24 -> R.string.clock_maker_hour_24
                            })) })
                    }
                }
            }
            return
        }
    }
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
