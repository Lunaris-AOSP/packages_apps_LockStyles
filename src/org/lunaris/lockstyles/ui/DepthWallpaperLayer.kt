/*
 * SPDX-FileCopyrightText: 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.lockstyles.ui

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.lunaris.clockmaker.ClockPreviewGeometry
import org.lunaris.clockmaker.spatial.SpatialWallpaperDrawable
import org.lunaris.clockmaker.spatial.SpatialWallpaperMotion
import org.lunaris.clockmaker.spatial.SpatialWallpaperScene
import org.lunaris.lockstyles.ClockStyle
import org.lunaris.lockstyles.CustomClocks
import org.lunaris.lockstyles.DynamicClocks
import org.lunaris.lockstyles.GalleryStore
import org.lunaris.lockstyles.GalleryWallpaper
import org.lunaris.lockstyles.settings.Keys
import org.lunaris.lockstyles.settings.state
import org.lunaris.lockstyles.settings.ui.rememberWidgetLifecycle
import kotlin.math.roundToInt

internal class DepthPreviewMotion(context: Context) {
    var horizontal by mutableFloatStateOf(0f)
    var vertical by mutableFloatStateOf(0f)
    var scale by mutableFloatStateOf(1f)
    val motion = SpatialWallpaperMotion(context) { x, y, zoom ->
        horizontal = x
        vertical = y
        scale = zoom
    }
}

internal data class PreparedDepthScene(val scene: SpatialWallpaperScene, val offsetX: Int, val offsetY: Int)
internal class DepthPreviewSession(
    val geometry: ClockPreviewGeometry,
    val driver: DepthPreviewMotion,
    val prepared: PreparedDepthScene?,
    val foreground: SpatialWallpaperDrawable,
    val background: SpatialWallpaperDrawable?,
)
internal val LocalDepthPreview = staticCompositionLocalOf<DepthPreviewSession?> { null }

private object DepthPreviewCache {
    private data class Key(val uri: Uri, val revision: Long)
    private val guard = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val completed = LinkedHashMap<Key, SpatialWallpaperScene>(4, 0.75f, true)
    private val pending = mutableMapOf<Key, Deferred<SpatialWallpaperScene?>>()

    suspend fun load(context: Context, uri: Uri, revision: Long): SpatialWallpaperScene? {
        val key = Key(uri, revision)
        val work = synchronized(guard) {
            completed[key]?.let { return it }
            pending[key] ?: scope.async(start = CoroutineStart.LAZY) {
                var result: SpatialWallpaperScene? = null
                try {
                    result = runCatching {
                        context.contentResolver.openFileDescriptor(uri, "r")
                            ?.let { SpatialWallpaperScene.read(it) }
                    }.getOrNull()
                    result
                } finally {
                    synchronized(guard) {
                        pending.remove(key)
                        result?.let { completed[key] = it }
                        while (completed.size > 2) completed.remove(completed.keys.first())
                    }
                }
            }.also {
                pending[key] = it
                it.start()
            }
        }
        return work.await()
    }
}

private fun matchesDepthWallpaper(context: Context, wallpaper: GalleryWallpaper?): Boolean {
    if (wallpaper == null) return true
    if (wallpaper.kind != GalleryWallpaper.Kind.PHOTO) return false
    val store = GalleryStore.get(context)
    val current = store.lockScreens.firstOrNull { it.id == store.currentId }?.wallpaper ?: return false
    return current.kind == wallpaper.kind && current.photos == wallpaper.photos && current.variant == wallpaper.variant
}

@Composable
internal fun rememberDepthPreview(wallpaper: GalleryWallpaper?, enabled: Boolean): DepthPreviewSession? {
    val context = LocalContext.current
    val depth by Keys.DEPTH.state()
    val path by Keys.DEPTH_SUBJECT.state()
    if (!enabled || !depth || path.isNullOrEmpty() || !matchesDepthWallpaper(context, wallpaper)) return null
    val subjectPath = path ?: return null
    val spatial by Keys.DEPTH_SPATIAL.state()
    val sensitivity by Keys.DEPTH_SPATIAL_SENSITIVITY.state()
    val offsetX by Keys.DEPTH_OFFSET_X.state()
    val offsetY by Keys.DEPTH_OFFSET_Y.state()
    val geometry = CustomClocks.geometry(context)
    val wm = remember(context) { WallpaperManager.getInstance(context) }
    val wallpaperId = wm.getWallpaperId(WallpaperManager.FLAG_LOCK).takeIf { it > 0 }
        ?: wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
    val revision = File(subjectPath).lastModified()
    val raw by key(subjectPath, revision, geometry.screenWidth, geometry.screenHeight) {
        produceState<Bitmap?>(null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(subjectPath, options)
                    var sample = 1
                    while (options.outWidth / (sample * 2) >= geometry.screenWidth &&
                        options.outHeight / (sample * 2) >= geometry.screenHeight) sample *= 2
                    options.inSampleSize = sample
                    options.inJustDecodeBounds = false
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888
                    BitmapFactory.decodeFile(subjectPath, options)
                }.getOrNull()
            }
        }
    }
    val uri = SpatialWallpaperScene.request(subjectPath, wallpaperId, geometry.screenWidth,
        geometry.screenHeight, geometry.density, offsetX, offsetY)
    val prepared by key(subjectPath, revision, wallpaperId, spatial, geometry.screenWidth, geometry.screenHeight) {
        produceState<PreparedDepthScene?>(null, uri) {
            if (spatial) {
                delay(150)
                DepthPreviewCache.load(context.applicationContext, uri, revision)?.let {
                    value = PreparedDepthScene(it, offsetX, offsetY)
                }
            }
        }
    }
    val driver = remember(context, subjectPath) { DepthPreviewMotion(context) }
    val lifecycle = rememberWidgetLifecycle()
    val moving = spatial && prepared != null && raw != null
    SideEffect { driver.motion.setSensitivity(sensitivity) }
    DisposableEffect(driver) { onDispose { driver.motion.destroy() } }
    DisposableEffect(driver, lifecycle, moving) {
        fun refresh(animate: Boolean) {
            driver.motion.setActive(moving && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED), animate)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh(true)
            else if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP)
                driver.motion.setActive(false, false)
        }
        val screen = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) driver.motion.setActive(false, false)
                else refresh(true)
            }
        }
        lifecycle.addObserver(observer)
        context.registerReceiver(screen, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }, Context.RECEIVER_NOT_EXPORTED)
        refresh(true)
        onDispose {
            context.unregisterReceiver(screen)
            lifecycle.removeObserver(observer)
            driver.motion.setActive(false, false)
        }
    }
    return remember(raw, prepared, driver, geometry) {
        val bitmap = raw ?: return@remember null
        val scene = prepared?.scene
        val foreground = SpatialWallpaperDrawable(bitmap, scene?.subjectDepth,
            scene?.depthWidth ?: 0, scene?.depthHeight ?: 0)
        val background = scene?.let {
            SpatialWallpaperDrawable(it.background, it.backgroundDepth, it.depthWidth, it.depthHeight).apply {
                setSubjectUnderlay(bitmap, it.subjectDepth, it.depthWidth, it.depthHeight)
            }
        }
        DepthPreviewSession(geometry, driver, prepared, foreground, background)
    }
}

@Composable
internal fun DepthPreviewProvider(wallpaper: GalleryWallpaper?, enabled: Boolean,
    content: @Composable () -> Unit) {
    val session = rememberDepthPreview(wallpaper, enabled)
    CompositionLocalProvider(LocalDepthPreview provides session, content = content)
}

@Composable
internal fun depthPreviewScale(wallpaper: GalleryWallpaper? = null): Float =
    LocalDepthPreview.current?.driver?.scale ?: 1f

@Composable
internal fun DepthWallpaperLayer(wallpaper: GalleryWallpaper? = null) {
    val session = LocalDepthPreview.current ?: return
    val geometry = session.geometry
    val opacity by Keys.DEPTH_OPACITY.state()
    val offsetX by Keys.DEPTH_OFFSET_X.state()
    val offsetY by Keys.DEPTH_OFFSET_Y.state()
    val fade by Keys.DEPTH_BOTTOM_FADE.state()
    val inset by Keys.DEPTH_BOTTOM_INSET.state()
    val curve by Keys.DEPTH_FADE_CURVE.state()
    Canvas(Modifier.fillMaxSize().graphicsLayer {
        transformOrigin = TransformOrigin(geometry.wallpaperPivotX / geometry.screenWidth,
            geometry.wallpaperPivotY / geometry.screenHeight)
        scaleX = geometry.wallpaperScale * session.driver.scale
        scaleY = geometry.wallpaperScale * session.driver.scale
    }) {
        val drawable = session.foreground
        drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
        drawable.setImagePlacement(offsetX * geometry.density, offsetY * geometry.density, true)
        drawable.setDepthOffset((offsetX - (session.prepared?.offsetX ?: offsetX)) * geometry.density / size.width,
            (offsetY - (session.prepared?.offsetY ?: offsetY)) * geometry.density / size.height)
        drawable.setFadeHeightPx((fade.coerceAtLeast(0) * geometry.density).roundToInt())
        drawable.setBottomInsetPx((inset.coerceAtLeast(0) * geometry.density).roundToInt())
        drawable.setFadeCurveExponent(0.2f + curve.coerceIn(0, 100) / 100f * 0.8f)
        drawable.alpha = (opacity.coerceIn(0, 100) * 2.55f).roundToInt().coerceIn(0, 255)
        drawable.setTilt(session.driver.horizontal, session.driver.vertical)
        drawIntoCanvas { drawable.draw(it.nativeCanvas) }
    }
}

@Composable
internal fun DepthWallpaperBackground(modifier: Modifier) {
    val session = LocalDepthPreview.current ?: return
    val drawable = session.background ?: return
    val offsetX by Keys.DEPTH_OFFSET_X.state()
    val offsetY by Keys.DEPTH_OFFSET_Y.state()
    Canvas(modifier) {
        drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
        drawable.subjectUnderlay?.apply {
            setImagePlacement(offsetX * session.geometry.density, offsetY * session.geometry.density, true)
            setDepthOffset((offsetX - (session.prepared?.offsetX ?: offsetX)) * session.geometry.density / size.width,
                (offsetY - (session.prepared?.offsetY ?: offsetY)) * session.geometry.density / size.height)
        }
        drawable.setTilt(session.driver.horizontal, session.driver.vertical)
        drawIntoCanvas { drawable.draw(it.nativeCanvas) }
    }
}

@Composable
internal fun DepthWallpaperPreview() {
    val context = LocalContext.current
    val geometry = CustomClocks.geometry(context)
    val subjectPath by Keys.DEPTH_SUBJECT.state()
    val wm = remember(context) { WallpaperManager.getInstance(context) }
    val wallpaperId = wm.getWallpaperId(WallpaperManager.FLAG_LOCK).takeIf { it > 0 }
        ?: wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
    val bitmap by produceState<Bitmap?>(null, wallpaperId, subjectPath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                var loaded: Bitmap? = null
                for (flag in intArrayOf(WallpaperManager.FLAG_LOCK, WallpaperManager.FLAG_SYSTEM)) {
                    wm.getWallpaperFile(flag)?.use { descriptor ->
                        loaded = BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor)
                    }
                    if (loaded != null) break
                }
                loaded
            }.getOrNull()
        }
    }
    val store = remember(context) { GalleryStore.get(context) }
    val active = store.lockScreens.firstOrNull { it.id == store.currentId }
    val dynamic = DynamicClocks.load(context)
    val clock = if (dynamic.enabled) dynamic.large
        else CustomClocks.appliedStyle(context, active?.clock ?: ClockStyle())
    Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.aspectRatio(geometry.aspect, matchHeightConstraintsFirst = true)
            .clip(MaterialTheme.shapes.extraLarge).background(Color.Black)) {
            LockScreenPreviewLayout(Modifier.fillMaxSize(), showDepth = true) {
                val session = LocalDepthPreview.current
                val background = Modifier.fillMaxSize().graphicsLayer {
                    transformOrigin = TransformOrigin(geometry.wallpaperPivotX / geometry.screenWidth,
                        geometry.wallpaperPivotY / geometry.screenHeight)
                    scaleX = geometry.wallpaperScale * (session?.driver?.scale ?: 1f)
                    scaleY = geometry.wallpaperScale * (session?.driver?.scale ?: 1f)
                }
                if (session?.background != null) DepthWallpaperBackground(background)
                else bitmap?.let { Image(it.asImageBitmap(), null, background, contentScale = ContentScale.Crop) }
                if (geometry.wallpaperDimAmount > 0f)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = geometry.wallpaperDimAmount)))
                Box(Modifier.fillMaxSize().background(Color(geometry.wallpaperScrimColor)))
                CustomClockLayer(clock, wallpaper = active?.wallpaper)
                DepthWallpaperLayer()
            }
        }
    }
}
