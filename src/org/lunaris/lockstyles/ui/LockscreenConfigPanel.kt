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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.lunaris.lockstyles.settings.ui.components.squishable

enum class CustomizerTab { WALLPAPER, CLOCK, SHORTCUTS, DEPTH, WIDGETS, AVATAR }

data class CustomizerTabSpec(val tab: CustomizerTab, val icon: ImageVector, val label: String)

private val DETENTS = floatArrayOf(0.42f, 0.62f, 0.88f)
private const val START_DETENT = 1

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LockscreenConfigPanel(
    visible: Boolean,
    tabs: List<CustomizerTabSpec>,
    selectedTab: CustomizerTab,
    onTabChange: (CustomizerTab) -> Unit,
    onDismiss: () -> Unit,
    selfScrolling: (CustomizerTab) -> Boolean = { false },
    content: @Composable (CustomizerTab) -> Unit,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val screenPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    var detent by rememberSaveable { mutableIntStateOf(START_DETENT) }
    var dragging by remember { mutableStateOf(false) }
    val height = remember { Animatable(screenPx * DETENTS[START_DETENT]) }
    val shift = remember { Animatable(if (visible) 0f else screenPx) }
    val spatialFloat = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val fastSpatialFloat = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val spatialOffset = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    LaunchedEffect(visible, screenPx) {
        if (visible) {
            height.snapTo(screenPx * DETENTS[detent])
            shift.animateTo(0f, spatialFloat)
        } else shift.animateTo(height.value + with(density) { 50.dp.toPx() }, spatialFloat)
    }
    if (!visible && shift.value >= height.value) return

    val dragState = rememberDraggableState { delta ->
        scope.launch {
            height.snapTo((height.value - delta).coerceIn(screenPx * 0.3f, screenPx * DETENTS.last() + 48f))
        }
    }
    val dragModifier = Modifier.draggable(
        dragState,
        Orientation.Vertical,
        onDragStarted = { dragging = true },
        onDragStopped = { velocity ->
            dragging = false
            val projected = height.value - velocity * 0.12f
            if (velocity > 2200f || projected < screenPx * 0.32f) {
                onDismiss()
            } else {
                val target = DETENTS.indices.minBy { abs(screenPx * DETENTS[it] - projected) }
                if (target != detent) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                detent = target
                scope.launch { height.animateTo(screenPx * DETENTS[target], spatialFloat) }
            }
        },
    )

    val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier.fillMaxSize().clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            )
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { height.value.toDp() })
                .offset { IntOffset(0, shift.value.roundToInt()) }
                .clip(shape)
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            Color.Transparent,
                        )
                    ),
                    shape,
                ),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp,
            shadowElevation = 16.dp,
        ) {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                Column(dragModifier) {
                    val grab by animateFloatAsState(if (dragging) 1f else 0f, fastSpatialFloat, label = "grab")
                    Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .width((44 + 20 * grab).dp)
                                .height(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f + 0.35f * grab),
                                    RoundedCornerShape(2.dp),
                                )
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.Top,
                    ) {
                        tabs.forEach { spec ->
                            MorphingTab(
                                selected = spec.tab == selectedTab,
                                onClick = {
                                    if (spec.tab != selectedTab) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onTabChange(spec.tab)
                                    }
                                },
                                icon = spec.icon,
                                label = spec.label,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                AnimatedContent(
                    targetState = selectedTab,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    transitionSpec = {
                        val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                        (slideInHorizontally(spatialOffset) { it / 8 * dir } + fadeIn(fastEffects)) togetherWith
                            (slideOutHorizontally(spatialOffset) { -it / 8 * dir } + fadeOut(fastEffects))
                    },
                    label = "tab_content",
                ) { target ->
                    if (selfScrolling(target)) {
                        Box(Modifier.fillMaxSize().padding(top = 12.dp)) { content(target) }
                    } else {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) { content(target) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MorphingTab(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    val colors = MaterialTheme.colorScheme
    val fastSpatialDp = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    val fastSpatialSize = MaterialTheme.motionScheme.fastSpatialSpec<IntSize>()
    val fastEffectsColor = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    val fastEffectsFloat = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val interactions = remember { MutableInteractionSource() }
    val corner by animateDpAsState(if (selected) 18.dp else 30.dp, fastSpatialDp, label = "tab_corner")
    val container by animateColorAsState(
        if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        fastEffectsColor,
        label = "tab_container",
    )
    val tint by animateColorAsState(
        if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        fastEffectsColor,
        label = "tab_tint",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .animateContentSize(fastSpatialSize)
            .semantics {
                role = Role.Tab
                this.selected = selected
            },
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(corner),
            color = container,
            interactionSource = interactions,
            modifier = Modifier.size(58.dp).squishable(interactions, scaleDown = 0.92f),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp), tint = tint)
            }
        }
        AnimatedVisibility(
            selected,
            enter = fadeIn(fastEffectsFloat) + expandVertically(fastSpatialSize),
            exit = fadeOut(fastEffectsFloat) + shrinkVertically(fastSpatialSize),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 6.dp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                color = colors.primary,
            )
        }
    }
}
