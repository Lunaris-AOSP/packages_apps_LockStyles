/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lunaris.extrainfowidgets.InfoBackground
import org.lunaris.lockstyles.R
import org.lunaris.lockstyles.settings.Keys
import org.lunaris.lockstyles.settings.state
import org.lunaris.lockstyles.ui.ClockGlassControls
import org.lunaris.lockstyles.ui.LockStylesBottomSheet

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InfoWidgetBackgroundPreference() {
    var enabled by Keys.EXTRA_INFO_BLUR.state()
    var background by Keys.EXTRA_INFO_BACKGROUND.state()
    var raw by Keys.EXTRA_INFO_GLASS.state()
    val style = remember(raw) { InfoBackground.glass(raw) }
    val selected = if (!enabled) 0 else if (InfoBackground.style(background) == InfoBackground.LIQUID_GLASS) 2 else 1
    val labels = listOf(R.string.info_background_design, R.string.info_background_ax_blur, R.string.info_background_liquid_glass)
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(stringResource(R.string.info_background_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(labels[selected]), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (open) LockStylesBottomSheet(onDismissRequest = { open = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.85f)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.info_background_title), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { open = false }) {
                    Icon(Icons.Rounded.Close, stringResource(android.R.string.cancel))
                }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                Text(stringResource(R.string.extra_info_preview), Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.labelMedium)
                ExtraInfoPreview(Modifier.padding(horizontal = 20.dp, vertical = 16.dp).clip(MaterialTheme.shapes.large),
                    sampleWhenEmpty = true, showBackdrop = true)
                FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    labels.forEachIndexed { index, label ->
                        FilterChip(selected = selected == index, onClick = {
                            if (index == 0) enabled = false else {
                                background = if (index == 2) InfoBackground.LIQUID_GLASS else InfoBackground.AX_BLUR
                                enabled = true
                            }
                        }, label = { Text(stringResource(label)) })
                    }
                }
                if (selected == 2) {
                    Text(stringResource(R.string.info_background_glass_help), Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ClockGlassControls(style, surface = true, defaultStyle = InfoBackground.GLASS_DEFAULT) {
                        raw = it.normalized().toJson().toString()
                    }
                }
            }
        }
    }
}
