package org.lunaris.lockstyles.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import org.lunaris.lockstyles.R

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
    val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveChoices(
    labels: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        labels.forEachIndexed { index, label ->
            ToggleButton(
                checked = selected == index,
                onCheckedChange = { onSelect(index) },
                modifier =
                    Modifier.heightIn(min = 48.dp).semantics {
                        role = Role.RadioButton
                        this.selected = selected == index
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
                Box(Modifier.size(18.dp)) {
                    if (selected == index) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

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
    Box(
        modifier
            .size(52.dp)
            .clip(CircleShape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(4.dp)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) colors.primary else colors.outlineVariant,
                CircleShape,
            )
            .padding(5.dp)
            .clip(CircleShape)
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
        if (selected) {
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
        modifier.size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Check,
            null,
            Modifier.size(16.dp),
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
