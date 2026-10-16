/* SPDX-License-Identifier: Apache-2.0 */
package org.lunaris.lockstyles.ui

import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.unit.dp
import org.lunaris.lockstyles.settings.ui.AppWidgetGrid
import org.lunaris.lockstyles.settings.ui.LocalAppWidgets

@Composable
internal fun AppWidgetWallpaperLayer(clockBottom: Float?) {
    val state = LocalAppWidgets.current
    if (!state.enabled || state.entries.isEmpty() || clockBottom == null) return
    val context = LocalContext.current
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val scale = constraints.maxWidth / context.resources.displayMetrics.widthPixels.toFloat()
        val top = with(density) { clockBottom.toDp() } + 8.dp * scale
        val availableHeight = (maxHeight - top - 64.dp * scale).coerceAtLeast(0.dp)
        AppWidgetGrid(Modifier.align(Alignment.TopCenter).padding(top = top.coerceAtLeast(0.dp))
            .width(with(density) { (state.gridWidth * scale).toDp() })
            .heightIn(max = minOf(maxHeight * 0.45f, availableHeight)).clipToBounds())
    }
}
