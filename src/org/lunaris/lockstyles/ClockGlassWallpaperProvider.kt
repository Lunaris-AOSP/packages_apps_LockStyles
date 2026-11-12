/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles

import android.app.WallpaperManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.util.Log
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import org.lunaris.clockmaker.ClockDesign
import org.lunaris.clockmaker.ClockGlass
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/** Supplies the gallery's rendered wallpaper to the shared SystemUI clock material. */
class ClockGlassWallpaperProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r" || uri.path != "/wallpaper") throw FileNotFoundException("Unsupported wallpaper request")
        val app = requireNotNull(context)
        val width = uri.getQueryParameter("width")?.toIntOrNull()?.coerceIn(1, 2048) ?: 1080
        val height = uri.getQueryParameter("height")?.toIntOrNull()?.coerceIn(1, 4096) ?: 2400
        val lock = uri.getQueryParameter("lock") != "0"
        val store = GalleryStore.get(app)
        val spec = store.liveWallpaper(lock) ?: store.liveWallpaper(!lock)
            ?: throw FileNotFoundException("No gallery wallpaper")
        val index = store.shuffleIndex
        val display = uri.getQueryParameter("display")?.toIntOrNull() ?: 0
        val rendered = synchronized(frames) { frames[FrameKey(spec, display, lock)] }?.takeIf {
            kotlin.math.abs(it.aspect - width.toFloat() / height) < 0.01f
        }?.bytes
        if (rendered != null) return openPipeHelper(uri, "image/png", null, rendered) { output, _, _, _, bytes ->
            FileOutputStream(output.fileDescriptor).use { it.write(requireNotNull(bytes)) }
        }
        return openPipeHelper(uri, "image/png", null, spec) { output, _, _, _, wallpaper ->
            val bitmap = GalleryRenderer.toBitmap(app, requireNotNull(wallpaper), width, height, shuffleIndex = index)
            try {
                FileOutputStream(output.fileDescriptor).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                bitmap.recycle()
            }
        }
    }

    override fun getType(uri: Uri) = "image/png"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()

    companion object {
        val URI: Uri = Uri.parse("content://org.lunaris.lockstyles.clockglass/wallpaper")
        private var lastFrame = 0L
        private data class FrameKey(val wallpaper: GalleryWallpaper, val display: Int, val lock: Boolean)
        private data class Frame(val aspect: Float, val bytes: ByteArray)
        private val frames = object : LinkedHashMap<FrameKey, Frame>(4, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FrameKey, Frame>?) = size > 4
        }
        private val encoding = mutableSetOf<FrameKey>()
        private val encoder = Executors.newSingleThreadExecutor()

        /** Keep moving/shuffled glass current, with background work capped at one update per second. */
        @Synchronized
        fun needsFrame(context: Context, force: Boolean = false): Boolean {
            val now = SystemClock.uptimeMillis()
            if (!force && now - lastFrame < 1000L) return false
            lastFrame = now
            val cr = context.contentResolver
            val style = Settings.Secure.getInt(cr, "lock_screen_custom_clock_style", 0)
            val enabled = if (style == ClockDesign.STYLE) {
                ClockDesign.parse(Settings.Secure.getString(cr, ClockDesign.SETTING))?.elements
                    ?.any { it.glass && it.visible } == true
            } else style in 1..92 && style !in setOf(1, 2, 25, 26) &&
                Settings.Secure.getInt(cr, ClockGlass.ENABLED_SETTING, 0) != 0
            return enabled
        }
        fun notifyFrame(context: Context) {
            if (needsFrame(context)) context.contentResolver.notifyChange(URI, null)
        }

        fun publishEffectFrame(context: Context, wallpaper: GalleryWallpaper, display: Int, flags: Int, frame: Bitmap) {
            val key = FrameKey(wallpaper, display, flags and WallpaperManager.FLAG_LOCK != 0)
            if (!synchronized(encoding) { encoding.add(key) }) {
                frame.recycle()
                return
            }
            encoder.execute {
                var small: Bitmap? = null
                try {
                    val width = frame.width
                    val height = frame.height
                    val scale = minOf(1f, 540f / width)
                    small = Bitmap.createScaledBitmap(frame, (width * scale).toInt().coerceAtLeast(1),
                        (height * scale).toInt().coerceAtLeast(1), true)
                    val stream = ByteArrayOutputStream()
                    small.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    synchronized(frames) {
                        val result = Frame(width.toFloat() / height, stream.toByteArray())
                        if (flags and WallpaperManager.FLAG_LOCK != 0) frames[FrameKey(wallpaper, display, true)] = result
                        if (flags and WallpaperManager.FLAG_SYSTEM != 0) frames[FrameKey(wallpaper, display, false)] = result
                    }
                    context.contentResolver.notifyChange(URI, null)
                } catch (error: Exception) {
                    Log.w("ClockGlass", "Cannot encode effect wallpaper", error)
                } finally {
                    if (small !== frame) small?.recycle()
                    frame.recycle()
                    synchronized(encoding) { encoding.remove(key) }
                }
            }
        }
    }
}
