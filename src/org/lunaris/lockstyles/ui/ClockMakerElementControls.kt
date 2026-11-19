/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import org.lunaris.clockmaker.ClockImageFit
import org.lunaris.clockmaker.ClockImageShapes
import org.lunaris.clockmaker.ClockTimePart
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.lunaris.clockmaker.ClockDesignPositioning
import org.lunaris.clockmaker.ClockElement
import org.lunaris.clockmaker.ClockElementKind
import org.lunaris.lockstyles.GalleryWallpaper
import org.lunaris.lockstyles.R

@Composable
internal fun ElementLayoutControls(
    element: ClockElement,
    positioning: ClockDesignPositioning?,
    fontName: String,
    onChooseFont: () -> Unit,
    onChange: (ClockElement) -> Unit,
) {
    if (element.kind == ClockElementKind.IMAGE) {
        ImageSizeControls(element, onChange)
    } else {
        TextButton(onClick = onChooseFont, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(stringResource(R.string.clock_maker_font) + ": " + fontName)
        }
        Text(stringResource(R.string.clock_maker_shared_font),
            modifier = Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TuningSlider(stringResource(R.string.gallery_clock_size), element.size, 8..200, "",
            ClockElement.create(element.kind).size) { onChange(element.copy(size = it)) }
        TuningSlider(stringResource(R.string.clock_maker_weight), element.weight, 100..900, "", 400) {
            onChange(element.copy(weight = it))
        }
        TuningSwitch(stringResource(R.string.clock_maker_italic), element.italic) {
            onChange(element.copy(italic = it))
        }
        TuningSlider(stringResource(R.string.clock_maker_spacing), element.letterSpacing, -10..50, "%", 0) {
            onChange(element.copy(letterSpacing = it))
        }
    }
    positioning?.let { placement ->
        val position = placement.position(element.x, element.y)
        TuningSlider(stringResource(R.string.clock_maker_horizontal),
            (position.first * 100).roundToInt(), 0..100, "%", 50) {
            val moved = placement.move(element.x, element.y, horizontal = it / 100f)
            onChange(element.copy(x = moved.first, y = moved.second))
        }
        TuningSlider(stringResource(R.string.clock_maker_vertical),
            (position.second * 100).roundToInt(), 0..100, "%", 10) {
            val moved = placement.move(element.x, element.y, vertical = it / 100f)
            onChange(element.copy(x = moved.first, y = moved.second))
        }
    }
    TuningSlider(stringResource(R.string.custom_clock_rotation_title), element.rotation, -180..180, "°", 0) {
        onChange(element.copy(rotation = it))
    }
}

@Composable
internal fun ElementImageShapeControls(
    element: ClockElement,
    wallpaper: GalleryWallpaper,
    allowImport: Boolean,
    onChange: (ClockElement) -> Unit,
) {
    ShapePicker(element.imageShape) { onChange(element.copy(imageShape = it)) }
    TuningSwitch(stringResource(R.string.clock_maker_border), element.imageBorderWidth > 0) {
        onChange(element.copy(imageBorderWidth = if (it) 2 else 0))
    }
    if (element.imageBorderWidth > 0) {
        TuningSlider(stringResource(R.string.clock_maker_border_width), element.imageBorderWidth,
            1..24, "", 2) { onChange(element.copy(imageBorderWidth = it)) }
        Text(stringResource(R.string.clock_maker_border_colour), Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.titleSmall)
        ClockColourPicker(ELEMENT_COLOURS.filterNotNull(), element.imageBorderColor, wallpaper, allowImport) {
            onChange(element.copy(imageBorderColor = it ?: element.imageBorderColor))
        }
    }
}

@Composable
internal fun ElementTextEffectsControls(
    element: ClockElement,
    wallpaper: GalleryWallpaper,
    allowImport: Boolean,
    aod: Boolean = false,
    onChange: (ClockElement) -> Unit,
) {
    if (!element.supportsAodOutline) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TuningSwitch(stringResource(if (aod) R.string.clock_maker_aod_outline
            else R.string.clock_maker_lockscreen_outline), element.lockscreenOutline) {
            onChange(element.copy(lockscreenOutline = it))
        }
        if (element.lockscreenOutline) {
            TuningSlider(stringResource(R.string.clock_maker_aod_outline_width),
                element.lockscreenOutlineWidth, 1..6, "", 2) {
                onChange(element.copy(lockscreenOutlineWidth = it))
            }
        }
        TuningSwitch(stringResource(R.string.clock_maker_text_stroke), element.textStroke) {
            onChange(element.copy(textStroke = it))
        }
        if (element.textStroke) {
            TuningSlider(stringResource(R.string.clock_maker_text_stroke_width),
                element.textStrokeWidth, 1..12, "", 2) {
                onChange(element.copy(textStrokeWidth = it))
            }
            Text(stringResource(R.string.clock_maker_text_stroke_colour), Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.titleSmall)
            ClockColourPicker(ELEMENT_COLOURS.filterNotNull(), element.textStrokeColor, wallpaper, allowImport) {
                onChange(element.copy(textStrokeColor = it ?: element.textStrokeColor))
            }
        }
    }
}

