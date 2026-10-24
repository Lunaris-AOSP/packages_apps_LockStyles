/* SPDX-License-Identifier: Apache-2.0 */
package org.lunaris.lockstyles.settings.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import org.lunaris.lockstyles.R
import org.lunaris.lockstyles.ui.LockStylesBottomSheet
import androidx.compose.ui.unit.dp
import org.lunaris.lockscreenwidgets.WidgetContract
import org.lunaris.lockscreenwidgets.WidgetEntry
import org.lunaris.lockscreenwidgets.WidgetSizes
import org.lunaris.lockscreenwidgets.R as WidgetR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppWidgetsBottomSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = LocalAppWidgets.current
    var picking by rememberSaveable { mutableStateOf(false) }
    val configure = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        state.refresh()
    }
    fun launch(intent: Intent) {
        runCatching { configure.launch(intent) }.onFailure {
            Toast.makeText(context, WidgetR.string.app_widgets_failed, Toast.LENGTH_LONG).show()
        }
    }
    LockStylesBottomSheet(onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        BackHandler(enabled = picking) { picking = false }
        Column(Modifier.fillMaxWidth()
            .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (picking) IconButton(onClick = { picking = false }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.app_widget_back))
                }
                Text(stringResource(if (picking) WidgetR.string.app_widgets_add else WidgetR.string.app_widgets_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).padding(start = if (picking) 0.dp else 12.dp))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.app_widget_close))
                }
            }
            if (picking) AppWidgetPicker(onPick = { info, size ->
                launch(Intent().setClassName("com.android.systemui", WidgetContract.ACTIVITY)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra("width", size.first).putExtra("height", size.second))
                picking = false
            }) else AppWidgetsPanel(onAdd = { picking = true }, onConfigure = { id ->
                launch(Intent().setClassName("com.android.systemui", WidgetContract.ACTIVITY).putExtra("id", id))
            })
        }
    }
}

@Composable
private fun AppWidgetsPanel(onAdd: () -> Unit, onConfigure: (Int) -> Unit) {
    val state = LocalAppWidgets.current
    val context = LocalContext.current
    val entries = state.entries
    var selectedId by rememberSaveable { mutableStateOf(-1) }
    val selected = entries.firstOrNull { it.id == selectedId } ?: entries.firstOrNull()
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.toggleable(value = state.enabled, enabled = state.connected,
                    role = Role.Switch, onValueChange = { state.change("enable", it.toString()) })
                    .padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_widget_show), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(WidgetR.string.app_widgets_summary),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.enabled, enabled = state.connected, onCheckedChange = null)
                }
            }
        }
        state.error?.let { failure ->
            item {
                Text(stringResource(failure.messageResId), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { state.refresh() }) { Text(stringResource(WidgetR.string.app_widgets_retry)) }
            }
        }
        item {
            Button(onClick = onAdd, enabled = state.connected, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, null)
                Text(stringResource(WidgetR.string.app_widgets_add), Modifier.padding(start = 8.dp))
            }
        }
        if (!state.connected && state.error == null) {
            item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
        if (entries.isEmpty() && state.connected) {
            item { Text(stringResource(WidgetR.string.app_widgets_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else if (entries.isNotEmpty()) {
            item { Text(stringResource(WidgetR.string.app_widgets_select), style = MaterialTheme.typography.bodyMedium) }
            item {
                AppWidgetGrid(Modifier.fillMaxWidth(), editing = true, selectedId = selected?.id ?: -1,
                    onSelect = { selectedId = it })
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries, key = { it.id }) { entry ->
                        FilterChip(selected = selected?.id == entry.id, onClick = { selectedId = entry.id }, label = {
                            Text(context.getString(WidgetR.string.app_widgets_provider_size,
                                state.info(entry.id)?.loadLabel(context.packageManager)
                                    ?: context.getString(WidgetR.string.app_widgets_unavailable), entry.width, entry.height))
                        })
                    }
                }
            }
            selected?.let { entry ->
                item { WidgetControls(entry, onConfigure) }
            }
        }
    }
}

@Composable
private fun WidgetControls(entry: WidgetEntry, onConfigure: (Int) -> Unit) {
    val context = LocalContext.current
    val state = LocalAppWidgets.current
    val entries = state.entries
    val info = state.info(entry.id)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(info?.loadLabel(context.packageManager) ?: stringResource(WidgetR.string.app_widgets_unavailable),
                style = MaterialTheme.typography.titleMedium)
            if (info != null) {
                val sizes = (WidgetSizes(context, state.gridWidth).allowed(info) + (entry.width to entry.height))
                    .distinct().filter { (w, h) -> WidgetEntry.place(entry.id, w, h, entries) != null }
                WidgetChoice(stringResource(WidgetR.string.app_widgets_size),
                    sizes.map { context.getString(R.string.app_widget_dimensions, it.first, it.second) },
                    sizes.indexOf(entry.width to entry.height), state.connected) { index ->
                    val (w, h) = sizes[index]
                    val resized = entry.copy(width = w, height = h)
                    val placed = if (resized.fits(entries)) resized else WidgetEntry.place(entry.id, w, h, entries)
                    placed?.let { state.change("change", extras = it.bundle()) }
                }
                val positions = buildList {
                    for (y in 0..4 - entry.height) for (x in 0..4 - entry.width) {
                        entry.copy(x = x, y = y).takeIf { it.fits(entries) }?.let(::add)
                    }
                }
                WidgetChoice(stringResource(WidgetR.string.app_widgets_position), positions.map {
                    context.getString(WidgetR.string.app_widgets_position_value, it.x + 1, it.y + 1)
                }, positions.indexOf(entry), state.connected) { index ->
                    state.change("change", extras = positions[index].bundle())
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (info?.configure != null && info.widgetFeatures and
                    AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0) {
                    OutlinedButton(onClick = { onConfigure(entry.id) }, enabled = state.connected) {
                        Text(stringResource(WidgetR.string.app_widgets_configure))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { state.change("remove", entry.id.toString()) }, enabled = state.connected) {
                    Text(stringResource(WidgetR.string.app_widgets_remove), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun WidgetChoice(title: String, values: List<String>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled && values.isNotEmpty()) {
                Text(values.getOrElse(selected) { "" }, Modifier.weight(1f))
                Icon(Icons.Rounded.ArrowDropDown, null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                values.forEachIndexed { index, value ->
                    DropdownMenuItem(text = { Text(value) }, onClick = { expanded = false; onSelect(index) })
                }
            }
        }
    }
}
