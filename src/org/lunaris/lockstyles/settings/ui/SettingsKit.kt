/*
 * SPDX-FileCopyrightText: 2024-2025 Lunaris AOSP
 * SPDX-FileCopyrightText: 2026 Lunaris-AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles.settings.ui

import androidx.compose.foundation.background
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.lunaris.lockstyles.settings.LockSetting
import org.lunaris.lockstyles.ui.ColorPickerDialog
import org.lunaris.lockstyles.ui.LockStylesBottomSheet
import org.lunaris.lockstyles.settings.state
import org.lunaris.lockstyles.settings.ui.components.HapticFeedbackHelper.HapticIntensity
import org.lunaris.lockstyles.settings.ui.components.ModernSettingSlider
import org.lunaris.lockstyles.settings.ui.components.ModernSettingSwitch
import org.lunaris.lockstyles.settings.ui.components.ModernSettingsCard
import org.lunaris.lockstyles.settings.ui.components.rememberHapticFeedback
import org.lunaris.lockstyles.settings.ui.components.squishable

/*
 * The settings are laid out as LunarisDolby's: pages on the surface container under a plain
 * top bar, each group an extra-large card built from its components.
 */

/** Fades a row to 40% while it's disabled, with the theme's effects spring. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.dimmed(enabled: Boolean): Modifier {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f,
        MaterialTheme.motionScheme.defaultEffectsSpec(), label = "dim")
    return graphicsLayer { this.alpha = alpha }
}

/** The theme's primary colour, for the few places that colour by hand. */
val Accent: Color
    @Composable get() = MaterialTheme.colorScheme.primary

/** The tile look for picker items: tonal, or primary with an outline when chosen. */
@Composable
fun Modifier.pickTile(on: Boolean): Modifier {
    val shape = MaterialTheme.shapes.large
    val colors = MaterialTheme.colorScheme
    return clip(shape)
        .background(if (on) colors.primaryContainer else colors.surfaceVariant.copy(alpha = 0.6f))
        .border(if (on) 2.dp else 0.dp, if (on) colors.primary else Color.Transparent, shape)
}

/** A settings page: a large title that folds into the bar as it scrolls, then the cards. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsPage(title: String, onBack: () -> Unit, inSheet: Boolean = false,
        content: LazyListScope.() -> Unit) {
    if (inSheet) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.75f).dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Text(title, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
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
                    Text(title, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
                scrollBehavior = scroll,
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

/** A titled group with its card's icon. */
fun LazyListScope.group(title: String?, icon: ImageVector,
        rows: List<@Composable (RowShape) -> Unit>) = group(title, rows, icon)

/** A group of rows, as one of LunarisDolby's settings cards. */
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
    val body: @Composable () -> Unit = {
        Column(Modifier.animateContentSize(spatial),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEachIndexed { i, row ->
                row(RowShape(first = i == 0, last = i == rows.lastIndex))
            }
        }
    }
    if (title != null) {
        ModernSettingsCard(title = title, icon = icon) { body() }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceBright),
        ) {
            Box(Modifier.padding(20.dp)) { body() }
        }
    }
}

/** Where a row sits in its group. */
class RowShape(val first: Boolean, val last: Boolean)

/** A tonal tile, as LunarisDolby's switches use, for rows that open something. */
@Composable
private fun Tile(
    enabled: Boolean = true,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val haptic = rememberHapticFeedback()
    val scope = rememberCoroutineScope()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
            .squishable(enabled = enabled && onClick != null)
            .clip(MaterialTheme.shapes.large)
            .then(if (onClick != null && enabled) Modifier.clickable {
                scope.launch { haptic.performHaptic(HapticIntensity.TICK) }
                onClick()
            } else Modifier)
            .dimmed(enabled),
    ) {
        Box(Modifier.heightIn(min = 56.dp).padding(16.dp),
            contentAlignment = Alignment.CenterStart) { content() }
    }
}

@Composable
private fun Labels(title: String, summary: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface)
        if (!summary.isNullOrEmpty()) {
            Text(summary, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The on/off switch at the top of a page, as Singularity's MainSwitchPreference. */
fun LazyListScope.mainSwitch(title: String, setting: LockSetting<Boolean>,
        onChange: (Boolean) -> Unit = {}) {
    item {
        var on by setting.state()
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = if (on) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceBright),
        ) {
            Box(Modifier.padding(12.dp)) {
                ModernSettingSwitch(
                    title = title,
                    subtitle = "",
                    checked = on,
                    onCheckedChange = { on = it; onChange(it) },
                )
            }
        }
    }
}

