/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lunaris.lockstyles

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.provider.Settings
import org.lunaris.clockmaker.ClockDesign
import org.lunaris.clockmaker.ClockImages
import java.io.File
import java.util.UUID

class ClockImageStore(private val context: Context) {
    private val storage = context.createDeviceProtectedStorageContext()
    private val resolver = context.contentResolver
    private val directory = File(storage.filesDir, "clock_images")

    data class ImportedImage(val id: String, val width: Int, val height: Int)

    fun file(id: String): File? = if (ClockImages.validId(id)) File(directory, id) else null

    fun importImage(uri: Uri, keepIds: Set<String> = emptySet()): ImportedImage {
        check(directory.isDirectory || directory.mkdirs())
        pruneUnused(keepIds)
        val id = "${UUID.randomUUID()}.img"
        val pending = File(directory, "$id.pending")
        val target = file(id)!!
        try {
            val used = directory.listFiles()?.sumOf { it.length() } ?: 0L
            resolver.openInputStream(uri)!!.use { input ->
                pending.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= ClockImages.MAX_FILE_BYTES && used + total <= ClockImages.MAX_STORAGE_BYTES)
                        output.write(buffer, 0, count)
                    }
                    require(total > 0)
                }
            }
            var width = 0
            var height = 0
            val decoded = ImageDecoder.decodeDrawable(ImageDecoder.createSource(pending)) { decoder, info, _ ->
                ClockImages.configureDecoder(decoder, info)
                width = info.size.width
                height = info.size.height
            }
            if (decoded is AnimatedImageDrawable) decoded.stop()
            check(pending.renameTo(target))
            return ImportedImage(id, width, height)
        } catch (e: Exception) {
            pending.delete()
            target.delete()
            throw e
        }
    }

    private fun pruneUnused(keepIds: Set<String>) {
        val references = storage.getSharedPreferences("clock_maker", Context.MODE_PRIVATE).all.values.filterIsInstance<String>() +
            context.getSharedPreferences("wallpaper_gallery", Context.MODE_PRIVATE).all.values.filterIsInstance<String>() +
            listOfNotNull(Settings.Secure.getString(resolver, ClockDesign.SETTING))
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        directory.listFiles()?.forEach { file ->
            if (file.name.endsWith(".pending") && ClockImages.validId(file.name.removeSuffix(".pending")) &&
                file.lastModified() < cutoff) file.delete()
            if (ClockImages.validId(file.name) && file.name !in keepIds && file.lastModified() < cutoff &&
                references.none { file.name in it }) file.delete()
        }
    }
}
