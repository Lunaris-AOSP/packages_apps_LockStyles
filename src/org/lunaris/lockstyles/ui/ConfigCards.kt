/*
 * Copyright (C) 2026 RisingOS (revived) Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lunaris.lockstyles.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.lunaris.lockstyles.R
import org.lunaris.lockstyles.settings.ui.components.squishable

@Composable
internal fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

@Composable
internal fun innerContainer(dark: Boolean = isDarkSurface()): Color =
    MaterialTheme.colorScheme.surfaceContainerHigh

@Composable
internal fun cardContainer(dark: Boolean = isDarkSurface(), enabled: Boolean = false): Color =
    if (enabled) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surfaceContainer

@Composable
internal fun cardText(dark: Boolean) = MaterialTheme.colorScheme.onSurface

@Composable
internal fun cardSubText(dark: Boolean) = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
internal fun SectionHeader(title: String, dark: Boolean = isDarkSurface()) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ConfigCard(
    title: String,
    subtitle: String = "",
    icon: ImageVector? = null,
    enabled: Boolean = false,
    dark: Boolean = isDarkSurface(),
    shape: Shape = MaterialTheme.shapes.large,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    val spatialDp = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    val interactions = remember { MutableInteractionSource() }
    val container by animateColorAsState(cardContainer(dark, enabled), effects, label = "card_container")
    val iconContainer by animateColorAsState(
        if (enabled) colors.primaryContainer else colors.surfaceContainerHigh,
        effects,
        label = "card_icon_container",
    )
    val iconTint by animateColorAsState(
        if (enabled) colors.onPrimaryContainer else colors.onSurfaceVariant,
        effects,
        label = "card_icon_tint",
    )
    val iconCorner by animateDpAsState(if (enabled) 16.dp else 24.dp, spatialDp, label = "card_icon_corner")
    val titleColor by animateColorAsState(
        if (enabled) colors.onSecondaryContainer else colors.onSurface,
        effects,
        label = "card_title",
    )
    val subtitleColor by animateColorAsState(
        if (enabled) colors.onSecondaryContainer.copy(alpha = 0.78f) else colors.onSurfaceVariant,
        effects,
        label = "card_subtitle",
    )
    val body: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Surface(
                    shape = RoundedCornerShape(iconCorner),
                    modifier = Modifier.size(48.dp),
                    color = iconContainer,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(24.dp), tint = iconTint)
                    }
                }
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    color = titleColor)
                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
                }
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().squishable(interactions),
            shape = shape,
            color = container,
            interactionSource = interactions,
            content = body,
        )
    } else {
        Surface(Modifier.fillMaxWidth(), shape = shape, color = container, content = body)
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
    shape: Shape = MaterialTheme.shapes.large,
) {
    ConfigCard(title, subtitle, icon, enabled = checked, dark = dark, shape = shape, onClick = { onChange(!checked) }) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            thumbContent = expressiveSwitchThumb(checked),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SliderCard(
    title: String,
    value: Int,
    range: IntRange,
    unit: String,
    defaultValue: Int,
    displayValue: String? = null,
    dark: Boolean = isDarkSurface(),
    shape: Shape = MaterialTheme.shapes.large,
    onChange: (Int) -> Unit,
) {
    val shown = displayValue ?: "$value$unit"
    Surface(Modifier.fillMaxWidth(), shape = shape, color = cardContainer(dark)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        shown,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                if (value != defaultValue) {
                    TextButton(
                        onClick = { onChange(defaultValue) },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.gallery_clock_reset))
                    }
                }
            }
            AdjustableSlider(
                value.coerceIn(range).toFloat(),
                { onChange(it.roundToInt()) },
                title,
                valueRange = range.first.toFloat()..range.last.toFloat(),
                sliderModifier = Modifier.semantics {
                    contentDescription = title
                    stateDescription = shown
                },
                colors = SliderDefaults.colors(),
            )
        }
    }
}

@Composable
internal fun ChoiceCard(
    labels: List<String>,
    selected: Int,
    title: String? = null,
    dark: Boolean = isDarkSurface(),
    shape: Shape = MaterialTheme.shapes.large,
    onSelect: (Int) -> Unit,
) {
    Surface(Modifier.fillMaxWidth(), shape = shape, color = cardContainer(dark)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            val maxLen = labels.maxOfOrNull { it.length } ?: 0
            val perRow = when {
                labels.size <= 2 -> labels.size.coerceAtLeast(1)
                labels.size == 3 && maxLen <= 10 -> 3
                labels.size == 4 && maxLen <= 5 -> 4
                labels.size == 4 -> 2
                maxLen <= 9 -> 3
                else -> 2
            }
            val chunks = labels.chunked(perRow)
            var offset = 0
            chunks.forEach { chunk ->
                val base = offset
                ExpressiveConnectedRow(
                    labels = chunk,
                    selected = if (selected in base until base + chunk.size) selected - base else -1,
                    onSelect = { onSelect(base + it) },
                )
                offset += chunk.size
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
