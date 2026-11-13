/*
 * Copyright (C) 2026 RisingOS (revived) Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.android.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lunaris.lockstyles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.lunaris.lockstyles.R

@Composable
internal fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

@Composable
internal fun innerContainer(dark: Boolean = isDarkSurface()): Color =
    if (dark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

@Composable
internal fun cardContainer(dark: Boolean = isDarkSurface(), enabled: Boolean = false): Color = when {
    enabled && dark -> Color.White.copy(alpha = 0.08f)
    enabled -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    dark -> Color.White.copy(alpha = 0.05f)
    else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
}

@Composable
internal fun cardText(dark: Boolean) = if (dark) Color.White else MaterialTheme.colorScheme.onSurface

@Composable
internal fun cardSubText(dark: Boolean) =
    if (dark) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant

@Composable
internal fun SectionHeader(title: String, dark: Boolean = isDarkSurface()) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
        color = cardText(dark), modifier = Modifier.padding(top = 4.dp))
}

@Composable
internal fun ConfigCard(
    title: String,
    subtitle: String = "",
    icon: ImageVector? = null,
    enabled: Boolean = false,
    dark: Boolean = isDarkSurface(),
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val body: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Surface(shape = CircleShape, modifier = Modifier.size(48.dp), color = when {
                    dark -> Color.White.copy(alpha = 0.1f)
                    enabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(24.dp), tint = when {
                            dark -> Color.White
                            enabled -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        })
                    }
                }
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    color = cardText(dark))
                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cardSubText(dark))
                }
            }
            trailing()
        }
    }
    if (onClick != null) {
        Surface(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
            color = cardContainer(dark, enabled), content = body)
    } else {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
            color = cardContainer(dark, enabled), content = body)
    }
}

@Composable
internal fun SwitchCard(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String = "",
    icon: ImageVector? = null,
    dark: Boolean = isDarkSurface(),
) {
    ConfigCard(title, subtitle, icon, enabled = checked, dark = dark, onClick = { onChange(!checked) }) {
        Switch(checked, onChange, colors = SwitchDefaults.colors(
            checkedThumbColor = if (dark) Color.White else MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = if (dark) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary))
    }
}

@Composable
internal fun SliderCard(
    title: String,
    value: Int,
    range: IntRange,
    unit: String,
    defaultValue: Int,
    displayValue: String? = null,
    dark: Boolean = isDarkSurface(),
    onChange: (Int) -> Unit,
) {
    val shown = displayValue ?: "$value$unit"
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = cardContainer(dark)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    color = cardText(dark), modifier = Modifier.weight(1f))
                Text(shown, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                    color = if (dark) Color.White else MaterialTheme.colorScheme.primary)
                if (value != defaultValue) {
                    TextButton(onClick = { onChange(defaultValue) }) {
                        Text(stringResource(R.string.gallery_clock_reset))
                    }
                }
            }
            Slider(value.coerceIn(range).toFloat(), { onChange(it.roundToInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = title
                    stateDescription = shown
                },
                colors = SliderDefaults.colors(
                    thumbColor = if (dark) Color.White else MaterialTheme.colorScheme.primary,
                    activeTrackColor = if (dark) Color.White else MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = if (dark) Color.White.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)))
        }
    }
}

@Composable
internal fun ChoiceCard(
    labels: List<String>,
    selected: Int,
    title: String? = null,
    dark: Boolean = isDarkSurface(),
    onSelect: (Int) -> Unit,
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = cardContainer(dark)) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    color = cardText(dark))
                Spacer(Modifier.height(12.dp))
            }
            labels.chunked(3).forEachIndexed { row, chunk ->
                if (row > 0) Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunk.forEachIndexed { i, label ->
                        val index = row * 3 + i
                        val on = index == selected
                        Surface(onClick = { onSelect(index) }, shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.weight(1f), color = when {
                                on && dark -> Color.White.copy(alpha = 0.2f)
                                on -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                dark -> Color.White.copy(alpha = 0.05f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }) {
                            Box(Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center) {
                                Text(label, style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1, color = when {
                                        on && dark -> Color.White
                                        on -> MaterialTheme.colorScheme.primary
                                        dark -> Color.White.copy(alpha = 0.7f)
                                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    })
                            }
                        }
                    }
                    repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
internal fun ImmersiveEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = generateSequence(view.context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<android.app.Activity>().firstOrNull()?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        if (enabled) {
            controller?.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose { if (enabled) controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }
}
