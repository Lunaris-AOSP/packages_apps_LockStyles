/*
 * SPDX-FileCopyrightText: 2024-2025 Lunaris AOSP
 * SPDX-FileCopyrightText: 2026 Lunaris-AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles.settings.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.lunaris.lockstyles.R
import org.lunaris.lockstyles.settings.ExtraInfoSettings
import org.lunaris.lockstyles.settings.LocalExtraInfoSettings
import org.lunaris.lockstyles.settings.LockSetting
import org.lunaris.lockstyles.settings.state
import org.lunaris.lockstyles.settings.ui.components.HapticFeedbackHelper.HapticIntensity
import org.lunaris.lockstyles.settings.ui.components.ModernSettingSlider
import org.lunaris.lockstyles.settings.ui.components.ModernSettingSwitch
import org.lunaris.lockstyles.settings.ui.components.SettingsSectionHeader
import org.lunaris.lockstyles.settings.ui.components.rememberHapticFeedback
import org.lunaris.lockstyles.settings.ui.components.squishable
import org.lunaris.lockstyles.ui.ColorPickerDialog
import org.lunaris.lockstyles.ui.ColorSwatch
import org.lunaris.lockstyles.ui.ExpressiveChoices
import org.lunaris.lockstyles.ui.ExpressiveConnectedRow
import org.lunaris.lockstyles.ui.LockStylesBottomSheet
import org.lunaris.lockstyles.ui.cardContainer
import org.lunaris.lockstyles.ui.innerContainer

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.dimmed(enabled: Boolean): Modifier {
    val alpha by animateFloatAsState(
        if (enabled) 1f else 0.4f,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "dim",
    )
    return graphicsLayer { this.alpha = alpha }
}

val Accent: Color
    @Composable get() = MaterialTheme.colorScheme.primary

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.pickTile(on: Boolean): Modifier {
    val colors = MaterialTheme.colorScheme
    val spatialDp = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    val corner by animateDpAsState(if (on) 22.dp else 16.dp, spatialDp, label = "pick_corner")
    val borderWidth by animateDpAsState(if (on) 2.5.dp else 0.dp, spatialDp, label = "pick_border")
    val borderColor by animateColorAsState(
        if (on) colors.primary else Color.Transparent,
        effects,
        label = "pick_border_color",
    )
    val container by animateColorAsState(
        if (on) colors.secondaryContainer else innerContainer(),
        effects,
        label = "selection_surface",
    )
    val shape = RoundedCornerShape(corner)
    return clip(shape)
        .background(container)
        .border(borderWidth, borderColor, shape)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    inSheet: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    if (inSheet) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.75f).dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
        return
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.gallery_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scroll,
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

fun LazyListScope.group(
    title: String?,
    icon: ImageVector,
    rows: List<@Composable (RowShape) -> Unit>,
) = group(title, rows, icon)

fun LazyListScope.group(
    title: String?,
    rows: List<@Composable (RowShape) -> Unit>,
    icon: ImageVector = Icons.Rounded.Tune,
) {
    if (rows.isEmpty()) return
    item { Group(title, rows, icon) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Group(
    title: String?,
    rows: List<@Composable (RowShape) -> Unit>,
    icon: ImageVector = Icons.Rounded.Tune,
) {
    if (rows.isEmpty()) return
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    Column(
        Modifier.fillMaxWidth().animateContentSize(spatial),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) SettingsSectionHeader(title, icon)
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            rows.forEachIndexed { i, row ->
                row(RowShape(first = i == 0, last = i == rows.lastIndex))
            }
        }
    }
}

class RowShape(val first: Boolean, val last: Boolean) {
    fun toShape(): Shape = when {
        first && last -> RoundedCornerShape(24.dp)
        first -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        last -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(6.dp)
    }
}

@Composable
private fun Tile(
    rowShape: RowShape,
    enabled: Boolean = true,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val haptic = rememberHapticFeedback()
    val scope = rememberCoroutineScope()
    val interactions = remember { MutableInteractionSource() }
    val shape = rowShape.toShape()
    Surface(
        shape = shape,
        color = cardContainer(),
        modifier = Modifier
            .fillMaxWidth()
            .squishable(interactions, enabled = enabled && onClick != null)
            .clip(shape)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactions,
                    indication = LocalIndication.current,
                    enabled = enabled,
                    role = Role.Button,
                ) {
                    scope.launch { haptic.performHaptic(HapticIntensity.TICK) }
                    onClick()
                } else Modifier
            )
            .dimmed(enabled),
    ) {
        Box(
            Modifier.heightIn(min = 56.dp).padding(20.dp),
            contentAlignment = Alignment.CenterStart,
        ) { content() }
    }
}

@Composable
private fun Labels(title: String, summary: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!summary.isNullOrEmpty()) {
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

fun LazyListScope.mainSwitch(
    title: String,
    setting: LockSetting<Boolean>,
    onChange: (Boolean) -> Unit = {},
) {
    item {
        var on by setting.state()
        ModernSettingSwitch(
            title = title,
            subtitle = "",
            checked = on,
            onCheckedChange = { on = it; onChange(it) },
            shape = MaterialTheme.shapes.extraLarge,
        )
    }
}

fun switchRow(
    title: String,
    summary: String?,
    setting: LockSetting<Boolean>,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit = {},
): @Composable (RowShape) -> Unit = { rowShape ->
    var on by setting.state()
    ModernSettingSwitch(
        title = title,
        subtitle = summary.orEmpty(),
        checked = on,
        onCheckedChange = { if (enabled) { on = it; onChange(it) } },
        modifier = Modifier.dimmed(enabled),
        enabled = enabled,
        shape = rowShape.toShape(),
    )
}

fun sliderRow(
    title: String,
    summary: String?,
    setting: LockSetting<Int>,
    range: IntRange,
    step: Int = 1,
    unit: String = "",
    enabled: Boolean = true,
): @Composable (RowShape) -> Unit = { rowShape ->
    var value by setting.state()
    val isDraft = LocalExtraInfoSettings.current != null && ExtraInfoSettings.contains(setting)
    var shown by remember(value) { mutableIntStateOf(value.coerceIn(range)) }
    LaunchedEffect(shown, isDraft) {
        if (isDraft || shown == value) return@LaunchedEffect
        delay(300)
        value = shown
    }
    val steps = ((range.last - range.first) / step - 1).coerceAtLeast(0)
    Surface(
        Modifier.fillMaxWidth().dimmed(enabled),
        shape = rowShape.toShape(),
        color = cardContainer(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ModernSettingSlider(
                title = title,
                value = shown,
                onValueChange = {
                    if (enabled) {
                        shown = (it / step).toInt() * step
                        if (isDraft) value = shown
                    }
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = if (steps in 1..60) steps else 0,
                increment = step.toFloat(),
                valueLabel = { "$it$unit" },
                enabled = enabled,
            )
            if (!summary.isNullOrEmpty()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun <T> choiceRow(
    title: String,
    setting: LockSetting<T>,
    entries: List<Pair<T, String>>,
    enabled: Boolean = true,
    onChange: (T) -> Unit = {},
): @Composable (RowShape) -> Unit = { rowShape ->
    var value by setting.state()
    ChoiceRow(title, value, entries, enabled, rowShape) { value = it; onChange(it) }
}

fun <T> localChoiceRow(
    title: String,
    value: T,
    entries: List<Pair<T, String>>,
    enabled: Boolean = true,
    onChange: (T) -> Unit,
): @Composable (RowShape) -> Unit = { rowShape ->
    ChoiceRow(title, value, entries, enabled, rowShape, onChange)
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    value: T,
    entries: List<Pair<T, String>>,
    enabled: Boolean,
    rowShape: RowShape,
    onChange: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = rememberHapticFeedback()
    val scope = rememberCoroutineScope()
    val interactions = remember { MutableInteractionSource() }
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    Surface(
        Modifier.fillMaxWidth().dimmed(enabled),
        shape = rowShape.toShape(),
        color = cardContainer(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (entries.size in 2..3 && entries.all { it.second.length <= 12 }) {
                ExpressiveConnectedRow(
                    labels = entries.map { it.second },
                    selected = entries.indexOfFirst { it.first == value },
                    enabled = enabled,
                    onSelect = { index ->
                        scope.launch { haptic.performHaptic(HapticIntensity.CLICK) }
                        onChange(entries[index].first)
                    },
                )
            } else if (entries.size in 2..3) {
                ExpressiveChoices(
                    labels = entries.map { it.second },
                    selected = entries.indexOfFirst { it.first == value },
                    horizontalPadding = 0.dp,
                    enabled = enabled,
                    onSelect = { index ->
                        scope.launch { haptic.performHaptic(HapticIntensity.CLICK) }
                        onChange(entries[index].first)
                    },
                )
            } else {
                Box(Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = {
                            if (!enabled) return@Surface
                            scope.launch { haptic.performHaptic(HapticIntensity.TICK) }
                            expanded = true
                        },
                        enabled = enabled,
                        interactionSource = interactions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .squishable(interactions, enabled = enabled)
                            .semantics { contentDescription = title },
                        shape = MaterialTheme.shapes.medium,
                        color = innerContainer(),
                    ) {
                        Row(
                            Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                entries.firstOrNull { it.first == value }?.second.orEmpty(),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Rounded.ArrowDropDown,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                        entries.forEach { (v, label) ->
                            DropdownMenuItem(
                                enabled = enabled,
                                modifier = Modifier.semantics { selected = v == value },
                                trailingIcon = if (v == value) {
                                    { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) }
                                } else null,
                                text = {
                                    Text(
                                        label,
                                        color = if (v == value) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                },
                                onClick = {
                                    scope.launch { haptic.performHaptic(HapticIntensity.CLICK) }
                                    expanded = false
                                    onChange(v)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

fun navRow(
    title: String,
    summary: String?,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
): @Composable (RowShape) -> Unit = { rowShape ->
    Tile(rowShape, enabled, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
            }
            Labels(title, summary, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val SWATCHES = listOf(
    0xFFFFFFFF, 0xFFB9A7FF, 0xFF0A84FF, 0xFF64D2FF, 0xFF30D158,
    0xFFFFD60A, 0xFFFF9F0A, 0xFFFF453A, 0xFFFF6482, 0xFFBF5AF2, 0xFF8E8E93, 0xFF1C1C1E,
).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class)
fun colorRow(
    title: String,
    setting: LockSetting<Int>,
    enabled: Boolean = true,
    usePicker: Boolean = false,
): @Composable (RowShape) -> Unit = { rowShape ->
    var value by setting.state()
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) open = false }
    Tile(rowShape, enabled, onClick = { open = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Labels(title, "#%08X".format(value), Modifier.weight(1f))
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(value))
                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        }
    }
    if (open && enabled && usePicker) {
        ColorPickerDialog(value, onDismiss = { open = false }) {
            value = it
            open = false
        }
    } else if (open && enabled) {
        LockStylesBottomSheet(
            onDismissRequest = { open = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            var hex by remember { mutableStateOf("%08X".format(value)) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                SWATCHES.chunked(4).forEach { line ->
                    Row(
                        Modifier.fillMaxWidth().selectableGroup(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        line.forEach { c ->
                            ColorSwatch(c, c == value, onClick = { value = c; hex = "%08X".format(c) })
                        }
                    }
                }
                Surface(shape = MaterialTheme.shapes.large, color = cardContainer()) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "#",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        BasicTextField(
                            value = hex,
                            onValueChange = { text ->
                                hex = text.uppercase().filter { it in "0123456789ABCDEF" }.take(8)
                                hex.takeIf { it.length == 6 || it.length == 8 }?.let {
                                    value = (if (it.length == 6) "FF$it" else it).toLong(16).toInt()
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f).padding(start = 4.dp),
                        )
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Color(value)))
                    }
                }
            }
        }
    }
}

fun footer(text: String): @Composable (RowShape) -> Unit = { rowShape ->
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = rowShape.toShape(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Rounded.Info,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp).padding(top = 2.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun actionRow(
    title: String,
    summary: String?,
    enabled: Boolean = true,
    onClick: () -> Unit,
): @Composable (RowShape) -> Unit = { rowShape ->
    Tile(rowShape, enabled, onClick = onClick) { Labels(title, summary) }
}

@Composable
fun Spacing() = Spacer(Modifier.height(8.dp))
