package org.lunaris.lockstyles.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.lunaris.lockstyles.R
import org.lunaris.lockstyles.settings.ui.components.squishable

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (primary) {
        Button(
            onClick,
            shapes = ButtonDefaults.shapes(),
            enabled = enabled,
            modifier = modifier.heightIn(min = 52.dp),
            content = content,
        )
    } else {
        FilledTonalButton(
            onClick,
            shapes = ButtonDefaults.shapes(),
            enabled = enabled,
            modifier = modifier.heightIn(min = 52.dp),
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val content: @Composable () -> Unit = { Icon(icon, label, Modifier.size(24.dp)) }
    if (primary) {
        FilledIconButton(
            onClick,
            shapes = IconButtonDefaults.shapes(),
            enabled = enabled,
            modifier = modifier.size(56.dp),
            content = content,
        )
    } else {
        FilledTonalIconButton(
            onClick,
            shapes = IconButtonDefaults.shapes(),
            enabled = enabled,
            modifier = modifier.size(56.dp),
            content = content,
        )
    }
}

@Composable
internal fun ExpressiveFloatingToolbar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

internal fun expressiveSwitchThumb(checked: Boolean): @Composable () -> Unit = {
    Icon(
        imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
        contentDescription = null,
        modifier = Modifier.size(SwitchDefaults.IconSize),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AdjustableSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    increment: Float = 1f,
    enabled: Boolean = true,
    colors: SliderColors = SliderDefaults.colors(),
    sliderModifier: Modifier = Modifier,
) {
    val current = value.coerceIn(valueRange)
    val step = if (steps > 0) (valueRange.endInclusive - valueRange.start) / (steps + 1)
        else increment
    val decreased = (current - step).coerceIn(valueRange)
    val increased = (current + step).coerceIn(valueRange)
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalIconButton(
            onClick = { onValueChange(decreased) },
            enabled = enabled && decreased < current,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(Icons.Rounded.Remove, stringResource(R.string.slider_decrease, label), Modifier.size(18.dp))
        }
        Slider(
            value = current,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).then(sliderModifier),
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = colors,
        )
        FilledTonalIconButton(
            onClick = { onValueChange(increased) },
            enabled = enabled && increased > current,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(Icons.Rounded.Add, stringResource(R.string.slider_increase, label), Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveChoices(
    labels: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val toggleColors = ToggleButtonDefaults.toggleButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .padding(horizontal = horizontalPadding, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        labels.forEachIndexed { index, label ->
            val checked = selected == index
            ToggleButton(
                checked = checked,
                onCheckedChange = { if (enabled) onSelect(index) },
                enabled = enabled,
                colors = toggleColors,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier =
                    Modifier.heightIn(min = 48.dp).semantics {
                        role = Role.RadioButton
                        this.selected = checked
                    },
                shapes =
                    when {
                        labels.size == 1 -> ToggleButtonDefaults.shapes()
                        index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        index == labels.lastIndex ->
                            ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
            ) {
                AnimatedVisibility(
                    visible = checked,
                    enter = fadeIn(effects) + scaleIn(spatial, initialScale = 0.6f),
                    exit = fadeOut(effects) + scaleOut(spatial, targetScale = 0.6f),
                ) {
                    Row {
                        Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveConnectedRow(
    labels: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    itemEnabled: (Int) -> Boolean = { true },
    onSelect: (Int) -> Unit,
) {
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val toggleColors = ToggleButtonDefaults.toggleButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        labels.forEachIndexed { index, label ->
            val checked = selected == index
            val isEnabled = enabled && itemEnabled(index)
            ToggleButton(
                checked = checked,
                onCheckedChange = { if (isEnabled) onSelect(index) },
                enabled = isEnabled,
                colors = toggleColors,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier =
                    Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                        role = Role.RadioButton
                        this.selected = checked
                    },
                shapes =
                    when {
                        labels.size == 1 -> ToggleButtonDefaults.shapes()
                        index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        index == labels.lastIndex ->
                            ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
            ) {
                AnimatedVisibility(
                    visible = checked,
                    enter = fadeIn(effects) + scaleIn(spatial, initialScale = 0.6f),
                    exit = fadeOut(effects) + scaleOut(spatial, targetScale = 0.6f),
                ) {
                    Row {
                        Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ColorSwatch(
    color: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description =
        if (color == null) stringResource(R.string.clock_maker_automatic)
        else stringResource(R.string.gallery_colour_value, "%08X".format(color))
    val colors = MaterialTheme.colorScheme
    val spatialDp = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    val spatialFloat = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val effectsColor = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    val effectsFloat = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val interactions = remember { MutableInteractionSource() }
    val corner by animateDpAsState(if (selected) 16.dp else 26.dp, spatialDp, label = "swatch_corner")
    val borderWidth by animateDpAsState(if (selected) 3.dp else 1.dp, spatialDp, label = "swatch_border")
    val borderColor by animateColorAsState(
        if (selected) colors.primary else colors.outlineVariant,
        effectsColor,
        label = "swatch_border_color",
    )
    val outerShape = RoundedCornerShape(corner)
    val innerShape = RoundedCornerShape((corner - 4.dp).coerceAtLeast(10.dp))
    Box(
        modifier
            .size(52.dp)
            .squishable(interactions, scaleDown = 0.92f)
            .clip(outerShape)
            .selectable(
                selected = selected,
                interactionSource = interactions,
                indication = androidx.compose.foundation.LocalIndication.current,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { contentDescription = description }
            .padding(4.dp)
            .border(borderWidth, borderColor, outerShape)
            .padding(5.dp)
            .clip(innerShape)
            .background(
                if (color == null)
                    Brush.sweepGradient(
                        listOf(
                            Color.Red,
                            Color.Yellow,
                            Color.Green,
                            Color.Cyan,
                            Color.Blue,
                            Color.Magenta,
                            Color.Red,
                        )
                    )
                else SolidColor(Color(color))
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(spatialFloat, initialScale = 0.5f) + fadeIn(effectsFloat),
            exit = scaleOut(spatialFloat, targetScale = 0.5f) + fadeOut(effectsFloat),
        ) {
            Box(
                Modifier.size(22.dp).background(colors.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = colors.onPrimary)
            }
        }
    }
}

@Composable
internal fun SelectionBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(24.dp)
            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Check,
            null,
            Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveLoadingIndicator(modifier: Modifier = Modifier) {
    LoadingIndicator(
        modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
    )
}
