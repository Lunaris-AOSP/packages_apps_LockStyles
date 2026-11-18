package org.lunaris.lockstyles.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.View
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lunaris.clockmaker.ClockDesign
import org.lunaris.clockmaker.ClockGlass
import org.lunaris.clockmaker.ClockPreviewGeometry
import org.lunaris.clockmaker.ClockViewTransform
import org.lunaris.lockstyles.ClockStyle
import org.lunaris.lockstyles.CustomClocks
import org.lunaris.lockstyles.GalleryEffect
import org.lunaris.lockstyles.GalleryStore
import org.lunaris.lockstyles.GalleryRenderer
import org.lunaris.lockstyles.GalleryWallpaper
import org.lunaris.lockstyles.GalleryWeather
import org.lunaris.lockstyles.StockClock
import org.lunaris.lockstyles.settings.ExtraInfoSettings
import org.lunaris.lockstyles.settings.ExtraInfoSettingsProvider

/** The flex clock's weight/width presets, heaviest and narrowest first, as SystemUI has them. */
val CLOCK_PRESETS = StockClock.PRESETS

fun clockVariation(style: ClockStyle): String {
    val (weight, width) = CLOCK_PRESETS[(style.preset ?: 3).coerceIn(0, CLOCK_PRESETS.size - 1)]
    return "'wght' $weight, 'wdth' $width, 'ROND' ${if (style.rounded) 100 else 0}"
}

private val clockTypeface: Typeface by lazy { Typeface.create("google-sans-flex-clock",
    Typeface.NORMAL) }