fun switchRow(
    title: String,
    summary: String?,
    setting: LockSetting<Boolean>,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit = {},
): @Composable (RowShape) -> Unit = {
    var on by setting.state()
    ModernSettingSwitch(
        title = title,
        subtitle = summary.orEmpty(),
        checked = on,
        onCheckedChange = { if (enabled) { on = it; onChange(it) } },
        modifier = Modifier.dimmed(enabled),
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
): @Composable (RowShape) -> Unit = {
    var value by setting.state()
    // Shown at once, written once it settles: every tick would restart SystemUI's observers.
    var shown by remember(value) { mutableIntStateOf(value.coerceIn(range)) }
    LaunchedEffect(shown) {
        if (shown == value) return@LaunchedEffect
        delay(300)
        value = shown
    }
    val steps = ((range.last - range.first) / step - 1).coerceAtLeast(0)
    Column(Modifier.dimmed(enabled).padding(horizontal = 4.dp)) {
        ModernSettingSlider(
            title = title,
            value = shown,
            onValueChange = { if (enabled) shown = (it / step).toInt() * step },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = if (steps in 1..60) steps else 0,
            valueLabel = { "$it$unit" },
        )
        if (!summary.isNullOrEmpty()) {
            Text(summary, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A list preference, as LunarisDolby's ModernSettingSelector: a pill that drops down. */
fun <T> choiceRow(
    title: String,
    setting: LockSetting<T>,
    entries: List<Pair<T, String>>,
    enabled: Boolean = true,
    onChange: (T) -> Unit = {},
): @Composable (RowShape) -> Unit = {
    var value by setting.state()
    ChoiceRow(title, value, entries, enabled) { value = it; onChange(it) }
}

/** A choice kept by the caller rather than in a setting. */
fun <T> localChoiceRow(
    title: String,
    value: T,
    entries: List<Pair<T, String>>,
    enabled: Boolean = true,
    onChange: (T) -> Unit,
): @Composable (RowShape) -> Unit = {
    ChoiceRow(title, value, entries, enabled, onChange)
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    value: T,
    entries: List<Pair<T, String>>,
    enabled: Boolean,
    onChange: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = rememberHapticFeedback()
    val scope = rememberCoroutineScope()
    Row(
        Modifier.fillMaxWidth().dimmed(enabled)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Box {
            Surface(
                onClick = {
                    if (!enabled) return@Surface
                    scope.launch { haptic.performHaptic(HapticIntensity.TICK) }
                    expanded = true
                },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(entries.firstOrNull { it.first == value }?.second.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.ArrowDropDown, null,
                        tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                entries.forEach { (v, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(label, color = if (v == value) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface)
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

fun navRow(
    title: String,
    summary: String?,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
): @Composable (RowShape) -> Unit = {
    Tile(enabled, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
            }
            Labels(title, summary, Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val SWATCHES = listOf(0xFFFFFFFF, 0xFFB9A7FF, 0xFF0A84FF, 0xFF64D2FF, 0xFF30D158,
    0xFFFFD60A, 0xFFFF9F0A, 0xFFFF453A, 0xFFFF6482, 0xFFBF5AF2, 0xFF8E8E93, 0xFF1C1C1E)
    .map { it.toInt() }

/** A colour preference: swatches, or any colour as hex. */
@OptIn(ExperimentalMaterial3Api::class)
fun colorRow(
    title: String,
    setting: LockSetting<Int>,
    enabled: Boolean = true,
    usePicker: Boolean = false,
): @Composable (RowShape) -> Unit = {
    var value by setting.state()
    var open by remember { mutableStateOf(false) }
    Tile(enabled, onClick = { open = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Labels(title, "#%08X".format(value), Modifier.weight(1f))
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color(value))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
        }
    }
    if (open && usePicker) {
        ColorPickerDialog(value, onDismiss = { open = false }) {
            value = it
            open = false
        }
    } else if (open) {
        LockStylesBottomSheet(onDismissRequest = { open = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            var hex by remember { mutableStateOf("%08X".format(value)) }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                SWATCHES.chunked(6).forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        line.forEach { c ->
                            Box(Modifier.size(44.dp).clip(CircleShape).background(Color(c))
                                .border(if (c == value) 3.dp else 1.dp,
                                    if (c == value) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable { value = c; hex = "%08X".format(c) })
                        }
                    }
                }
                Surface(shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("#", style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BasicTextField(
                            value = hex,
                            onValueChange = { text ->
                                hex = text.uppercase().filter { it in "0123456789ABCDEF" }.take(8)
                                hex.takeIf { it.length == 6 || it.length == 8 }?.let {
                                    value = (if (it.length == 6) "FF$it" else it).toLong(16)
                                        .toInt()
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface),
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

fun footer(text: String): @Composable (RowShape) -> Unit = {
    Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A plain action row, e.g. "Extract now". */
fun actionRow(title: String, summary: String?, enabled: Boolean = true,
        onClick: () -> Unit): @Composable (RowShape) -> Unit = {
    Tile(enabled, onClick = onClick) { Labels(title, summary) }
}

@Composable
fun Spacing() = Spacer(Modifier.height(8.dp))