@Composable
private fun ImageSizeControls(element: ClockElement, onChange: (ClockElement) -> Unit) {
    TuningSwitch(stringResource(R.string.clock_maker_lock_aspect), element.imageAspectLocked) {
        onChange(element.copy(imageAspectLocked = it, imageAspectRatio = element.imageWidth.toFloat() / element.imageHeight))
    }
    TuningSlider(stringResource(R.string.clock_maker_image_width), element.imageWidth, 16..360, "", 120) {
        onChange(element.resizeImage(width = it))
    }
    TuningSlider(stringResource(R.string.clock_maker_image_height), element.imageHeight, 16..720, "", 120) {
        onChange(element.resizeImage(height = it))
    }
    TuningSlider(stringResource(R.string.gallery_clock_opacity), element.opacity, 0..100, "%", 100) {
        onChange(element.copy(opacity = it))
    }
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ClockImageFit.entries.forEach { fit ->
            FilterChip(element.imageFit == fit, { onChange(element.copy(imageFit = fit)) },
                label = { Text(stringResource(if (fit == ClockImageFit.CROP) R.string.clock_maker_crop else R.string.clock_maker_fit)) })
        }
    }
}

@Composable
private fun ShapePicker(shape: String, onChange: (String) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Adaptive(88.dp), modifier = Modifier.fillMaxWidth()
        .height(300.dp).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ClockImageShapes.ids, key = { it }) { id ->
            val isSelected = id == shape
            val tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            Surface(shape = MaterialTheme.shapes.large,
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.clip(MaterialTheme.shapes.large)
                    .selectable(isSelected, role = Role.RadioButton, onClick = { onChange(id) })) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(40.dp).drawWithCache {
                        val path = ClockImageShapes.path(id, size.width, size.height).asComposePath()
                        onDrawBehind { drawPath(path, tint) }
                    })
                    Text(shapeLabel(id), style = MaterialTheme.typography.labelMedium, color = tint,
                        textAlign = TextAlign.Center, minLines = 2)
                }
            }
        }
    }
}

@Composable
internal fun elementLabel(element: ClockElement): String =
    if (element.kind == ClockElementKind.TIME && element.timePart != ClockTimePart.FULL) {
        timePartLabel(element.timePart)
    } else elementLabel(element.kind)

