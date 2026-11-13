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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

enum class CustomizerTab { WALLPAPER, CLOCK, SHORTCUTS, DEPTH, WIDGETS, AVATAR }

data class CustomizerTabSpec(val tab: CustomizerTab, val icon: ImageVector, val label: String)

private val DETENTS = floatArrayOf(0.42f, 0.62f, 0.88f)
private const val START_DETENT = 1

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
    val dark = isDarkSurface()
    val screenPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    var detent by rememberSaveable { mutableIntStateOf(START_DETENT) }
    var dragging by remember { mutableStateOf(false) }
    val height = remember { Animatable(screenPx * DETENTS[START_DETENT]) }
    val shift = remember { Animatable(if (visible) 0f else screenPx) }
    val spring = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

    LaunchedEffect(visible, screenPx) {
        if (visible) {
            height.snapTo(screenPx * DETENTS[detent])
            shift.animateTo(0f, spring)
        } else shift.animateTo(height.value + with(density) { 50.dp.toPx() }, spring)
    }
    if (!visible && shift.value >= height.value) return

    val dragState = rememberDraggableState { delta ->
        scope.launch {
            height.snapTo((height.value - delta).coerceIn(screenPx * 0.3f, screenPx * DETENTS.last() + 48f))
        }
    }
    val dragModifier = Modifier.draggable(dragState, Orientation.Vertical,
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
                scope.launch { height.animateTo(screenPx * DETENTS[target], spring) }
            }
        })

    val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.fillMaxSize().clickable(indication = null,
            interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss))
        Surface(
            modifier = Modifier.fillMaxWidth()
                .height(with(density) { height.value.toDp() })
                .offset { IntOffset(0, shift.value.roundToInt()) }
                .clip(shape)
                .border(1.dp, Brush.verticalGradient(listOf(
                    if (dark) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.outlineVariant,
                    Color.Transparent)), shape),
            color = if (dark) Color.Black else MaterialTheme.colorScheme.surface,
            tonalElevation = if (dark) 0.dp else 3.dp,
            shadowElevation = 16.dp,
        ) {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                Column(dragModifier) {
                    val grab by animateFloatAsState(if (dragging) 1f else 0f, tween(150), label = "grab")
                    Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.width((48 + 16 * grab).dp).height(4.dp).background(
                            (if (dark) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                                .copy(alpha = 0.3f + 0.3f * grab), RoundedCornerShape(2.dp)))
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.Top) {
                        tabs.forEach { spec ->
                            CircularTab(spec.tab == selectedTab, {
                                if (spec.tab != selectedTab) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onTabChange(spec.tab)
                                }
                            }, spec.icon, spec.label, dark)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
                HorizontalDivider(color = if (dark) Color.White.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.outlineVariant)
                AnimatedContent(
                    targetState = selectedTab,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    transitionSpec = {
                        val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                        (slideInHorizontally(tween(260)) { it / 8 * dir } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(200)) { -it / 8 * dir } + fadeOut(tween(120)))
                    },
                    label = "tab_content",
                ) { target ->
                    if (selfScrolling(target)) {
                        Box(Modifier.fillMaxSize().padding(top = 12.dp)) { content(target) }
                    } else {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) { content(target) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CircularTab(selected: Boolean, onClick: () -> Unit, icon: ImageVector, label: String,
        dark: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.animateContentSize().semantics { role = Role.Tab; this.selected = selected }) {
        Surface(onClick = onClick, shape = CircleShape, modifier = Modifier.size(60.dp),
            color = when {
                selected && dark -> Color.White.copy(alpha = 0.15f)
                selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                dark -> Color.White.copy(alpha = 0.05f)
                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            }) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp), tint = when {
                    selected && dark -> Color.White
                    selected -> MaterialTheme.colorScheme.primary
                    dark -> Color.White.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                })
            }
        }
        AnimatedVisibility(selected, enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 6.dp), fontWeight = FontWeight.Medium, maxLines = 1,
                color = if (dark) Color.White else MaterialTheme.colorScheme.onSurface)
        }
    }
}
