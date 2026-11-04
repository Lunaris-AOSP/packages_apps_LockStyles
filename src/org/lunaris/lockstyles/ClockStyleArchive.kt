/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles

import android.content.Context
import android.net.Uri
import org.lunaris.clockmaker.ClockArchive
import org.lunaris.clockmaker.ClockFonts
import java.io.File
import java.io.InputStream
import java.util.UUID

class ClockStyleArchive(private val context: Context, private val store: ClockMakerStore) {
    private val images = ClockImageStore(context)

    fun fileName(clock: ClockStyle): String = clock.design?.name.orEmpty()
        .replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(60)
        .ifEmpty { "Custom clock" } + ClockArchive.EXTENSION

    fun exportClock(clock: ClockStyle, checkActive: () -> Unit = {}): File {
        checkActive()
        val design = requireNotNull(clock.design)
        val directory = File(context.cacheDir, "clock_exports")
        check(directory.isDirectory || directory.mkdirs())
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        directory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        val target = File(directory, "${UUID.randomUUID()}-${fileName(clock)}")
        val fonts = store.fonts.associateBy { it.family }
        val assets = mutableListOf<ClockArchive.Asset>()
        design.elements.map { it.font }.distinct().filter { it !in ClockFonts.families }.forEach { family ->
            checkActive()
            val header = openFont(family).use { it.readNBytes(4) }
            val extension = when {
                header.contentEquals("OTTO".toByteArray(Charsets.US_ASCII)) -> "otf"
                header.contentEquals(byteArrayOf(0, 1, 0, 0)) -> "ttf"
                else -> throw IllegalArgumentException("Invalid font")
            }
            assets += ClockArchive.Asset(ClockArchive.Kind.FONT, family,
                (fonts[family]?.name ?: family.removePrefix("bundled:")).take(60),
                "assets/${assets.size}.$extension")
        }
        design.elements.map { it.image }.distinct().filter { it.isNotEmpty() }.forEach { id ->
            assets += ClockArchive.Asset(ClockArchive.Kind.IMAGE, id, "Image",
                "assets/${assets.size}.${id.substringAfterLast('.')}")
        }
        try {
            target.outputStream().use { output ->
                ClockArchive.write(output, clock.copy(tuning = clock.tuning ?: CustomClocks.Tuning.load(context)).toJson(), assets, checkActive) { asset ->
                    when (asset.kind) {
                        ClockArchive.Kind.FONT -> openFont(asset.reference)
                        ClockArchive.Kind.IMAGE -> requireNotNull(images.file(asset.reference)).inputStream()
                    }
                }
            }
            return target
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    fun exportTo(uri: Uri, clock: ClockStyle, checkActive: () -> Unit = {}) {
        val file = exportClock(clock, checkActive)
        try {
            requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { output ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        checkActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

    fun importClock(uri: Uri, checkActive: () -> Unit = {}): ClockStyle {
        checkActive()
        val directory = File(context.cacheDir, "clock_import_${UUID.randomUUID()}")
        check(directory.mkdirs())
        try {
            val contents = requireNotNull(context.contentResolver.openInputStream(uri)).use {
                ClockArchive.read(it, directory, checkActive)
            }
            checkActive()
            return store.installArchive(contents, directory, checkActive)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun openFont(family: String): InputStream = if (family.startsWith("bundled:")) {
        ClockFonts.openBundled(context, family)
    } else {
        require(family.startsWith("import:"))
        requireNotNull(store.file(family.removePrefix("import:"))).inputStream()
    }
}
