/*
 * SPDX-FileCopyrightText: 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles.settings

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Singularity's ImageUtils.saveImageToInternalStorage: picked images go to
 * /sdcard/Lunaris-OS/<feature>/<PREFIX>_<time>, where SystemUI reads them, replacing older ones.
 */
object LunarisImages {

    private const val TAG = "LunarisImages"

    fun save(context: Context, uri: Uri, feature: String, prefix: String): String? =
        runCatching {
            val type = context.contentResolver.getType(uri).orEmpty()
            val keepBytes = type == "image/gif" || type == "image/webp"
            val extension = when {
                type == "image/gif" -> ".gif"
                type == "image/webp" -> ".webp"
                else -> ".png"
            }
            val dir = File(Environment.getExternalStorageDirectory(), "Lunaris-OS/$feature")
            check(dir.isDirectory || dir.mkdirs())
            val file = File(dir, "${prefix}_${UUID.randomUUID()}$extension")
            try {
                requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                    FileOutputStream(file).use { out ->
                        if (keepBytes) {
                            check(input.copyTo(out) > 0)
                        } else {
                            val bitmap = requireNotNull(BitmapFactory.decodeStream(input))
                            try {
                                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                file.delete()
                throw e
            }
            // Preserve the old subject if reading or writing the replacement fails.
            dir.listFiles { _, name -> name.startsWith("${prefix}_") }
                ?.filter { it != file }?.forEach { it.delete() }
            file.absolutePath
        }.onFailure { Log.e(TAG, "Could not save $uri for $feature", it) }.getOrNull()
}