/** A still of a wallpaper, drawn off the main thread at the size it is shown. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WallpaperImage(
    wallpaper: GalleryWallpaper,
    modifier: Modifier = Modifier,
    weather: GalleryWeather.Snapshot? = null,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val context = LocalContext.current
        val w = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val h = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        // The last one stays up until the next is drawn, so switching crossfades rather than
        // flashing empty, e.g. while scrolling through variants.
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(wallpaper, w, h, weather) {
            bitmap = withContext(Dispatchers.Default) {
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                    GalleryRenderer.draw(context, android.graphics.Canvas(it), wallpaper, w, h,
                        weather = weather)
                }
            }
        }
        Crossfade(bitmap, animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "wallpaper") { shown ->
            shown?.let {
                Image(it.asImageBitmap(), null, Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop)
            } ?: Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
        }
    }
}

@Composable
internal fun LockScreenWallpaper(
    wallpaper: GalleryWallpaper,
    modifier: Modifier = Modifier,
    live: Boolean = true,
    weather: GalleryWeather.Snapshot? = null,
) {
    val geometry = CustomClocks.geometry(LocalContext.current)
    Box(modifier) {
        val background = Modifier.fillMaxSize().graphicsLayer {
            transformOrigin = TransformOrigin(geometry.wallpaperPivotX / geometry.screenWidth,
                geometry.wallpaperPivotY / geometry.screenHeight)
            scaleX = geometry.wallpaperScale
            scaleY = geometry.wallpaperScale
        }
        if (live) LiveWallpaper(wallpaper, background, weather)
        else WallpaperImage(wallpaper, background, weather)
        if (geometry.wallpaperDimAmount > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = geometry.wallpaperDimAmount)))
        }
        Box(Modifier.fillMaxSize().background(Color(geometry.wallpaperScrimColor)))
    }
}

internal fun lockScreenWallpaperBitmap(context: Context, wallpaper: GalleryWallpaper,
    width: Int, height: Int, geometry: ClockPreviewGeometry): Bitmap =
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        canvas.save()
        canvas.scale(geometry.wallpaperScale, geometry.wallpaperScale,
            geometry.wallpaperPivotX * width / geometry.screenWidth,
            geometry.wallpaperPivotY * height / geometry.screenHeight)
        GalleryRenderer.draw(context, canvas, wallpaper, width, height,
            shuffleIndex = GalleryStore.get(context).shuffleIndex, weather = GalleryWeather.query(context))
        canvas.restore()
        canvas.drawColor(android.graphics.Color.argb((geometry.wallpaperDimAmount * 255f).toInt(), 0, 0, 0))
        canvas.drawColor(geometry.wallpaperScrimColor)
    }

@Composable
private fun previewMinute(): Long {
    val minute by produceState(System.currentTimeMillis() / 60_000L) {
        while (true) {
            val now = System.currentTimeMillis()
            value = now / 60_000L
            kotlinx.coroutines.delay(60_000L - now % 60_000L)
        }
    }
    return minute
}

internal data class GlassKey(val wallpaper: GalleryWallpaper, val radii: List<Int>, val width: Int, val height: Int)

internal object ClockGlassCache {
    private val entries = object : LinkedHashMap<GlassKey, ClockGlass>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<GlassKey, ClockGlass>) = size > 4
    }

    @Synchronized fun get(key: GlassKey): ClockGlass? = entries[key]

    @Synchronized fun put(key: GlassKey, glass: ClockGlass) { entries[key] = glass }
}

private val MOVING_WALLPAPERS = setOf(GalleryWallpaper.Kind.BUBBLES, GalleryWallpaper.Kind.SHUFFLE,
    GalleryWallpaper.Kind.ASTRONOMY, GalleryWallpaper.Kind.WAVES, GalleryWallpaper.Kind.WEATHER)

private const val GLASS_SOURCE_WIDTH = 540

@Composable
internal fun rememberClockGlass(wallpaper: GalleryWallpaper?, enabled: Boolean,
    blurRadii: List<Int> = listOf(16), homeScreen: Boolean = false): ClockGlass? {
    val context = LocalContext.current
    val geometry = CustomClocks.geometry(context, homeScreen)
    val width = geometry.screenWidth
    val height = geometry.screenHeight
    val frames by ClockGlassPreviewFrames.frames.collectAsState()
    val effectFrame = frames[wallpaper]
    DisposableEffect(wallpaper, enabled) {
        if (enabled && wallpaper != null && wallpaper.effect != 0) ClockGlassPreviewFrames.register(wallpaper)
        onDispose {
            if (enabled && wallpaper != null && wallpaper.effect != 0) ClockGlassPreviewFrames.unregister(wallpaper)
        }
    }
    val moving = wallpaper != null && wallpaper.kind in MOVING_WALLPAPERS
    val radii = blurRadii.map { it.coerceIn(0, 120) }.distinct().sorted()
    val cacheKey = if (enabled && wallpaper != null && wallpaper.effect == 0 && !moving)
        GlassKey(wallpaper, radii, width, height) else null
    // A new wallpaper or material must not keep the previous request's glass.
    return key(wallpaper, enabled, width, height, radii) {
        val background by produceState<ClockGlass?>(cacheKey?.let { ClockGlassCache.get(it) },
            wallpaper, enabled, width, height, blurRadii, effectFrame) {
            if (!enabled || wallpaper == null) {
                value = null
                return@produceState
            }
            if (cacheKey != null) ClockGlassCache.get(cacheKey)?.let {
                value = it
                return@produceState
            }
            do {
                val result = withContext(Dispatchers.Default) {
                    runCatching {
                        val bitmap = if (wallpaper.effect != 0) effectFrame ?: return@runCatching null
                            else {
                                val w = minOf(width, GLASS_SOURCE_WIDTH)
                                GalleryRenderer.toBitmap(context, wallpaper, w,
                                    (height * w.toLong() / width).toInt().coerceAtLeast(1),
                                    shuffleIndex = GalleryStore.get(context).shuffleIndex)
                            }
                        ClockGlass.prepare(bitmap, radii.toIntArray(), width, height)
                    }.getOrNull()
                }
                if (result != null) {
                    value = result
                    if (cacheKey != null) ClockGlassCache.put(cacheKey, result)
                }
                if (moving) kotlinx.coroutines.delay(1000L)
            } while (moving)
        }
        background
    }
}

@Composable
internal fun holdPreview(wanted: Boolean, ready: Boolean, source: Any?): Boolean {
    var gaveUp by remember(wanted, ready, source) { mutableStateOf(false) }
    LaunchedEffect(wanted, ready, source) {
        if (wanted && !ready) {
            kotlinx.coroutines.delay(3000L)
            gaveUp = true
        }
    }
    return wanted && !ready && !gaveUp
}

/** A wallpaper drawn every frame, for kinds that move. */
@Composable
fun LiveWallpaper(
    wallpaper: GalleryWallpaper,
    modifier: Modifier = Modifier,
    weather: GalleryWeather.Snapshot? = null,
    shuffleIndex: Int = 0,
) {
    val effect = GalleryEffect.of(wallpaper.effect)
    val photo = wallpaper.photos.firstOrNull()
    if (effect != GalleryEffect.NONE && photo != null) {
        EffectPreview(photo, effect, modifier, wallpaper)
        return
    }
    if (!wallpaper.kind.live || wallpaper.kind == GalleryWallpaper.Kind.SHUFFLE ||
            wallpaper.kind == GalleryWallpaper.Kind.ASTRONOMY) {
        val shown = if (wallpaper.kind == GalleryWallpaper.Kind.SHUFFLE) {
            wallpaper.copy(kind = GalleryWallpaper.Kind.PHOTO, variant = 0,
                photos = listOfNotNull(wallpaper.photos.getOrNull(
                    Math.floorMod(shuffleIndex, wallpaper.photos.size.coerceAtLeast(1)))))
        } else wallpaper
        WallpaperImage(shown, modifier, weather)
        return
    }
    val context = LocalContext.current
    var time by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(wallpaper) {
        while (true) withFrameMillis { time = System.currentTimeMillis() }
    }
    androidx.compose.foundation.Canvas(modifier) {
        drawIntoCanvas {
            GalleryRenderer.draw(context, it.nativeCanvas, wallpaper, size.width.toInt(),
                size.height.toInt(), time, weather = weather)
        }
    }
}

