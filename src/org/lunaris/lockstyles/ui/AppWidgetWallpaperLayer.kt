/* SPDX-License-Identifier: Apache-2.0 */
package org.lunaris.lockstyles.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.lunaris.lockstyles.settings.ui.AppWidgetGrid
import org.lunaris.lockstyles.settings.ui.LocalAppWidgets
import org.lunaris.lockstyles.CustomClocks

@Composable
internal fun AppWidgetWallpaperLayer(contentBottom: Float?, clockBounds: ClockPreviewBounds? = null) {
    val state = LocalAppWidgets.current
    if (!state.enabled || state.entries.isEmpty() || contentBottom == null) return
    val context = LocalContext.current
    val geometry = CustomClocks.geometry(context)
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val scale = constraints.maxWidth / geometry.screenWidth.toFloat()
        val auxiliaryBottom = (clockBounds?.layoutBottom ?: contentBottom) +
            geometry.appWidgetContentOffset * scale
        val anchorBottom = maxOf(contentBottom, auxiliaryBottom) + geometry.appWidgetTrailingOffset * scale
        val topPx = maxOf(anchorBottom, clockBounds?.visualBottom ?: anchorBottom) +
            (8f * context.resources.displayMetrics.density).toInt() * scale
        val top = with(density) { topPx.toDp() }
        val availableHeight = with(density) {
            (geometry.appWidgetBottom * scale - topPx).coerceAtLeast(0f).toDp()
        }
        AppWidgetGrid(Modifier.align(Alignment.TopCenter).padding(top = top.coerceAtLeast(0.dp))
            .absoluteOffset { IntOffset(((geometry.appWidgetCenterX - geometry.screenWidth / 2f) *
                scale).roundToInt(), 0) }
            .width(with(density) { (state.gridWidth * scale).toDp() })
            .heightIn(max = minOf(with(density) {
                (geometry.appWidgetMaxHeight * scale).toDp()
            }, availableHeight)).clipToBounds())
    }
}
