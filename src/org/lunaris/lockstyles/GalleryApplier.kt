package org.lunaris.lockstyles

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import java.io.File
import org.lunaris.lockstyles.settings.DepthWallpaper
import kotlin.math.max
import kotlin.math.min

/** Puts a [LockScreen]'s wallpapers on the device. The clock is applied by the caller. */
object GalleryApplier {

    private const val TAG = "GalleryApplier"
    private const val DEPTH_ENABLED = "depth_wallpaper_enabled"
    private const val DEPTH_AUTO_SUBJECT = "depth_wallpaper_auto_subject"
    const val DEPTH_SUBJECT = "depth_wallpaper_subject_image_uri"
    private const val SUBJECT_TIMEOUT_MS = 15_000L

    fun screenSize(context: Context): Pair<Int, Int> {
        val bounds = context.getSystemService(WindowManager::class.java)!!
            .maximumWindowMetrics.bounds
        return min(bounds.width(), bounds.height()) to max(bounds.width(), bounds.height())
    }

    enum class Result { APPLIED, DEPTH_FAILED, FAILED }

    /** Blocking; call off the main thread. */
    fun apply(context: Context, lockScreen: LockScreen): Result {
        val wm = WallpaperManager.getInstance(context)
        val store = GalleryStore.get(context)
        val (w, h) = screenSize(context)
        val wallpaper = lockScreen.wallpaper
        val live = ComponentName(context, GalleryWallpaperService::class.java)
        val wantsDepth = wallpaper.kind == GalleryWallpaper.Kind.PHOTO && wallpaper.depth
        var depthReady = false
        try {
            // Stop the previous subject appearing over a different wallpaper while extracting.
            Settings.System.putInt(context.contentResolver, DEPTH_ENABLED, 0)
            Settings.System.putInt(context.contentResolver, DEPTH_AUTO_SUBJECT, 0)
            Settings.System.putString(context.contentResolver, DEPTH_SUBJECT, null)
            if (wallpaper.effect != 0) {
                // An effect runs from the lock screen into the home screen, so it covers both.
                store.setLiveWallpaper(lock = false, wallpaper = null)
                store.setLiveWallpaper(lock = true, wallpaper = wallpaper)
                if (wantsDepth) {
                    val photo = GalleryRenderer.toBitmap(context, wallpaper, w, h)
                    try {
                        // The extractor checks the home wallpaper's live/static state too.
                        // Effects replace both screens, so stage this photo on both of them.
                        wm.setBitmap(photo, null, true,
                            WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                        depthReady = extractSubject(context)
                    } finally {
                        photo.recycle()
                    }
                }
                wm.setWallpaperComponentWithFlags(
                    ComponentName(context, GalleryEffectService::class.java),
                    WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            } else if (lockScreen.home == HomeStyle.PAIR) {
                store.setLiveWallpaper(lock = false, wallpaper = null)
                if (wallpaper.kind.live) {
                    store.setLiveWallpaper(lock = true, wallpaper = wallpaper)
                    wm.setWallpaperComponentWithFlags(live,
                        WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                } else {
                    store.setLiveWallpaper(lock = true, wallpaper = null)
                    wm.setBitmap(GalleryRenderer.toBitmap(context, wallpaper, w, h), null, true,
                        WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                }
            } else {
                // Home first, so the old shared wallpaper doesn't linger on the lock screen.
                store.setLiveWallpaper(lock = false, wallpaper = null)
                wm.setBitmap(homeBitmap(context, lockScreen, w, h), null, true,
                    WallpaperManager.FLAG_SYSTEM)
                if (wallpaper.kind.live) {
                    store.setLiveWallpaper(lock = true, wallpaper = wallpaper)
                    wm.setWallpaperComponentWithFlags(live, WallpaperManager.FLAG_LOCK)
                } else {
                    store.setLiveWallpaper(lock = true, wallpaper = null)
                    wm.setBitmap(GalleryRenderer.toBitmap(context, wallpaper, w, h), null, true,
                        WallpaperManager.FLAG_LOCK)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not set the wallpaper", e)
            return Result.FAILED
        }
        if (wantsDepth && wallpaper.effect == 0) depthReady = extractSubject(context)
        Settings.System.putInt(context.contentResolver, DEPTH_ENABLED, if (depthReady) 1 else 0)
        // Effects are live wallpapers: auto-extraction could replace their still-photo mask.
        Settings.System.putInt(context.contentResolver, DEPTH_AUTO_SUBJECT,
            if (depthReady && wallpaper.effect == 0) 1 else 0)
        store.setCurrent(lockScreen.id)
        store.pruneImportedPhotos()
        return if (wantsDepth && !depthReady) Result.DEPTH_FAILED else Result.APPLIED
    }

    /** Wait for the service to publish a new, readable subject, not just accept the intent. */
    private fun extractSubject(context: Context): Boolean {
        val resolver = context.contentResolver
        Settings.System.putString(resolver, DEPTH_SUBJECT, null)
        if (!DepthWallpaper.extractNow(context)) return false
        val deadline = SystemClock.uptimeMillis() + SUBJECT_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val path = Settings.System.getString(resolver, DEPTH_SUBJECT)
            if (!path.isNullOrEmpty() && File(path).isFile && File(path).length() > 0) return true
            SystemClock.sleep(200)
        }
        Log.w(TAG, "Timed out waiting for a depth subject")
        return false
    }

    fun homeBitmap(context: Context, lockScreen: LockScreen, w: Int, h: Int): Bitmap =
        when (lockScreen.home) {
            HomeStyle.BLUR -> blur(GalleryRenderer.toBitmap(context, lockScreen.wallpaper, w, h),
                context.resources.displayMetrics.density * 40)
            HomeStyle.COLOUR, HomeStyle.GRADIENT -> {
                val color = GalleryRenderer.dominantColor(context, lockScreen.wallpaper)
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
                    val canvas = Canvas(bitmap)
                    if (lockScreen.home == HomeStyle.COLOUR) {
                        canvas.drawColor(color)
                    } else {
                        val paint = Paint()
                        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                            GalleryRenderer.blend(color, Color.WHITE, 0.3f),
                            GalleryRenderer.blend(color, Color.BLACK, 0.45f), Shader.TileMode.CLAMP)
                        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                    }
                }
            }
            HomeStyle.PAIR -> GalleryRenderer.toBitmap(context, lockScreen.wallpaper, w, h)
        }

    fun blur(source: Bitmap, radius: Float): Bitmap {
        val w = source.width
        val h = source.height
        val content = RenderNode("GalleryBlur").apply {
            setPosition(0, 0, w, h)
            setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
        }
        content.beginRecording().apply {
            drawBitmap(source, 0f, 0f, null)
            drawColor(0x33000000)
        }
        content.endRecording()
        val root = RenderNode("GalleryBlurRoot").apply { setPosition(0, 0, w, h) }
        root.beginRecording().drawRenderNode(content)
        root.endRecording()
        return render(root, w, h) ?: source
    }

    /**
     * Draws [root] on the GPU and reads it back. Android 16 has no
     * HardwareRenderer.createHardwareBitmap, so it renders into an ImageReader instead.
     */
    private fun render(root: RenderNode, w: Int, h: Int): Bitmap? {
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
        val renderer = HardwareRenderer()
        return try {
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(root)
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            reader.acquireNextImage()?.use { image ->
                image.hardwareBuffer?.use { buffer ->
                    Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB))
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                }
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not render the blur", e)
            null
        } finally {
            renderer.destroy()
            reader.close()
        }
    }
}