/** A lightweight stand-in for SystemUI's clock, for tiles, the switcher and customising. */
@Composable
fun ClockText(
    style: ClockStyle,
    heightDp: Dp,
    modifier: Modifier = Modifier,
    fixedTime: Boolean = false,
    onLight: Boolean = false,
) {
    val context = LocalContext.current
    val text = clockString(context, style.small, fixedTime)
    val color = style.color ?: if (onLight) 0xFF1C1C1E.toInt() else android.graphics.Color.WHITE
    val lines = if (style.small) 1 else 2
    val family = remember(style.preset, style.rounded) { clockFontFamily(clockVariation(style)) }
    val fontScale = LocalDensity.current.fontScale
    val size = (heightDp.value / lines * 0.95f / fontScale).sp
    Text(
        text,
        color = Color(color),
        fontFamily = family,
        fontSize = size,
        lineHeight = size * 0.9f,
        textAlign = TextAlign.Center,
        maxLines = lines,
        softWrap = false,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        modifier = modifier,
    )
}

private val clockFamilies = HashMap<String, FontFamily>()

/** The system clock font with the preset's axes baked in, which Compose can't set itself. */
private fun clockFontFamily(variation: String): FontFamily =
    clockFamilies.getOrPut(variation) {
        val typeface = CLOCK_FONT_FILES.map(::File).firstOrNull(File::exists)?.let {
            Typeface.Builder(it).setFontVariationSettings(variation).build()
        } ?: clockTypeface
        FontFamily(typeface)
    }

private val CLOCK_FONT_FILES = listOf("/system/fonts/GoogleSansFlexClock-Regular.ttf",
    "/product/fonts/GoogleSansFlexClock-Regular.ttf")

/** A clock thumbnail: SystemUI's own clock, or a custom style cropped to fit. */
@Composable
fun LockClock(
    style: ClockStyle,
    heightDp: Dp,
    widthDp: Dp,
    modifier: Modifier = Modifier,
    fixedTime: Boolean = false,
    onLight: Boolean = false,
) {
    if (style.face == ClockDesign.STYLE) {
        MakerClockLayer(style, style.tuning ?: CustomClocks.Tuning(),
            modifier.width(widthDp).height(heightDp), thumbnail = true)
        return
    }
    if (style.face <= 0) {
        ClockText(style, heightDp, modifier, fixedTime, onLight)
        return
    }
    val context = LocalContext.current
    val color = when {
        style.tuning?.gradient == true -> style.tuning.gradientStart
        style.tuning?.accent == true -> context.getColor(android.R.color.system_accent1_100)
        else -> style.color
    }
    val assetsSeq = LocalConfiguration.current.assetsSeq
    val geometry = CustomClocks.geometry(context)
    val minute = previewMinute()
    val preview by produceState<Bitmap?>(null, style.face, color, style.tuning, assetsSeq, geometry, minute) {
        value = CustomClocks.preview(context, style.face, geometry.clockWidth,
            color, trim = true, tuning = style.tuning)
    }
    preview?.let {
        Image(it.asImageBitmap(), null, modifier.width(widthDp).height(heightDp),
            contentScale = ContentScale.Fit)
    }
}

