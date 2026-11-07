/* SPDX-License-Identifier: Apache-2.0 */
package org.lunaris.lockstyles.settings.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.lunaris.extrainfowidgets.ExtraInfoCatalog
import org.lunaris.extrainfowidgets.ExtraInfoLayout
import org.lunaris.extrainfowidgets.InfoAppearance
import org.lunaris.extrainfowidgets.InfoData
import org.lunaris.extrainfowidgets.InfoWidget
import org.lunaris.lockstyles.LockScreen
import org.lunaris.lockstyles.R
import org.lunaris.lockstyles.settings.Keys
import org.lunaris.lockstyles.settings.state

@Composable
fun extraInfoAppearance(): InfoAppearance {
    val mode by Keys.WIDGETS_COLOR_MODE.state()
    val color by Keys.WIDGETS_CUSTOM_COLOR.state()
    val opacity by Keys.WIDGETS_TRANSPARENCY.state()
    val blur by Keys.EXTRA_INFO_BLUR.state()
    return InfoAppearance(mode ?: "default", color, opacity, blur)
}

@Composable
fun ExtraInfoPreview(
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    wallpaper: Bitmap? = null,
    wallpaperOffset: Float = 0f,
) {
    val context = LocalContext.current
    val catalog = remember(context) { ExtraInfoCatalog.load(context) }
    val raw by Keys.EXTRA_INFO_LAYOUT.state()
    val layout = remember(raw, catalog) { ExtraInfoLayout.parse(raw, catalog) }
    val appearance = extraInfoAppearance()
    AndroidView(
        factory = { ExtraInfoPreviewHost(it) },
        modifier = modifier.fillMaxWidth(),
        update = {
            it.wallpaper = wallpaper
            it.wallpaperOffset = wallpaperOffset
            it.grid.selection = layout
            it.grid.data = InfoData.SAMPLE
            it.grid.appearance = appearance
            it.grid.previewScale = scale
            it.refreshSource()
        },
    )
}

@Composable
internal fun DesignPreview(widget: InfoWidget, appearance: InfoAppearance, modifier: Modifier) {
    AndroidView(
        factory = { ExtraInfoDesignPreviewHost(it, widget) },
        modifier = modifier,
        update = {
            it.widget.data = InfoData.SAMPLE
            it.widget.appearance = appearance
        },
    )
}

@Composable
fun ExtraInfoWidgetsPanel(preview: LockScreen? = null) {
    var independent by Keys.EXTRA_INFO_INDEPENDENT.state()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.extra_info_mode), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !independent,
                onClick = { independent = false },
                label = { Text(stringResource(R.string.extra_info_mode_rows)) },
            )
            FilterChip(
                selected = independent,
                onClick = { independent = true },
                label = { Text(stringResource(R.string.extra_info_mode_independent)) },
            )
        }
        if (independent) IndependentInfoPanel(preview) else ExtraInfoRowsPanel()
    }
}

@Composable
private fun ExtraInfoRowsPanel() {
    val context = LocalContext.current
    val catalog = remember(context) { ExtraInfoCatalog.load(context) }
    var raw by Keys.EXTRA_INFO_LAYOUT.state()
    val layout = remember(raw, catalog) { ExtraInfoLayout.parse(raw, catalog) }
    val appearance = extraInfoAppearance()
    var pickerRow by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF29465B), Color(0xFF4C3557), Color(0xFF1E2635))
                    )
                )
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.extra_info_preview),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ExtraInfoPreview()
            if (layout.rows.all { it.isEmpty() })
                Text(
                    stringResource(R.string.extra_info_empty),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
        }
        Text(
            stringResource(R.string.extra_info_capacity),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(4, 5).forEach { count ->
                val resized = layout.withColumns(count, catalog)
                FilterChip(
                    selected = layout.columns == count,
                    enabled = resized != null,
                    onClick = { resized?.let { raw = it.encode() } },
                    label = { Text(stringResource(R.string.extra_info_slots, count)) },
                )
            }
        }
        Text(
            stringResource(R.string.extra_info_layout_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        (0..1).forEach { row ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    Modifier.fillMaxWidth().animateContentSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(
                            R.string.extra_info_row,
                            row + 1,
                            layout.used(row, catalog),
                            layout.columns,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    layout.rows[row].forEachIndexed { index, id ->
                        val widget = catalog.first { it.id == id }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                widget.name,
                                Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(
                                enabled = index > 0,
                                onClick = { raw = layout.move(row, index, -1).encode() },
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    stringResource(R.string.extra_info_move_before),
                                )
                            }
                            IconButton(
                                enabled = index < layout.rows[row].lastIndex,
                                onClick = { raw = layout.move(row, index, 1).encode() },
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowForward,
                                    stringResource(R.string.extra_info_move_after),
                                )
                            }
                            IconButton(onClick = { raw = layout.remove(row, id).encode() }) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    stringResource(R.string.extra_info_remove, widget.name),
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { pickerRow = row },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.extra_info_choose))
                    }
                }
            }
        }
    }
    pickerRow?.let { row ->
        Dialog(
            onDismissRequest = { pickerRow = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 640.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.extra_info_title),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                stringResource(
                                    R.string.extra_info_row,
                                    row + 1,
                                    layout.used(row, catalog),
                                    layout.columns,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        IconButton(onClick = { pickerRow = null }) {
                            Icon(Icons.Rounded.Close, stringResource(android.R.string.cancel))
                        }
                    }
                    Text(
                        stringResource(R.string.extra_info_picker_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(catalog, key = { it.id }) { widget ->
                            val selected = widget.id in layout.rows.flatten()
                            val next = layout.add(row, widget, catalog)
                            val cardColor by
                                animateColorAsState(
                                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    label = "widgetSelection",
                                )
                            Card(
                                enabled = selected || next != null,
                                modifier =
                                    Modifier.alpha(if (selected || next != null) 1f else 0.4f),
                                colors = CardDefaults.cardColors(containerColor = cardColor),
                                onClick = {
                                    raw =
                                        if (selected)
                                            layout
                                                .copy(rows = layout.rows.map { it - widget.id })
                                                .encode()
                                        else next?.encode() ?: raw
                                },
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth()
                                            .height(88.dp)
                                            .clip(MaterialTheme.shapes.medium)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        Color(0xFF29465B),
                                                        Color(0xFF4C3557),
                                                        Color(0xFF1E2635),
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        DesignPreview(
                                            widget,
                                            appearance,
                                            Modifier.fillMaxWidth()
                                                .height(72.dp)
                                                .padding(horizontal = 6.dp),
                                        )
                                    }
                                    Text(
                                        widget.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                    Text(
                                        stringResource(
                                            if (selected) R.string.extra_info_selected
                                            else R.string.extra_info_slots,
                                            if (selected)
                                                layout.rows.indexOfFirst { widget.id in it } + 1
                                            else widget.span(layout.columns),
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
