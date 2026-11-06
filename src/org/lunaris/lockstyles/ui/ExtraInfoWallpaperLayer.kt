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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lunaris.extrainfowidgets.ExtraInfoCatalog
import org.lunaris.extrainfowidgets.ExtraInfoLayout
import org.lunaris.lockstyles.GalleryRenderer
import org.lunaris.lockstyles.GalleryWallpaper
import org.lunaris.lockstyles.settings.Keys
import org.lunaris.lockstyles.settings.state
import org.lunaris.lockstyles.settings.ui.ExtraInfoPreview

@Composable
internal fun ExtraInfoWallpaperLayer(clockBottom: Float?, wallpaper: GalleryWallpaper): Float? {
    val enabled by Keys.EXTRA_INFO_WIDGETS.state()
    val raw by Keys.EXTRA_INFO_LAYOUT.state()
    if (!enabled || clockBottom == null) return clockBottom
    val context = LocalContext.current
    val catalog = remember(context) { ExtraInfoCatalog.load(context) }
    val layout = remember(raw, catalog) { ExtraInfoLayout.parse(raw, catalog) }
    if (layout.rows.all { it.isEmpty() }) return clockBottom
    val density = LocalDensity.current
    val blur by Keys.EXTRA_INFO_BLUR.state()
    var extent by remember { mutableStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val bitmap by
            produceState<Bitmap?>(null, wallpaper, width, height, blur) {
                value =
                    if (blur && width > 0 && height > 0)
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
        ExtraInfoPreview(
            Modifier.align(Alignment.TopCenter).padding(top = top).fillMaxWidth().onSizeChanged {
                extent = margin + it.height.toFloat()
            },
            scale,
            bitmap,
            clockBottom + margin,
        )
    }
    return clockBottom + extent
}