data class ClockPreviewBounds(val layoutBottom: Float, val visualBottom: Float)

@Composable
fun CustomClockLayer(
    style: ClockStyle,
    modifier: Modifier = Modifier,
    tuning: CustomClocks.Tuning? = null,
    onBottomChanged: ((ClockPreviewBounds) -> Unit)? = null,
    wallpaper: GalleryWallpaper? = null,
    homeScreen: Boolean = false,
) {
    val context = LocalContext.current
    val fallbackTuning = remember { CustomClocks.Tuning.load(context) }
    val currentTuning = tuning ?: style.tuning ?: fallbackTuning
    val geometry = CustomClocks.geometry(context, homeScreen)
    if (style.face == ClockDesign.STYLE) {
        MakerClockLayer(style, currentTuning, modifier, wallpaper = wallpaper,
            onBottomChanged = onBottomChanged, homeScreen = homeScreen)
        return
    }
    val configuration = LocalConfiguration.current
    val frame = remember(currentTuning, style.face, homeScreen, configuration, geometry) {
        CustomClocks.frame(context, currentTuning, style.face, homeScreen)
    }
    val color = when {
        currentTuning.gradient -> currentTuning.gradientStart
        currentTuning.accent -> context.getColor(android.R.color.system_accent1_100)
        else -> style.color
    }
    val wantGlass = currentTuning.textGlass && CustomClocks.supportsTextGlass(style.face)
    val glass = rememberClockGlass(wallpaper, wantGlass,
        listOf(currentTuning.glassStyle.blurRadius, currentTuning.dateGlassStyle.blurRadius).distinct(),
        homeScreen = homeScreen)
    val holdForGlass = holdPreview(wantGlass && wallpaper != null, glass != null, wallpaper)
    val assetsSeq = configuration.assetsSeq
    val minute = previewMinute()
    val preview by produceState<Bitmap?>(null, style.face, color, frame.width, currentTuning,
        glass, holdForGlass, assetsSeq, homeScreen, geometry, minute) {
        if (holdForGlass) {
            value = null
            return@produceState
        }
        value = CustomClocks.preview(context, style.face, frame.width, color, tuning = currentTuning,
            glassBackground = glass, homeScreen = homeScreen)
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val bitmap = preview ?: return@BoxWithConstraints
        val shadowPadding = (bitmap.width - frame.width) / 2
        val metrics = context.resources.displayMetrics
        val k = constraints.maxWidth / geometry.screenWidth.toFloat()
        val density = LocalDensity.current
        val bottom = remember(frame, currentTuning, bitmap.height, shadowPadding, k) {
            val height = bitmap.height - shadowPadding * 2
            val proxy = View(context).apply {
                layout(0, 0, frame.width, height)
                scaleX = frame.scale
                scaleY = frame.scale
                ClockViewTransform.applyClockTransform(this, currentTuning.rotation,
                    currentTuning.tiltX, currentTuning.tiltY)
            }
            val visual = RectF(0f, 0f, frame.width.toFloat(), height.toFloat())
            proxy.matrix.mapRect(visual)
            val layout = (frame.top + ClockViewTransform.layoutHeight(frame.width,
                height, frame.scale).coerceAtLeast(0)) * k
            ClockPreviewBounds(layout, if (frame.alpha > 0f)
                maxOf(layout, (frame.top + visual.bottom) * k) else layout)
        }
        SideEffect { onBottomChanged?.invoke(bottom) }
        Image(
            bitmap.asImageBitmap(), null,
            Modifier
                .absoluteOffset { IntOffset(((frame.side - shadowPadding) * k).toInt(),
                    ((frame.top - shadowPadding) * k).toInt()) }
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(with(density) { (bitmap.width * k).toDp() },
                    with(density) { (bitmap.height * k).toDp() })
                .graphicsLayer {
                    transformOrigin = TransformOrigin.Center
                    scaleX = frame.scale
                    scaleY = frame.scale
                    alpha = frame.alpha
                    rotationX = currentTuning.tiltX.coerceIn(-75, 75).toFloat()
                    rotationY = currentTuning.tiltY.coerceIn(-75, 75).toFloat()
                    rotationZ = currentTuning.rotation.coerceIn(-180, 180).toFloat()
                    // View.setCameraDistance divides by densityDpi; Compose passes directly
                    // to RenderNode. Match SystemUI's conversion before shrinking the preview.
                    cameraDistance = ClockViewTransform.cameraDistance(frame.width,
                        bitmap.height - shadowPadding * 2, metrics.density) / metrics.densityDpi * k
                },
            contentScale = ContentScale.FillBounds,
        )
    }
}

