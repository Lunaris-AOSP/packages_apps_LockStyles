/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.lockstyles.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import org.lunaris.lockstyles.CustomClocks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun LockScreenPreviewLayout(
    modifier: Modifier = Modifier,
    homeScreen: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val geometry = CustomClocks.geometry(context, homeScreen)
    LaunchedEffect(context, configuration, homeScreen) {
        if (!homeScreen) withContext(Dispatchers.IO) { CustomClocks.refreshPreviewGeometry(context) }
    }
    Layout(modifier = modifier, content = { Box(Modifier.fillMaxSize(), content = content) }) {
        measurables, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth
            else constraints.constrainWidth(geometry.screenWidth)
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight
            else constraints.constrainHeight(geometry.screenHeight)
        val scene = measurables.single().measure(
            Constraints.fixed(geometry.screenWidth, geometry.screenHeight))
        val scale = maxOf(width / geometry.screenWidth.toFloat(), height / geometry.screenHeight.toFloat())
        layout(width, height) {
            scene.placeWithLayer(((width - geometry.screenWidth * scale) / 2f).roundToInt(),
                ((height - geometry.screenHeight * scale) / 2f).roundToInt()) {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = scale
                scaleY = scale
            }
        }
    }
}
