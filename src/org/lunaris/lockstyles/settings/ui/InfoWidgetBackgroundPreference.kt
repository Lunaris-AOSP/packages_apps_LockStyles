/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles.settings.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.lunaris.extrainfowidgets.InfoBackground
import org.lunaris.lockstyles.R
import org.lunaris.lockstyles.settings.Keys
import org.lunaris.lockstyles.settings.state
import org.lunaris.lockstyles.settings.ui.components.squishable
import org.lunaris.lockstyles.ui.ClockGlassControls
import org.lunaris.lockstyles.ui.ExpressiveConnectedRow
import org.lunaris.lockstyles.ui.LockStylesBottomSheet
import org.lunaris.lockstyles.ui.cardContainer

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InfoWidgetBackgroundPreference() {
    var enabled by Keys.EXTRA_INFO_BLUR.state()
    var background by Keys.EXTRA_INFO_BACKGROUND.state()
    var raw by Keys.EXTRA_INFO_GLASS.state()
    val style = remember(raw) { InfoBackground.glass(raw) }
    val selected = if (!enabled) 0 else if (InfoBackground.style(background) == InfoBackground.LIQUID_GLASS) 2 else 1
    val labels = listOf(R.string.info_background_design, R.string.info_background_ax_blur, R.string.info_background_liquid_glass)
    var open by rememberSaveable { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    Surface(
        onClick = { open = true },
        interactionSource = interactions,
        shape = MaterialTheme.shapes.large,
        color = cardContainer(),
        modifier = Modifier.fillMaxWidth().squishable(interactions),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.info_background_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(labels[selected]),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (open) LockStylesBottomSheet(onDismissRequest = { open = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.85f)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.info_background_title), Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall)
                FilledTonalIconButton(onClick = { open = false }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Close, stringResource(android.R.string.cancel))
                }
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.extra_info_preview), Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                ExtraInfoPreview(Modifier.padding(horizontal = 20.dp).clip(MaterialTheme.shapes.large),
                    sampleWhenEmpty = true, showBackdrop = true)
                ExpressiveConnectedRow(
                    labels = labels.map { stringResource(it) },
                    selected = selected,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    onSelect = { index ->
                        if (index == 0) enabled = false else {
                            background = if (index == 2) InfoBackground.LIQUID_GLASS else InfoBackground.AX_BLUR
                            enabled = true
                        }
                    },
                )
                if (selected == 2) {
                    Text(stringResource(R.string.info_background_glass_help), Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ClockGlassControls(style, surface = true, defaultStyle = InfoBackground.GLASS_DEFAULT) {
                        raw = it.normalized().toJson().toString()
                    }
                }
            }
        }
    }
}
