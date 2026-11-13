/* SPDX-License-Identifier: Apache-2.0 */
package org.lunaris.lockstyles.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lunaris.extrainfowidgets.ExtraInfoCatalog
import org.lunaris.extrainfowidgets.ExtraInfoFreeformLayout
import org.lunaris.extrainfowidgets.ExtraInfoLayout
import org.lunaris.lockstyles.GalleryRenderer
import org.lunaris.lockstyles.GalleryWallpaper
import org.lunaris.lockstyles.settings.Keys
import org.lunaris.lockstyles.settings.state
import org.lunaris.lockstyles.settings.ui.ExtraInfoPreview
import org.lunaris.lockstyles.settings.ui.IndependentInfoPreview
import org.lunaris.lockstyles.settings.ui.extraInfoAppearance

@Composable
internal fun ExtraInfoWallpaperLayer(clockBottom: Float?, wallpaper: GalleryWallpaper): Float? {
    val enabled by Keys.EXTRA_INFO_WIDGETS.state()
    val raw by Keys.EXTRA_INFO_LAYOUT.state()
    val independent by Keys.EXTRA_INFO_INDEPENDENT.state()
    if (!enabled || independent || clockBottom == null) return clockBottom
    val context = LocalContext.current
    val catalog = remember(context) { ExtraInfoCatalog.load(context) }
    val layout = remember(raw, catalog) { ExtraInfoLayout.parse(raw, catalog) }
    if (layout.rows.all { it.isEmpty() }) return clockBottom
    val density = LocalDensity.current
    val appearance = extraInfoAppearance()
    val glass = rememberClockGlass(wallpaper, appearance.liquidGlass, listOf(appearance.glassStyle.blurRadius))
    var extent by remember { mutableStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val bitmap by
            produceState<Bitmap?>(null, wallpaper, width, height, appearance.axBlur) {
                value =
                    if (appearance.axBlur && width > 0 && height > 0)
                        withContext(Dispatchers.Default) {
                            runCatching {
                                    GalleryRenderer.toBitmap(context, wallpaper, width, height)
                                }
                                .getOrNull()
                        }
                    else null
            }
        val scale = constraints.maxWidth / context.resources.displayMetrics.widthPixels.toFloat()
        val margin = with(density) { (12.dp * scale).toPx() }
        val top = with(density) { (clockBottom + margin).toDp() }
        val hold = holdPreview(appearance.liquidGlass || appearance.axBlur,
            if (appearance.liquidGlass) glass != null else bitmap != null, wallpaper)
        ExtraInfoPreview(
            Modifier.align(Alignment.TopCenter).padding(top = top).fillMaxWidth()
                .alpha(if (hold) 0f else 1f).onSizeChanged {
                extent = margin + it.height.toFloat()
            },
            scale,
            bitmap,
            clockBottom + margin,
            glassMaterial = glass,
        )
    }
    return clockBottom + extent
}

@Composable
internal fun IndependentInfoWallpaperLayer(wallpaper: GalleryWallpaper, fullScreen: Boolean = false) {
    val enabled by Keys.EXTRA_INFO_WIDGETS.state()
    val independent by Keys.EXTRA_INFO_INDEPENDENT.state()
    if (!enabled || !independent) return
    val context = LocalContext.current
    val catalog = remember(context) { ExtraInfoCatalog.load(context) }
    val rows by Keys.EXTRA_INFO_LAYOUT.state()
    var raw by Keys.EXTRA_INFO_FREEFORM.state()
    val layout =
        remember(raw, rows, catalog) {
            ExtraInfoFreeformLayout.parse(raw, catalog, ExtraInfoLayout.parse(rows, catalog))
        }
    var picked by remember { mutableStateOf<String?>(null) }
    var pinchBase by remember { mutableStateOf<Float?>(null) }
    val latest by rememberUpdatedState(layout)
    val selected = picked?.takeIf { id -> layout.widgets.any { it.id == id } } ?: layout.widgets.lastOrNull()?.id
    IndependentInfoPreview(
        layout, wallpaper, Modifier.fillMaxSize(),
        selected = if (fullScreen) selected else null,
        onSelected = if (fullScreen) { { id: String -> picked = id } } else null,
        onEdit = if (fullScreen) { { edited: ExtraInfoFreeformLayout -> raw = edited.encode() } } else null,
        onPinchStart = if (fullScreen && selected != null) {
            { pinchBase = latest.widgets.firstOrNull { it.id == selected }?.scale }
        } else null,
        onPinch = if (fullScreen && selected != null) { { factor ->
            val base = pinchBase
            if (base != null) {
                raw = latest.resize(selected, (base * factor).coerceIn(
                    ExtraInfoFreeformLayout.MIN_SCALE, ExtraInfoFreeformLayout.MAX_SCALE)).encode()
            }
        } } else null,
        onPinchEnd = if (fullScreen && selected != null) { { pinchBase = null } } else null,
    )
}
