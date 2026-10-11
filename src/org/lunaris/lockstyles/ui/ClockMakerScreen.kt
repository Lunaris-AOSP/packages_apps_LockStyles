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

package org.lunaris.lockstyles.ui

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
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lunaris.clockmaker.ClockDesign
import org.lunaris.clockmaker.ClockElement
import org.lunaris.clockmaker.ClockElementKind
import org.lunaris.clockmaker.ClockFonts
import org.lunaris.lockstyles.ClockMakerStore
import org.lunaris.lockstyles.ClockStyle
import org.lunaris.lockstyles.CustomClocks
import org.lunaris.lockstyles.GalleryWallpaper
import org.lunaris.lockstyles.R
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockMakerScreen(initial: ClockStyle, wallpaper: GalleryWallpaper, draftKey: String,
    allowImport: Boolean, onCancel: () -> Unit, onSave: (ClockStyle) -> Unit) {
    val context = LocalContext.current
    val store = remember { ClockMakerStore(context) }
    val restored = remember { store.draft(draftKey) ?: initial }
    var design by remember { mutableStateOf(restored.design ?: ClockDesign()) }
    var tuning by remember { mutableStateOf(restored.tuning ?: CustomClocks.Tuning.load(context)) }
    var color by remember { mutableStateOf(restored.color) }
    var selectedId by remember { mutableStateOf(design.elements.firstOrNull()?.id) }
    var tab by remember { mutableStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var undo by remember { mutableStateOf(emptyList<ClockDesign>()) }
    var redo by remember { mutableStateOf(emptyList<ClockDesign>()) }
    var dragging by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var fonts by remember { mutableStateOf(store.fonts) }
    val bundledFonts by produceState(emptyList<Pair<String, String>>()) {
        value = withContext(Dispatchers.IO) { ClockFonts.bundled(context) }
    }
    var library by remember { mutableStateOf(store.designs) }
    var importing by remember { mutableStateOf(false) }
    var fontTarget by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var removeFont by remember { mutableStateOf<ClockMakerStore.ImportedFont?>(null) }
    val scope = rememberCoroutineScope()
    val clock = initial.copy(face = ClockDesign.STYLE, design = design, tuning = tuning, color = color)
    val latestClock by rememberUpdatedState(clock)
    val frame = CustomClocks.frame(context, tuning, ClockDesign.STYLE)
    val maxY = ((context.resources.displayMetrics.heightPixels - frame.top) / frame.width.toFloat()).coerceIn(0.1f, 4f)
    fun change(value: ClockDesign, record: Boolean = true) {
        if (value == design) return
        if (record) undo = (undo + design).takeLast(50)
        redo = emptyList()
        design = value
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

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .statusBarsPadding().navigationBarsPadding()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { cancel() }) {
                    Icon(Icons.Rounded.Close, stringResource(android.R.string.cancel))
                }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(
                        stringResource(R.string.clock_maker_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        design.name.trim().ifEmpty { stringResource(R.string.clock_maker_new) },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                IconButton(enabled = undo.isNotEmpty(), onClick = {
                    redo = redo + design
                    design = undo.last()
                    undo = undo.dropLast(1)
                }) {
                    Icon(Icons.Rounded.Undo, stringResource(R.string.clock_maker_undo))
                }
                IconButton(enabled = redo.isNotEmpty(), onClick = {
                    undo = undo + design
                    design = redo.last()
                    redo = redo.dropLast(1)
                }) {
                    Icon(Icons.Rounded.Redo, stringResource(R.string.clock_maker_redo))
                }
                FilledTonalButton(enabled = design.elements.isNotEmpty() && !importing, onClick = {
                    val saved = design.copy(name = design.name.trim().ifEmpty { "Custom clock" })
                    store.save(clock.copy(design = saved))
                    finished = true
                    store.clearDraft(draftKey)
                    onSave(clock.copy(design = saved))
                }) {
                    Icon(Icons.Rounded.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.clock_maker_save))
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            val aspect = context.resources.displayMetrics.widthPixels.toFloat() / context.resources.displayMetrics.heightPixels
            val previewWidth = minOf(maxWidth, maxHeight * aspect)
            val previewShape = MaterialTheme.shapes.extraLarge
            Box(Modifier.size(previewWidth, previewWidth / aspect)
                .clip(previewShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), previewShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
                LiveWallpaper(wallpaper, Modifier.fillMaxSize())
                MakerClockLayer(clock, tuning, selectedId = selectedId, onSelect = { selectedId = it },
                    onMove = { id, x, y ->
                        if (!dragging) { undo = (undo + design).takeLast(50); dragging = true }
                        change(design.copy(elements = design.elements.map {
                            if (it.id == id) it.copy(x = x, y = y.coerceIn(0f, maxY)) else it
                        }), record = false)
                    }, onDragFinished = { dragging = false })
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .animateContentSize(animationSpec = spring())) {
            Box(Modifier.padding(top = 10.dp).size(width = 32.dp, height = 4.dp)
                .clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                .align(Alignment.CenterHorizontally))
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
                FilledTonalButton(enabled = design.elements.size < ClockDesign.MAX_ELEMENTS,
                    onClick = { dialog = "add" }) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.clock_maker_add))
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
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.clock_maker_content, R.string.clock_maker_style,
                        R.string.gallery_clock_colour, R.string.clock_maker_effects).forEachIndexed { index, label ->
                        FilterChip(tab == index, { tab = index }, label = { Text(stringResource(label)) })
                    }
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 20.dp)
                    .animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AnimatedContent(
                        targetState = tab to selected.id,
                        transitionSpec = {
                            (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.98f)) togetherWith
                                (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.98f))
                        },
                        label = "clock_maker_controls",
                    ) { (selectedTab, _) ->
                        when (selectedTab) {
                            0 -> {
                                ElementContent(selected) { edit(it) }
                                TuningSwitch(stringResource(R.string.clock_maker_visible), selected.visible) { edit(selected.copy(visible = it)) }
                                Row(Modifier.padding(horizontal = 12.dp)) {
                                    TextButton(enabled = design.elements.size < ClockDesign.MAX_ELEMENTS, onClick = {
                                        val duplicate = selected.copy(id = java.util.UUID.randomUUID().toString(), y = (selected.y + 0.1f).coerceAtMost(maxY))
                                        change(design.copy(elements = design.elements + duplicate))
                                        selectedId = duplicate.id
                                    }) { Text(stringResource(R.string.clock_maker_duplicate)) }
                                    TextButton(onClick = {
                                        change(design.copy(elements = design.elements.filter { it.id != selected.id }))
                                        selectedId = design.elements.firstOrNull()?.id
                                    }) { Text(stringResource(R.string.clock_maker_remove)) }
                                }
                            }
                            1 -> {
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
                                TuningSlider(stringResource(R.string.clock_maker_horizontal), (selected.x * 100).roundToInt(), 0..100, "%", 50) { edit(selected.copy(x = it / 100f)) }
                                TuningSlider(stringResource(R.string.clock_maker_vertical), (selected.y / maxY * 100).roundToInt().coerceIn(0, 100), 0..100, "%", 10) { edit(selected.copy(y = it / 100f * maxY)) }
                                TuningSlider(stringResource(R.string.custom_clock_rotation_title), selected.rotation, -180..180, "°", 0) { edit(selected.copy(rotation = it)) }
                            }
                            2 -> {
                                TuningSwitch(stringResource(R.string.clock_maker_inherit), selected.color == null && !selected.gradient) {
                                    edit(selected.copy(color = if (it) null else android.graphics.Color.WHITE, gradient = false))
                                }
                                ClockColourPicker(ELEMENT_COLOURS, selected.color, wallpaper, allowImport) { edit(selected.copy(color = it)) }
                                TuningSlider(stringResource(R.string.gallery_clock_opacity), selected.opacity, 0..100, "%", 100) { edit(selected.copy(opacity = it)) }
                                TuningSwitch(stringResource(R.string.gallery_clock_colour_gradient), selected.gradient) { edit(selected.copy(gradient = it)) }
                                AnimatedVisibility(
                                    visible = selected.gradient,
                                    enter = fadeIn(tween(180)) + expandVertically(),
                                    exit = fadeOut(tween(140)) + shrinkVertically(),
                                ) {
                                    Text(stringResource(R.string.gallery_clock_gradient_start), Modifier.padding(horizontal = 20.dp))
                                    ClockColourPicker(ELEMENT_COLOURS.filterNotNull(), selected.gradientStart, wallpaper, allowImport) { edit(selected.copy(gradientStart = it ?: selected.gradientStart)) }
                                    Text(stringResource(R.string.gallery_clock_gradient_end), Modifier.padding(horizontal = 20.dp))
                                    ClockColourPicker(ELEMENT_COLOURS.filterNotNull(), selected.gradientEnd, wallpaper, allowImport) { edit(selected.copy(gradientEnd = it ?: selected.gradientEnd)) }
                                }
                            }
                            3 -> {
                                TuningSwitch(stringResource(R.string.gallery_clock_shadow), selected.shadow) { edit(selected.copy(shadow = it)) }
                                AnimatedVisibility(
                                    visible = selected.shadow,
                                    enter = fadeIn(tween(180)) + expandVertically(),
                                    exit = fadeOut(tween(140)) + shrinkVertically(),
                                ) {
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
    if (dialog == "global") {
        ModalBottomSheet(onDismissRequest = { dialog = null }) {
            ClockSheet(clock, tuning, wallpaper, allowImport, { color = it.color }, { tuning = it }, showStyles = false)
        }
    }
    if (dialog == "add") {
        AlertDialog(onDismissRequest = { dialog = null }, title = { Text(stringResource(R.string.clock_maker_add)) },
            text = { Column {
                ClockElementKind.entries.forEach { kind ->
                    TextButton(onClick = {
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
                TextButton(onClick = { change(ClockDesign()); selectedId = design.elements.firstOrNull()?.id; dialog = null }) {
                    Text(stringResource(R.string.clock_maker_new))
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
        AlertDialog(onDismissRequest = { dialog = null }, title = { Text(stringResource(R.string.clock_maker_font)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = allowImport && !importing, onClick = {
                    fontTarget = selectedId
                    importer.launch(arrayOf("*/*"))
                }) { Text(stringResource(if (importing) R.string.clock_maker_importing else R.string.clock_maker_import_font)) }
                (ClockFonts.families.map { it to it } + bundledFonts + fonts.map { it.family to it.name }).forEach { (family, name) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FontOption(family, name, Modifier.weight(1f).clickable {
                            design.elements.firstOrNull { it.id == selectedId }?.let { edit(it.copy(font = family)) }
                            dialog = null
                        })
                        fonts.firstOrNull { it.family == family }?.let { font ->
                            TextButton(onClick = { removeFont = font }) { Text(stringResource(R.string.clock_maker_remove)) }
                        }
                    }
                }
            } }, confirmButton = {})
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
}

@Composable
private fun ElementContent(element: ClockElement, onChange: (ClockElement) -> Unit) {
    if (element.kind == ClockElementKind.WEATHER) {
        Text(stringResource(R.string.clock_maker_weather_summary), Modifier.padding(horizontal = 20.dp))
    } else if (element.kind == ClockElementKind.TEXT) {
        OutlinedTextField(element.text, { onChange(element.copy(text = it.take(240))) },
            label = { Text(stringResource(R.string.clock_maker_text)) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
    } else {
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
private fun FontOption(family: String, name: String, modifier: Modifier) {
    val context = LocalContext.current
    val face by produceState(Typeface.DEFAULT, family) {
        value = withContext(Dispatchers.IO) { ClockFonts.load(context, context.userId, family) }
    }
    Column(modifier.padding(8.dp)) {
        Text(name, style = MaterialTheme.typography.labelMedium)
        Text(stringResource(R.string.clock_maker_font_sample), fontFamily = FontFamily(face), style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun elementLabel(kind: ClockElementKind): String = stringResource(when (kind) {
    ClockElementKind.TIME -> R.string.clock_maker_time
    ClockElementKind.DATE -> R.string.clock_maker_date
    ClockElementKind.WEATHER -> R.string.gallery_clock_weather
    ClockElementKind.TEXT -> R.string.clock_maker_text
})

private val ELEMENT_COLOURS: List<Int?> = listOf(null, 0xFFFFFFFF.toInt(), 0xFF000000.toInt(),
    0xFFFFD60A.toInt(), 0xFFFF9F0A.toInt(), 0xFFFF6482.toInt(), 0xFFBF5AF2.toInt(),
    0xFF64D2FF.toInt(), 0xFF30D158.toInt())