fun clockString(context: Context, small: Boolean, fixedTime: Boolean): String {
    val now = Calendar.getInstance()
    val hour = if (fixedTime) 9 else if (DateFormat.is24HourFormat(context))
        now.get(Calendar.HOUR_OF_DAY) else (now.get(Calendar.HOUR) + 11) % 12 + 1
    val minute = if (fixedTime) 41 else now.get(Calendar.MINUTE)
    return if (small) "%d:%02d".format(hour, minute) else "%02d\n%02d".format(hour, minute)
}

/** A phone-shaped preview of a lock screen, as the gallery and switcher show them. */
@Composable
fun LockScreenCard(
    wallpaper: GalleryWallpaper,
    clock: ClockStyle,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    fixedTime: Boolean = true,
    corner: Dp = 24.dp,
    weather: GalleryWeather.Snapshot? = null,
    showExtraInfo: Boolean = true,
    extraInfo: ExtraInfoSettings = ExtraInfoSettings(),
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(corner.coerceAtLeast(0.dp))
    val onLight = rememberTopLight(wallpaper)
    var clockBottom by remember(clock.face, clock.small) { mutableStateOf<ClockPreviewBounds?>(null) }
    BoxWithConstraints(
        modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        LockScreenPreviewLayout(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                LockScreenWallpaper(wallpaper, Modifier.fillMaxSize(), live && wallpaper.isAnimated, weather)
                if (clock.face > 0) {
                    CustomClockLayer(clock, wallpaper = wallpaper, onBottomChanged = { clockBottom = it })
                } else {
                    val clockHeight = maxHeight * if (clock.small) 0.06f else 0.24f
                    val dateSize = (maxWidth.value * 0.055f).sp
                    Column(
                        Modifier.fillMaxWidth().onSizeChanged {
                            clockBottom = ClockPreviewBounds(it.height.toFloat(), it.height.toFloat())
                        }
                            .padding(top = maxHeight * 0.1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            dateString(),
                            color = if (onLight) Color(0xFF1C1C1E) else Color.White,
                            fontSize = dateSize,
                            fontWeight = FontWeight.Medium,
                        )
                        ClockText(clock, clockHeight, fixedTime = fixedTime, onLight = onLight)
                    }
                }
                ExtraInfoSettingsProvider(extraInfo) {
                    val infoBottom = if (showExtraInfo)
                        ExtraInfoWallpaperLayer(clockBottom?.layoutBottom, wallpaper)
                        else clockBottom?.layoutBottom
                    AppWidgetWallpaperLayer(infoBottom, clockBottom)
                    if (showExtraInfo) IndependentInfoWallpaperLayer(wallpaper)
                }
            }
        }
    }
}

/** The clock turns dark over light wallpapers, as the real one does. */
@Composable
fun rememberTopLight(wallpaper: GalleryWallpaper): Boolean {
    val context = LocalContext.current
    val light by produceState(false, wallpaper) {
        value = withContext(Dispatchers.Default) { GalleryRenderer.isTopLight(context, wallpaper) }
    }
    return light
}

fun dateString(): String =
    DateFormat.format(DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(),
        "EEEdMMM"), System.currentTimeMillis()).toString()

@Composable
fun SourceButton(label: String, preview: GalleryWallpaper?, icon: ImageVector?,
        onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.width(104.dp)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(72.dp).clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center) {
                if (preview != null) WallpaperImage(preview, Modifier.fillMaxSize())
                if (icon != null) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(10.dp).size(26.dp))
                    }
                }
            }
            Text(label, color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                minLines = 2)
        }
    }
}
