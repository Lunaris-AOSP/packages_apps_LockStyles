/*
 * SPDX-FileCopyrightText: 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lunaris.lockstyles.GalleryRenderer
import org.lunaris.lockstyles.GalleryWallpaper
import org.lunaris.lockstyles.R

@Composable
fun WallpaperColorPickerDialog(wallpaper: GalleryWallpaper, onDismiss: () -> Unit,
        onColorSelected: (Int) -> Unit, allowPhotoPicking: Boolean = true) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val ratio = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.coerceAtLeast(1)
    var source by rememberSaveable { mutableStateOf<Uri?>(null) }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (allowPhotoPicking && it != null) source = it
    }
    LaunchedEffect(wallpaper, source, ratio) {
        loading = true
        selected = null
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val uri = source
                if (uri == null) {
                    val height = (800 / maxOf(ratio, 1f)).roundToInt().coerceAtLeast(1)
                    GalleryRenderer.toBitmap(context, wallpaper, (height * ratio).roundToInt()
                        .coerceAtLeast(1), height)
                } else {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) {
                            decoder, info, _ ->
                        val scale = minOf(800f / maxOf(info.size.width, info.size.height), 1f)
                        decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1),
                            (info.size.height * scale).roundToInt().coerceAtLeast(1))
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                }
            }.getOrNull()
        }
        loading = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_picker_wallpaper)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(enabled = allowPhotoPicking, onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.color_picker_import))
                }
                if (source != null) {
                    TextButton(onClick = { source = null }) {
                        Text(stringResource(R.string.color_picker_use_wallpaper))
                    }
                }
                val image = bitmap
                if (loading) {
                    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                        ExpressiveLoadingIndicator()
                    }
                } else if (image != null) {
                    ImageColorSampler(image) { selected = it }
                    Text(stringResource(R.string.color_picker_tap_image),
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(stringResource(R.string.color_picker_load_failed),
                        color = MaterialTheme.colorScheme.error)
                }
                selected?.let { color ->
                    Box(Modifier.fillMaxWidth().height(48.dp)
                        .background(Color(color), MaterialTheme.shapes.large))
                    Text("#%08X".format(color), style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        confirmButton = {
            ExpressiveButton(stringResource(R.string.apply_change), enabled = !loading && selected != null,
                onClick = { selected?.let(onColorSelected) })
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun ImageColorSampler(bitmap: Bitmap, onColorSelected: (Int) -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val select by rememberUpdatedState(onColorSelected)
    var marker by remember(bitmap) { mutableStateOf<Offset?>(null) }
    val description = stringResource(R.string.color_picker_tap_image)
    fun sample(position: Offset, size: IntSize) {
        val scale = minOf(size.width.toFloat() / bitmap.width, size.height.toFloat() / bitmap.height)
        if (scale <= 0f) return
        val left = (size.width - bitmap.width * scale) / 2f
        val top = (size.height - bitmap.height * scale) / 2f
        val x = (position.x - left) / scale
        val y = (position.y - top) / scale
        if (x < 0f || y < 0f || x >= bitmap.width || y >= bitmap.height) return
        marker = Offset(x / bitmap.width, y / bitmap.height)
        select(bitmap.getPixel(x.toInt(), y.toInt()))
    }
    Canvas(Modifier.fillMaxWidth().height(260.dp)
        .semantics { contentDescription = description }
        .pointerInput(bitmap) { detectTapGestures { sample(it, size) } }
        .pointerInput(bitmap) {
            detectDragGestures(onDragStart = { sample(it, size) }) { event, _ ->
                event.consume()
                sample(event.position, size)
            }
        }) {
        val scale = minOf(size.width / bitmap.width, size.height / bitmap.height)
        val width = (bitmap.width * scale).roundToInt()
        val height = (bitmap.height * scale).roundToInt()
        val left = ((size.width - width) / 2f).roundToInt()
        val top = ((size.height - height) / 2f).roundToInt()
        drawImage(image, dstOffset = IntOffset(left, top), dstSize = IntSize(width, height))
        marker?.let {
            val point = Offset(left + it.x * width, top + it.y * height)
            drawCircle(Color.Black, 9.dp.toPx(), point, style = Stroke(3.dp.toPx()))
            drawCircle(Color.White, 9.dp.toPx(), point, style = Stroke(1.5.dp.toPx()))
        }
    }
}
