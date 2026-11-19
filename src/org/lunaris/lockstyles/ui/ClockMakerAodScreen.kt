/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.lunaris.lockstyles.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.lunaris.clockmaker.ClockDesignPositioning
import org.lunaris.clockmaker.ClockElement
import org.lunaris.clockmaker.ClockElementKind
import org.lunaris.lockstyles.ClockStyle
import org.lunaris.lockstyles.CustomClocks
import org.lunaris.lockstyles.GalleryWallpaper
import org.lunaris.lockstyles.R

@Composable
internal fun ClockMakerAodScreen(
    clock: ClockStyle,
    wallpaper: GalleryWallpaper,
    allowImport: Boolean,
    selectedId: String?,
    fontName: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
    onEdit: (ClockElement, Boolean) -> Unit,
    onChooseFont: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: (String) -> Unit,
    onAnimateOutline: (String, Boolean) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val design = requireNotNull(clock.design)
    val tuning = requireNotNull(clock.tuning)
    var tab by rememberSaveable(design.id) { mutableStateOf(0) }
    var fullScreen by rememberSaveable(design.id) { mutableStateOf(false) }
    var editorHidden by rememberSaveable(design.id) { mutableStateOf(false) }
    var previewLockscreen by rememberSaveable(design.id) { mutableStateOf(false) }
    var positioning by remember(design.id) { mutableStateOf<ClockDesignPositioning?>(null) }
    var dragging by remember(design.id) { mutableStateOf(false) }
    var pinchBase by remember(design.id) { mutableStateOf<ClockElement?>(null) }
    var pinchRecorded by remember(design.id) { mutableStateOf(false) }
    val selected = design.elements.firstOrNull { it.id == selectedId }
    val aodElement = selected?.let { it.aodStyle.applyTo(it) }
    val layoutSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()

    BackHandler { onClose() }
    BackHandler(enabled = fullScreen) { fullScreen = false }
    ImmersiveEffect(fullScreen)

    val actions: @Composable (Modifier) -> Unit = { modifier ->
        Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ExpressiveIconButton(Icons.AutoMirrored.Rounded.Undo,
                    stringResource(R.string.clock_maker_undo), enabled = canUndo, onClick = onUndo)
                ExpressiveIconButton(Icons.AutoMirrored.Rounded.Redo,
                    stringResource(R.string.clock_maker_redo), enabled = canRedo, onClick = onRedo)
                FilterChip(previewLockscreen, { previewLockscreen = !previewLockscreen },
                    label = { Text(stringResource(R.string.clock_maker_preview_lockscreen), maxLines = 1) })
            }
            if (!fullScreen) {
                ExpressiveIconButton(if (editorHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    stringResource(if (editorHidden) R.string.clock_maker_editor_split
                        else R.string.clock_maker_editor_fullscreen),
                    onClick = { editorHidden = !editorHidden })
            }
            ExpressiveIconButton(if (fullScreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                stringResource(if (fullScreen) R.string.clock_maker_editor_split
                    else R.string.clock_maker_editor_fullscreen), onClick = { fullScreen = !fullScreen })
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
        .displayCutoutPadding().statusBarsPadding().navigationBarsPadding()) {
        if (!fullScreen) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.clock_maker_back_to_lockscreen))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.clock_maker_aod_title), style = MaterialTheme.typography.titleLarge)
                    Text(design.name, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ExpressiveButton(stringResource(R.string.gallery_done), icon = Icons.Rounded.Check, onClick = onClose)
            }
        }
        actions(Modifier)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val preview: @Composable (Modifier) -> Unit = { pane ->
                BoxWithConstraints(pane.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    val aspect = CustomClocks.geometry(context).aspect
                    val previewWidth = minOf(maxWidth, maxHeight * aspect)
                    val shape = MaterialTheme.shapes.extraLarge
                    Box(Modifier.size(previewWidth, previewWidth / aspect).clip(shape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).background(Color.Black)) {
                        LockScreenPreviewLayout(Modifier.fillMaxSize()) {
                            if (previewLockscreen) LockScreenWallpaper(wallpaper, Modifier.fillMaxSize())
                        }
                        // Let the native clock view scale its stage so touch coordinates use
                        // the same transform as the digits, including in the split preview.
                        MakerClockLayer(clock, tuning, wallpaper = wallpaper, dozing = !previewLockscreen,
                            selectedId = selectedId,
                            onSelect = if (previewLockscreen) null else onSelect,
                            onMove = if (previewLockscreen) null else { { id, x, y ->
                                design.elements.firstOrNull { it.id == id }?.let { element ->
                                    onEdit(element.aodStyle.applyTo(element).copy(x = x, y = y), !dragging)
                                    dragging = true
                                }
                            } },
                            onDragFinished = { dragging = false },
                            onPositioningChanged = { positioning = it },
                            onPinchStart = if (!fullScreen || previewLockscreen) null else { {
                                pinchBase = aodElement
                                pinchRecorded = false
                            } },
                            onPinch = if (!fullScreen || previewLockscreen) null else { { factor ->
                                pinchBase?.let { base ->
                                    val scaled = if (base.kind == ClockElementKind.IMAGE) base.resizeImage(
                                        width = (base.imageWidth * factor).roundToInt().coerceIn(16, 360),
                                        height = (base.imageHeight * factor).roundToInt().coerceIn(16, 720))
                                    else base.copy(size = (base.size * factor).roundToInt().coerceIn(8, 200))
                                    if (scaled != aodElement) {
                                        onEdit(scaled, !pinchRecorded)
                                        pinchRecorded = true
                                    }
                                }
                            } },
                            onPinchEnd = if (!fullScreen || previewLockscreen) null else { { pinchBase = null } })
                    }
                }
            }
            val editor: @Composable (Modifier) -> Unit = { pane ->
                Column(pane.clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .verticalScroll(rememberScrollState()).padding(vertical = 16.dp)
                    .animateContentSize(layoutSpec), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.clock_maker_aod_summary), Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.clock_maker_elements), Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        design.elements.forEach { element ->
                            FilterChip(selectedId == element.id, { onSelect(element.id) },
                                label = { Text(elementLabel(element)) })
                        }
                    }
                    if (previewLockscreen) {
                        Text(stringResource(R.string.clock_maker_aod_preview_read_only), Modifier.padding(20.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (aodElement != null) {
                        TuningSwitch(stringResource(R.string.clock_maker_visible_on_aod), aodElement.visible) {
                            onEdit(aodElement.copy(visible = it), true)
                        }
                        val image = aodElement.kind == ClockElementKind.IMAGE
                        ExpressiveChoices(listOf(stringResource(if (image) R.string.clock_maker_size_position
                            else R.string.clock_maker_style),
                            stringResource(if (image) R.string.clock_maker_shape else R.string.clock_maker_effects)),
                            tab, onSelect = { tab = it })
                        if (tab == 0) {
                            ElementLayoutControls(aodElement, positioning, fontName, onChooseFont) { onEdit(it, true) }
                        } else if (image) {
                            ElementImageShapeControls(aodElement, wallpaper, allowImport) { onEdit(it, true) }
                        } else {
                            TuningSlider(stringResource(R.string.gallery_clock_opacity), aodElement.opacity,
                                0..100, "%", 100) { onEdit(aodElement.copy(opacity = it), true) }
                            ElementTextEffectsControls(aodElement, wallpaper, allowImport, aod = true) { onEdit(it, true) }
                            if (selected.supportsAodOutline && (selected.lockscreenOutline || selected.aodStyle.outline)) {
                                TuningSwitch(stringResource(R.string.clock_maker_aod_outline_animate), selected.aodStyle.animateOutline) {
                                    onAnimateOutline(selected.id, it)
                                }
                            }
                        }
                        TextButton(onClick = { onReset(selected.id) }, modifier = Modifier.padding(horizontal = 12.dp)) {
                            Text(stringResource(R.string.clock_maker_aod_reset_element))
                        }
                    } else {
                        Text(stringResource(R.string.clock_maker_select), Modifier.padding(20.dp))
                    }
                }
            }
            if (fullScreen) {
                preview(Modifier.fillMaxSize())
            } else if (maxWidth >= 600.dp || maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    preview(Modifier.weight(1f).fillMaxSize())
                    if (!editorHidden) editor(Modifier.weight(1.1f).fillMaxSize())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    preview(Modifier.weight(if (editorHidden) 1f else 0.9f))
                    if (!editorHidden) editor(Modifier.weight(1.1f).fillMaxWidth())
                }
            }
        }
    }
}
