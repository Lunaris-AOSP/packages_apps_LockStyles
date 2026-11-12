package org.lunaris.lockstyles.ui

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import android.view.animation.PathInterpolator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.lunaris.lockstyles.GalleryEffect
import org.lunaris.lockstyles.GalleryWallpaper
import kotlinx.coroutines.flow.MutableStateFlow
import org.lunaris.lockstyles.GalleryEffectRenderer

/** Plays [effect] over [photo] from locked to unlocked and back, as a preview of unlocking. */
@Composable
fun EffectPreview(photo: String, effect: GalleryEffect, modifier: Modifier = Modifier,
    wallpaper: GalleryWallpaper? = null) {
    val context = LocalContext.current
    val player = remember { EffectPlayer(context) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    player.set(photo, effect, wallpaper)
    AndroidView(
        factory = { TextureView(it).apply { surfaceTextureListener = player } },
        modifier = modifier,
    )
}

private class EffectPlayer(context: android.content.Context) : TextureView.SurfaceTextureListener {
    private val thread = HandlerThread("GalleryEffectPreview").apply { start() }
    private val handler = Handler(thread.looper)
    private val renderer = GalleryEffectRenderer(context)
    private val ease = PathInterpolator(0.2f, 0f, 0f, 1f)
    private var surface: Surface? = null
    private var photo: String? = null
    private var effect = GalleryEffect.NONE
    private var start = 0L
    private var wallpaper: GalleryWallpaper? = null
    private var lastCapture = 0L

    private val frame = object : Runnable {
        override fun run() {
            val duration = effect.durationMs.coerceAtLeast(1)
            // Rest on the locked look, play the unlock, rest, then rewind.
            val cycle = HOLD_MS * 2 + duration * 2
            val t = (SystemClock.uptimeMillis() - start) % cycle
            val f = when {
                t < HOLD_MS -> 0f
                t < HOLD_MS + duration -> ease.getInterpolation((t - HOLD_MS) / duration.toFloat())
                t < HOLD_MS * 2 + duration -> 1f
                else -> 1f - ease.getInterpolation((t - HOLD_MS * 2 - duration) /
                    duration.toFloat())
            }
            val key = wallpaper
            val now = SystemClock.uptimeMillis()
            val capture = key != null && ClockGlassPreviewFrames.requested(key) && now - lastCapture >= 1000L
            if (capture) lastCapture = now
            renderer.draw(effect.locked + (effect.unlocked - effect.locked) * f, if (capture) { frame ->
                ClockGlassPreviewFrames.publish(requireNotNull(key), frame)
            } else null)
            handler.postDelayed(this, 16)
        }
    }

    fun set(photo: String, effect: GalleryEffect, wallpaper: GalleryWallpaper?) = handler.post {
        this.wallpaper = wallpaper
        if (photo == this.photo && effect == this.effect) return@post
        this.photo = photo
        this.effect = effect
        start = SystemClock.uptimeMillis()
        renderer.load(photo, effect)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        handler.post {
            renderer.release()
            surface?.release()
            thread.quitSafely()
        }
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, w: Int, h: Int) {
        handler.post {
            surface = Surface(texture)
            renderer.attach(surface!!, w, h)
            photo?.let { renderer.load(it, effect) }
            handler.removeCallbacks(frame)
            handler.post(frame)
        }
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, w: Int, h: Int) {
        handler.post { renderer.resize(w, h) }
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        handler.removeCallbacks(frame)
        handler.post {
            renderer.detachSurface()
            surface?.release()
            surface = null
            texture.release()
        }
        return false
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}

    companion object {
        private const val HOLD_MS = 900L
    }
}

internal object ClockGlassPreviewFrames {
    val frames = MutableStateFlow<Map<GalleryWallpaper, Bitmap>>(emptyMap())
    private val requests = mutableMapOf<GalleryWallpaper, Int>()

    @Synchronized fun register(wallpaper: GalleryWallpaper) {
        requests[wallpaper] = (requests[wallpaper] ?: 0) + 1
    }

    @Synchronized fun unregister(wallpaper: GalleryWallpaper) {
        val count = (requests[wallpaper] ?: 1) - 1
        if (count > 0) requests[wallpaper] = count else {
            requests.remove(wallpaper)
            frames.value = frames.value - wallpaper
        }
    }

    @Synchronized fun requested(wallpaper: GalleryWallpaper) = wallpaper in requests

    fun publish(wallpaper: GalleryWallpaper, frame: Bitmap) {
        val scale = minOf(1f, 540f / frame.width)
        val small = Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt().coerceAtLeast(1),
            (frame.height * scale).toInt().coerceAtLeast(1), true)
        if (small !== frame) frame.recycle()
        synchronized(this) {
            if (wallpaper in requests) frames.value = frames.value + (wallpaper to small)
            else small.recycle()
        }
    }
}