@Composable
internal fun timePartLabel(part: ClockTimePart): String = stringResource(when (part) {
    ClockTimePart.FULL -> R.string.clock_maker_full_time
    ClockTimePart.HOUR_TENS -> R.string.clock_maker_hour_tens
    ClockTimePart.HOUR_ONES -> R.string.clock_maker_hour_ones
    ClockTimePart.MINUTE_TENS -> R.string.clock_maker_minute_tens
    ClockTimePart.MINUTE_ONES -> R.string.clock_maker_minute_ones
    ClockTimePart.SECOND_TENS -> R.string.clock_maker_second_tens
    ClockTimePart.SECOND_ONES -> R.string.clock_maker_second_ones
})

@Composable
internal fun elementLabel(kind: ClockElementKind): String = stringResource(when (kind) {
    ClockElementKind.TIME -> R.string.clock_maker_time
    ClockElementKind.DATE -> R.string.clock_maker_date
    ClockElementKind.WEATHER -> R.string.gallery_clock_weather
    ClockElementKind.TEXT -> R.string.clock_maker_text
    ClockElementKind.IMAGE -> R.string.clock_maker_image
    ClockElementKind.QUICKLOOK -> R.string.clock_maker_quicklook
})

@Composable
private fun shapeLabel(id: String): String = stringResource(when (id) {
    "Circle" -> R.string.clock_maker_shape_circle
    "Square" -> R.string.clock_maker_shape_square
    "Slanted" -> R.string.clock_maker_shape_slanted
    "Arch" -> R.string.clock_maker_shape_arch
    "Fan" -> R.string.clock_maker_shape_fan
    "Arrow" -> R.string.clock_maker_shape_arrow
    "SemiCircle" -> R.string.clock_maker_shape_semicircle
    "Oval" -> R.string.clock_maker_shape_oval
    "Pill" -> R.string.clock_maker_shape_pill
    "Triangle" -> R.string.clock_maker_shape_triangle
    "Diamond" -> R.string.clock_maker_shape_diamond
    "ClamShell" -> R.string.clock_maker_shape_clamshell
    "Pentagon" -> R.string.clock_maker_shape_pentagon
    "Gem" -> R.string.clock_maker_shape_gem
    "Sunny" -> R.string.clock_maker_shape_sunny
    "VerySunny" -> R.string.clock_maker_shape_verysunny
    "Cookie4Sided" -> R.string.clock_maker_shape_cookie4sided
    "Cookie6Sided" -> R.string.clock_maker_shape_cookie6sided
    "Cookie7Sided" -> R.string.clock_maker_shape_cookie7sided
    "Cookie9Sided" -> R.string.clock_maker_shape_cookie9sided
    "Cookie12Sided" -> R.string.clock_maker_shape_cookie12sided
    "Ghostish" -> R.string.clock_maker_shape_ghostish
    "Clover4Leaf" -> R.string.clock_maker_shape_clover4leaf
    "Clover8Leaf" -> R.string.clock_maker_shape_clover8leaf
    "Burst" -> R.string.clock_maker_shape_burst
    "SoftBurst" -> R.string.clock_maker_shape_softburst
    "Boom" -> R.string.clock_maker_shape_boom
    "SoftBoom" -> R.string.clock_maker_shape_softboom
    "Flower" -> R.string.clock_maker_shape_flower
    "Puffy" -> R.string.clock_maker_shape_puffy
    "PuffyDiamond" -> R.string.clock_maker_shape_puffydiamond
    "PixelCircle" -> R.string.clock_maker_shape_pixelcircle
    "PixelTriangle" -> R.string.clock_maker_shape_pixeltriangle
    "Bun" -> R.string.clock_maker_shape_bun
    "Heart" -> R.string.clock_maker_shape_heart
    else -> R.string.clock_maker_shape_none
})

internal val ELEMENT_COLOURS: List<Int?> = listOf(null, 0xFFFFFFFF.toInt(), 0xFF000000.toInt(),
    0xFFFFD60A.toInt(), 0xFFFF9F0A.toInt(), 0xFFFF6482.toInt(), 0xFFBF5AF2.toInt(),
    0xFF64D2FF.toInt(), 0xFF30D158.toInt())
