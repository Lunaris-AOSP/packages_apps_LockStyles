package org.lunaris.lockstyles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Bundle
import android.provider.Settings
import android.util.AttributeSet
import android.util.Log
import android.util.LruCache
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextClock
import android.widget.TextView
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.lunaris.clockmaker.ClockDesign
import org.lunaris.clockmaker.ClockEffects
import org.lunaris.clockmaker.ClockFonts
import org.lunaris.clockmaker.ClockGlass
import org.lunaris.clockmaker.ClockGlassController
import org.lunaris.clockmaker.ClockGlassStyle
import org.lunaris.clockmaker.ClockPreviewGeometry
import org.lunaris.clockmaker.ClockViewTransform
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The custom lock screen clock styles from Singularity. SystemUI owns the layouts that are
 * actually shown, so previews are drawn from those,
 * placed, sized and coloured the way SystemUI's ClockStyle does it.
 */
object CustomClocks {

    private const val TAG = "CustomClocks"
    private const val SYSTEMUI = "com.android.systemui"
    private const val KEY_STYLE = "lock_screen_custom_clock_style"
    private const val KEY_COLOR_MODE = "lock_screen_custom_clock_color_mode"
    private const val KEY_CUSTOM_COLOR = "lock_screen_custom_clock_custom_color"
    private const val KEY_SIZE = "lock_screen_custom_clock_size_scale"
    private const val KEY_MARGIN_TOP = "lock_screen_custom_clock_margin_top"
    private const val KEY_OPACITY = "lock_screen_custom_clock_opacity"
    private const val KEY_SHADOW_ENABLED = "lock_screen_custom_clock_shadow_enabled"
    private const val KEY_SHADOW_COLOR = "lock_screen_custom_clock_shadow_color"
    private const val KEY_SHADOW_SOFTNESS = "lock_screen_custom_clock_shadow_softness"
    private const val KEY_SHADOW_OPACITY = "lock_screen_custom_clock_shadow_opacity"
    private const val CATEGORY_HIDE_CLOCK = "android.theme.customization.hideclock"
    private const val CATEGORY_SMARTSPACE = "android.theme.customization.smartspace"
    private const val OVERLAY_HIDE_CLOCK = "com.android.systemui.clocks.hideclock"
    private const val OVERLAY_HIDE_SMARTSPACE = "com.android.systemui.hide.smartspace"
    private const val KEY_MARGIN_START = "lock_screen_custom_clock_margin_start"
    private const val KEY_ROTATION = "lock_screen_custom_clock_rotation"
    private const val KEY_TILT_X = "lock_screen_custom_clock_tilt_x"
    private const val KEY_TILT_Y = "lock_screen_custom_clock_tilt_y"
    private const val KEY_HYPER_BLUR = "lock_screen_custom_clock_hyper_blur"
    private const val KEY_GRADIENT = "lock_screen_custom_clock_gradient_enabled"
    private const val KEY_GRADIENT_START = "lock_screen_custom_clock_gradient_color_start"
    private const val KEY_GRADIENT_END = "lock_screen_custom_clock_gradient_color_end"
    private const val KEY_GRADIENT_ANCHOR_Y = "lock_screen_custom_clock_gradient_anchor_y"
    private const val KEY_GRADIENT_RADIUS = "lock_screen_custom_clock_gradient_radius"
    private const val KEY_ALBUM_ART_COLOR = "lock_screen_custom_clock_album_art_color"
    private const val KEY_AOD_ANIM = "lock_screen_custom_clock_aod_anim"
    private const val KEY_WOBBLE = "lock_screen_custom_clock_wobble_on_charge"
    private const val KEY_WEATHER = "custom_clock_weather"
    private const val COLOR_MODE_ACCENT = "accent"

    /** Styles whose colours SystemUI never changes. */
    private val NO_COLOR = setOf(1, 2, 25, 26)
    private val NO_HORIZONTAL_MARGIN = setOf(45, 73, 74)
    private var measuredGeometry by mutableStateOf<ClockPreviewGeometry?>(null)
    private var previewWallpaperZoomEnabled by mutableStateOf<Boolean?>(null)

    private class Catalogue(
        val names: List<String>,
        /** Per style, a layout in [inflateContext]'s package. */
        val layouts: IntArray,
        val inflateContext: Context,
        val systemUi: Context?,
        val assetsSeq: Int,
    )

    /** Where SystemUI puts the clock, in pixels of the real screen. */
    class Frame(val top: Int, val side: Int, val width: Int, val scale: Float, val alpha: Float)

    /** How SystemUI sizes, places and colours a custom style; Settings' clock options. */
    data class Tuning(
        val scale: Int = 100,
        val opacity: Int = 100,
        val shadowEnabled: Boolean = false,
        val shadowColor: Int = Color.BLACK,
        val shadowSoftness: Int = 8,
        val shadowOpacity: Int = 35,
        val marginTop: Int = 15,
        val marginStart: Int = 0,
        val rotation: Int = 0,
        val tiltX: Int = 0,
        val tiltY: Int = 0,
        val hyperBlur: Boolean = false,
        val textGlass: Boolean = false,
        val glassIntensity: Int = ClockGlass.DEFAULT_INTENSITY,
        val glassLift: Int = ClockGlass.DEFAULT_LIFT,
        val glassStyle: ClockGlassStyle = ClockGlassStyle(),
        val separateDateGlass: Boolean = true,
        val dateGlassStyle: ClockGlassStyle = ClockGlassStyle.DATE_DEFAULT,
        val accent: Boolean = false,
        val gradient: Boolean = false,
        val gradientStart: Int = 0xFF00E5FF.toInt(),
        val gradientEnd: Int = 0xFFFF2DAA.toInt(),
        val gradientAnchorY: Int = 50,
        val gradientRadius: Int = 100,
        val albumArtColour: Boolean = false,
        val aodAnimation: Boolean = true,
        val wobbleOnCharge: Boolean = true,
        val weather: Boolean = true,
    ) {
        val shadowArgb: Int
            get() = if (shadowEnabled) {
                Color.argb((Color.alpha(shadowColor) * shadowOpacity.coerceIn(0, 100) / 100f)
                    .roundToInt(), Color.red(shadowColor), Color.green(shadowColor),
                    Color.blue(shadowColor))
            } else Color.TRANSPARENT

        companion object {
            fun load(context: Context): Tuning {
                val cr = context.contentResolver
                // Hidden keys are only readable by system apps; a sideloaded build gets defaults.
                fun int(key: String, default: Int) =
                    runCatching { Settings.Secure.getInt(cr, key, default) }.getOrDefault(default)
                val d = Tuning()
                fun string(key: String) = runCatching { Settings.Secure.getString(cr, key) }.getOrNull()
                val legacy = if (int(ClockGlass.ENABLED_SETTING, 0) != 0 &&
                    (string(ClockGlass.INTENSITY_SETTING) != null || string(ClockGlass.LIFT_SETTING) != null))
                    ClockGlassStyle.legacy(int(ClockGlass.INTENSITY_SETTING, d.glassIntensity),
                        int(ClockGlass.LIFT_SETTING, d.glassLift)) else ClockGlassStyle()
                return Tuning(
                    scale = int(KEY_SIZE, d.scale),
                    opacity = int(KEY_OPACITY, d.opacity),
                    shadowEnabled = int(KEY_SHADOW_ENABLED, 0) != 0,
                    shadowColor = int(KEY_SHADOW_COLOR, d.shadowColor),
                    shadowSoftness = int(KEY_SHADOW_SOFTNESS, d.shadowSoftness).coerceIn(0, 30),
                    shadowOpacity = int(KEY_SHADOW_OPACITY, d.shadowOpacity).coerceIn(0, 100),
                    marginTop = int(KEY_MARGIN_TOP, d.marginTop),
                    marginStart = int(KEY_MARGIN_START, d.marginStart),
                    rotation = int(KEY_ROTATION, d.rotation).coerceIn(-180, 180),
                    tiltX = int(KEY_TILT_X, d.tiltX).coerceIn(-75, 75),
                    tiltY = int(KEY_TILT_Y, d.tiltY).coerceIn(-75, 75),
                    hyperBlur = int(KEY_HYPER_BLUR, 0) != 0,
                    textGlass = int(ClockGlass.ENABLED_SETTING, 0) != 0,
                    glassIntensity = int(ClockGlass.INTENSITY_SETTING, d.glassIntensity).coerceIn(0, 100),
                    glassLift = int(ClockGlass.LIFT_SETTING, d.glassLift).coerceIn(0, 100),
                    glassStyle = ClockGlassStyle.parse(string(ClockGlassStyle.SETTING), legacy),
                    separateDateGlass = int(ClockGlassStyle.SEPARATE_DATE_SETTING, 1) != 0,
                    dateGlassStyle = ClockGlassStyle.parse(string(ClockGlassStyle.DATE_SETTING), ClockGlassStyle.DATE_DEFAULT),
                    accent = runCatching { Settings.Secure.getString(cr, KEY_COLOR_MODE) }
                        .getOrNull() == COLOR_MODE_ACCENT,
                    gradient = int(KEY_GRADIENT, 0) != 0,
                    gradientStart = int(KEY_GRADIENT_START, d.gradientStart),
                    gradientEnd = int(KEY_GRADIENT_END, d.gradientEnd),
                    gradientAnchorY = int(KEY_GRADIENT_ANCHOR_Y, d.gradientAnchorY),
                    gradientRadius = int(KEY_GRADIENT_RADIUS, d.gradientRadius),
                    albumArtColour = int(KEY_ALBUM_ART_COLOR, 0) != 0,
                    aodAnimation = int(KEY_AOD_ANIM, 1) != 0,
                    wobbleOnCharge = int(KEY_WOBBLE, 1) != 0,
                    weather = int(KEY_WEATHER, 1) != 0,
                )
            }

            fun fromJson(json: JSONObject): Tuning {
                val d = Tuning()
                return Tuning(
                    scale = json.optInt("scale", d.scale),
                    opacity = json.optInt("opacity", d.opacity),
                    shadowEnabled = json.optBoolean("shadowEnabled", d.shadowEnabled),
                    shadowColor = json.optInt("shadowColor", d.shadowColor),
                    shadowSoftness = json.optInt("shadowSoftness", d.shadowSoftness).coerceIn(0, 30),
                    shadowOpacity = json.optInt("shadowOpacity", d.shadowOpacity).coerceIn(0, 100),
                    marginTop = json.optInt("marginTop", d.marginTop),
                    marginStart = json.optInt("marginStart", d.marginStart),
                    rotation = json.optInt("rotation", d.rotation).coerceIn(-180, 180),
                    tiltX = json.optInt("tiltX", d.tiltX).coerceIn(-75, 75),
                    tiltY = json.optInt("tiltY", d.tiltY).coerceIn(-75, 75),
                    hyperBlur = json.optBoolean("hyperBlur", d.hyperBlur),
                    textGlass = json.optBoolean("textGlass", d.textGlass),
                    glassIntensity = json.optInt("glassIntensity", d.glassIntensity).coerceIn(0, 100),
                    glassLift = json.optInt("glassLift", d.glassLift).coerceIn(0, 100),
                    glassStyle = ClockGlassStyle.fromJson(json.optJSONObject("glassStyle"),
                        if (json.optBoolean("textGlass") && (json.has("glassIntensity") || json.has("glassLift"))) ClockGlassStyle.legacy(
                            json.optInt("glassIntensity", d.glassIntensity), json.optInt("glassLift", d.glassLift)) else ClockGlassStyle()),
                    separateDateGlass = json.optBoolean("separateDateGlass", d.separateDateGlass),
                    dateGlassStyle = ClockGlassStyle.fromJson(json.optJSONObject("dateGlassStyle"), ClockGlassStyle.DATE_DEFAULT),
                    accent = json.optBoolean("accent", d.accent),
                    gradient = json.optBoolean("gradient", d.gradient),
                    gradientStart = json.optInt("gradientStart", d.gradientStart),
                    gradientEnd = json.optInt("gradientEnd", d.gradientEnd),
                    gradientAnchorY = json.optInt("gradientAnchorY", d.gradientAnchorY),
                    gradientRadius = json.optInt("gradientRadius", d.gradientRadius),
                    albumArtColour = json.optBoolean("albumArtColour", d.albumArtColour),
                    aodAnimation = json.optBoolean("aodAnimation", d.aodAnimation),
                    wobbleOnCharge = json.optBoolean("wobbleOnCharge", d.wobbleOnCharge),
                    weather = json.optBoolean("weather", d.weather),
                )
            }
        }

        fun toJson(): JSONObject = JSONObject()
            .put("scale", scale)
            .put("opacity", opacity)
            .put("shadowEnabled", shadowEnabled)
            .put("shadowColor", shadowColor)
            .put("shadowSoftness", shadowSoftness)
            .put("shadowOpacity", shadowOpacity)
            .put("marginTop", marginTop)
            .put("marginStart", marginStart)
            .put("rotation", rotation)
            .put("tiltX", tiltX)
            .put("tiltY", tiltY)
            .put("hyperBlur", hyperBlur)
            .put("textGlass", textGlass)
            .put("glassIntensity", glassIntensity)
            .put("glassLift", glassLift)
            .put("glassStyle", glassStyle.toJson())
            .put("separateDateGlass", separateDateGlass)
            .put("dateGlassStyle", dateGlassStyle.toJson())
            .put("accent", accent)
            .put("gradient", gradient)
            .put("gradientStart", gradientStart)
            .put("gradientEnd", gradientEnd)
            .put("gradientAnchorY", gradientAnchorY)
            .put("gradientRadius", gradientRadius)
            .put("albumArtColour", albumArtColour)
            .put("aodAnimation", aodAnimation)
            .put("wobbleOnCharge", wobbleOnCharge)
            .put("weather", weather)

        fun save(context: Context) {
            val cr = context.contentResolver
            fun put(key: String, value: Int) = Settings.Secure.putInt(cr, key, value)
            fun put(key: String, value: Boolean) = put(key, if (value) 1 else 0)
            put(KEY_SIZE, scale)
            put(KEY_OPACITY, opacity)
            put(KEY_SHADOW_ENABLED, shadowEnabled)
            put(KEY_SHADOW_COLOR, shadowColor)
            put(KEY_SHADOW_SOFTNESS, shadowSoftness.coerceIn(0, 30))
            put(KEY_SHADOW_OPACITY, shadowOpacity.coerceIn(0, 100))
            put(KEY_MARGIN_TOP, marginTop)
            put(KEY_MARGIN_START, marginStart)
            put(KEY_ROTATION, rotation)
            put(KEY_TILT_X, tiltX)
            put(KEY_TILT_Y, tiltY)
            put(KEY_HYPER_BLUR, hyperBlur)
            put(ClockGlass.ENABLED_SETTING, textGlass)
            put(ClockGlass.INTENSITY_SETTING, glassIntensity.coerceIn(0, 100))
            put(ClockGlass.LIFT_SETTING, glassLift.coerceIn(0, 100))
            Settings.Secure.putString(cr, ClockGlassStyle.SETTING, glassStyle.toJson().toString())
            put(ClockGlassStyle.SEPARATE_DATE_SETTING, separateDateGlass)
            Settings.Secure.putString(cr, ClockGlassStyle.DATE_SETTING, dateGlassStyle.toJson().toString())
            put(KEY_GRADIENT, gradient)
            put(KEY_GRADIENT_START, gradientStart)
            put(KEY_GRADIENT_END, gradientEnd)
            put(KEY_GRADIENT_ANCHOR_Y, gradientAnchorY)
            put(KEY_GRADIENT_RADIUS, gradientRadius)
            put(KEY_ALBUM_ART_COLOR, albumArtColour)
            put(KEY_AOD_ANIM, aodAnimation)
            put(KEY_WOBBLE, wobbleOnCharge)
            put(KEY_WEATHER, weather)
        }
    }

    @Volatile private var catalogue: Catalogue? = null
    private val previews = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    private fun load(context: Context): Catalogue? {
        val assetsSeq = context.resources.configuration.assetsSeq
        catalogue?.takeIf { it.assetsSeq == assetsSeq }?.let { return it }
        return try {
            val systemUi = context.createPackageContext(SYSTEMUI,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
            Catalogue(
                ClockCatalogue.entries.map { it.second },
                ClockCatalogue.entries.map { (layout, _) ->
                    systemUi.resources.getIdentifier(layout, "layout", SYSTEMUI)
                }.toIntArray(),
                ContextThemeWrapper(systemUi,
                    android.R.style.Theme_DeviceDefault_NoActionBar),
                systemUi,
                assetsSeq,
            ).also { catalogue = it }
        } catch (e: Exception) {
            Log.w(TAG, "SystemUI has no custom clock styles", e)
            null
        }
    }

    /** Names in SystemUI's style-ID order, including the default clock at zero. */
    fun names(context: Context): List<String> = load(context)?.names ?: emptyList()

    fun isColourable(style: Int) = style !in NO_COLOR
    fun supportsHorizontalMargin(style: Int) = style !in NO_HORIZONTAL_MARGIN
    fun supportsHyperBlur(style: Int) = style in 93..102
    fun supportsTextGlass(style: Int) = style in 1..92 && isColourable(style)

    private fun readWallpaperZoomEnabled(context: Context): Boolean = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.WALLPAPER_ZOOM_ENABLED, 1) != 0 &&
            Settings.System.getInt(context.contentResolver,
                Settings.System.LOCKSCREEN_WALLPAPER_ZOOM_ENABLED, 1) != 0
    }.getOrDefault(true)

    fun refreshPreviewWallpaperZoom(context: Context) {
        previewWallpaperZoomEnabled = readWallpaperZoomEnabled(context)
    }

    @Synchronized
    fun refreshPreviewGeometry(context: Context) {
        refreshPreviewWallpaperZoom(context)
        val display = runCatching { context.display?.displayId ?: 0 }.getOrDefault(0)
        val result = runCatching {
            context.contentResolver.call(ClockPreviewGeometry.URI, ClockPreviewGeometry.METHOD,
                null, Bundle().apply { putInt("display_id", display) })
                ?.let(ClockPreviewGeometry::fromBundle)
        }.getOrNull()
        if (result != null) measuredGeometry = result
    }

    fun geometry(context: Context, homeScreen: Boolean = false): ClockPreviewGeometry {
        val metrics = context.resources.displayMetrics
        val bounds = context.getSystemService(WindowManager::class.java)!!.maximumWindowMetrics.bounds
        val width = bounds.width().coerceAtLeast(1)
        val height = bounds.height().coerceAtLeast(1)
        val display = runCatching { context.display?.displayId ?: 0 }.getOrDefault(0)
        val zoomEnabled = !homeScreen &&
            (previewWallpaperZoomEnabled ?: readWallpaperZoomEnabled(context))
        if (!homeScreen) measuredGeometry?.takeIf {
            it.displayId == display && it.userId == context.userId &&
                ((it.screenWidth == width && it.screenHeight == height) ||
                    (it.screenWidth == height && it.screenHeight == width)) && it.density == metrics.density
        }?.let { return if (zoomEnabled) it else it.copy(wallpaperScale = 1f) }
        val res = load(context)?.systemUi?.resources
        fun dimen(name: String, fallbackDp: Float): Int {
            val id = res?.getIdentifier(name, "dimen", SYSTEMUI) ?: 0
            return if (id != 0) res!!.getDimensionPixelSize(id) else (fallbackDp * metrics.density).toInt()
        }
        val statusBarTop = dimen("status_bar_height", 28f) * 1.25f
        val side = dimen("below_clock_padding_start", 32f)
        val wallpaperScale = if (zoomEnabled) runCatching {
            context.resources.getFloat(com.android.internal.R.dimen.config_wallpaperMaxScale)
        }.getOrDefault(1f) else 1f
        return ClockPreviewGeometry(display, context.userId, width, height, side,
            statusBarTop.toInt(), (width - side * 2).coerceAtLeast(1), statusBarTop, metrics.density,
            wallpaperScale = wallpaperScale)
    }

    fun appliedStyle(context: Context, fallback: ClockStyle): ClockStyle = runCatching {
        val cr = context.contentResolver
        val face = Settings.Secure.getInt(cr, KEY_STYLE, fallback.face)
            .coerceIn(0, ClockCatalogue.entries.lastIndex)
        if (face == 0) return@runCatching StockClock.load(context)
        val tuning = Tuning.load(context)
        val size = Settings.Secure.getString(cr, HomeClockLayout.SIZE_SETTING)?.toIntOrNull()
        val top = Settings.Secure.getString(cr, HomeClockLayout.TOP_SETTING)?.toIntOrNull()
        val start = Settings.Secure.getString(cr, HomeClockLayout.START_SETTING)?.toIntOrNull()
        fallback.copy(face = face, tuning = tuning,
            color = if (Settings.Secure.getString(cr, KEY_COLOR_MODE) == "custom")
                Settings.Secure.getInt(cr, KEY_CUSTOM_COLOR, Color.WHITE) else null,
            design = if (face == ClockDesign.STYLE)
                ClockDesign.parse(Settings.Secure.getString(cr, ClockDesign.SETTING)) ?: ClockDesign() else null,
            showOnHomeScreen = Settings.Secure.getInt(cr, "lock_screen_home_clock_enabled", 0) != 0,
            homeLayout = if (size != null || top != null || start != null) HomeClockLayout(
                (size ?: tuning.scale).coerceIn(25, 150), (top ?: tuning.marginTop).coerceIn(0, 2000),
                (start ?: tuning.marginStart).coerceIn(-2000, 2000)) else null,
        )
    }.getOrDefault(fallback)

    private fun shadowPadding(context: Context, tuning: Tuning?): Int {
        if (tuning == null || Color.alpha(tuning.shadowArgb) == 0) return 0
        return ceil((tuning.shadowSoftness.coerceIn(0, 30) * 2f + 2f) *
            context.resources.displayMetrics.density + 2f).toInt()
    }

    fun frame(context: Context, tuning: Tuning = Tuning.load(context), style: Int = 0,
        homeScreen: Boolean = false): Frame {
        val geometry = geometry(context, homeScreen)
        return Frame(
            top = geometry.top(tuning.marginTop.coerceIn(0, if (homeScreen) 2000 else 200)),
            side = geometry.left(if (homeScreen || supportsHorizontalMargin(style))
                tuning.marginStart.coerceIn(if (homeScreen) -2000 else -200, if (homeScreen) 2000 else 200) else 0),
            width = geometry.clockWidth,
            scale = tuning.scale.coerceIn(if (homeScreen) 25 else 50, 150) / 100f,
            alpha = tuning.opacity.coerceIn(0, 100) / 100f,
        )
    }

    /**
     * The style drawn at [width] pixels with the current time, or null for the default clock.
     * [trim] crops the empty space around it, for small tiles.
     */
    fun preview(context: Context, style: Int, width: Int, color: Int?,
            trim: Boolean = false, tuning: Tuning? = null, glassBackground: ClockGlass? = null,
            homeScreen: Boolean = false, font: String? = null, stockStyle: ClockStyle? = null): Bitmap? {
        if (style < 0) return null
        val minute = System.currentTimeMillis() / 60_000
        val assetsSeq = context.resources.configuration.assetsSeq
        val geometry = geometry(context, homeScreen)
        val stockKey = stockStyle?.let { "${it.small}/${it.preset}/${it.rounded}/${it.stockAxes}" }.orEmpty()
        val key = "$style/$width/$color/$minute/$trim/$tuning/$assetsSeq/$homeScreen/$geometry/$font/$stockKey/${System.identityHashCode(glassBackground)}"
        previews.get(key)?.let { return it }
        val cat = load(context) ?: return null
        if (style >= cat.layouts.size) return null
        return try {
            val inflater = LayoutInflater.from(cat.inflateContext).cloneInContext(
                cat.inflateContext).apply { factory2 = SystemUiViews(cat.systemUi?.classLoader) }
            val view = if (style == 0) inflateStockClock(context, cat,
                stockStyle ?: ClockStyle(), color) else inflater.inflate(cat.layouts[style], null)
            if (style != 0) prepare(view, if (isColourable(style)) color ?: Color.WHITE else null)
            if (font != null) {
                val typeface = ClockFonts.load(context, context.userId, font)
                fun applyFont(child: View) {
                    if (child is TextView) child.typeface = typeface
                    if (child is ViewGroup) for (i in 0 until child.childCount) applyFont(child.getChildAt(i))
                }
                applyFont(view)
            }
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            if (view.measuredHeight <= 0) return null
            if (style != 0 && isColourable(style)) {
                prepareColours(view, view, color ?: Color.WHITE, tuning)
            }
            val glass = if (tuning?.textGlass == true && supportsTextGlass(style) && glassBackground != null) {
                val frame = frame(context, tuning, style, homeScreen)
                val stage = FrameLayout(context)
                stage.addView(view)
                stage.layout(0, 0, geometry.screenWidth, geometry.screenHeight)
                view.layout(frame.side, frame.top, frame.side + view.measuredWidth,
                    frame.top + view.measuredHeight)
                view.pivotX = view.width / 2f
                view.pivotY = view.height / 2f
                view.scaleX = frame.scale
                view.scaleY = frame.scale
                ClockViewTransform.applyClockTransform(view, tuning.rotation, tuning.tiltX, tuning.tiltY)
                ClockGlassController(view).apply {
                    setPreview(glassBackground, stage)
                    fun collect(child: View) {
                        if (child is TextView) {
                            child.paint.shader = null
                            add(child, if (tuning.separateDateGlass && ClockGlassController.isDate(child))
                                tuning.dateGlassStyle else tuning.glassStyle)
                        }
                        if (child is ViewGroup) for (i in 0 until child.childCount) collect(child.getChildAt(i))
                    }
                    collect(view)
                    commit()
                }
            } else null
            val padding = shadowPadding(context, tuning)
            val preview = drawPreview(view, padding, glass, tuning)
            (if (trim) trimmed(preview) else preview)
                ?.also { previews.put(key, it) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not draw clock style $style", e)
            null
        }
    }

    private fun inflateStockClock(context: Context, cat: Catalogue, style: ClockStyle, color: Int?): View {
        val loader = requireNotNull(cat.systemUi).classLoader
        val settingsClass = loader.loadClass("com.android.systemui.plugins.keyguard.ui.clocks.ClockSettings")
        val settingsCompanion = settingsClass.getField("Companion").get(null)
        val axes = style.preset?.let { preset ->
            val (weight, width) = StockClock.PRESETS[preset.coerceIn(0, StockClock.PRESETS.lastIndex)]
            mapOf("wght" to weight, "wdth" to width, "slnt" to 0f)
        } ?: style.stockAxes ?: mapOf("wght" to 500f, "wdth" to 100f, "slnt" to 0f)
        val settingsJson = JSONObject().put("clockId", "DEFAULT")
            .put("seedColor", color ?: JSONObject.NULL)
            .put("axes", org.json.JSONArray().also { array ->
                (axes + ("ROND" to if (style.rounded) 100f else 0f)).forEach { (key, value) ->
                    array.put(JSONObject().put("key", key).put("value", value))
                }
            })
        val settings = settingsCompanion.javaClass.getMethod("fromJson", JSONObject::class.java)
            .invoke(settingsCompanion, settingsJson)
        val providerClass = loader.loadClass("com.android.systemui.shared.clocks.DefaultClockProvider")
        val reactive = loader.loadClass("com.android.systemui.shared.Flags")
            .getMethod("clockReactiveVariants").invoke(null) as Boolean
        val provider = providerClass.getConstructor(LayoutInflater::class.java,
            android.content.res.Resources::class.java, Boolean::class.javaPrimitiveType,
            android.os.Vibrator::class.java).newInstance(LayoutInflater.from(cat.inflateContext),
                cat.inflateContext.resources, reactive, context.getSystemService(android.os.Vibrator::class.java))
        val controller = providerClass.getMethod("createClock", Context::class.java, settingsClass)
            .invoke(provider, cat.inflateContext, settings)
        val controllerClass = loader.loadClass("com.android.systemui.plugins.keyguard.ui.clocks.ClockController")
        controllerClass.getMethod("initialize", Boolean::class.javaPrimitiveType,
            Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).invoke(controller, true, 0f, 0f)
        val eventClass = loader.loadClass("com.android.systemui.plugins.keyguard.ui.clocks.ClockEvents")
        val events = controllerClass.getMethod("getEvents").invoke(controller)
        val formatClass = loader.loadClass("com.android.systemui.plugins.keyguard.ui.clocks.TimeFormatKind")
        val format = formatClass.enumConstants.first { (it as Enum<*>).name ==
            if (android.text.format.DateFormat.is24HourFormat(context)) "FULL_DAY" else "HALF_DAY" }
        eventClass.getMethod("onTimeFormatChanged", formatClass).invoke(events, format)
        val face = controllerClass.getMethod(if (style.small) "getSmallClock" else "getLargeClock").invoke(controller)
        val faceClass = loader.loadClass("com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceController")
        val faceEvents = faceClass.getMethod("getEvents").invoke(face)
        val sizeId = cat.inflateContext.resources.getIdentifier(
            if (style.small) "small_clock_text_size" else "large_clock_text_size", "dimen", SYSTEMUI)
        require(sizeId != 0)
        val faceEventClass = loader.loadClass("com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceEvents")
        faceEventClass.getMethod("onFontSettingChanged", Float::class.javaPrimitiveType)
            .invoke(faceEvents, cat.inflateContext.resources.getDimension(sizeId))
        faceEventClass.getMethod("onTimeTick").invoke(faceEvents)
        return (faceClass.getMethod("getView").invoke(face) as View).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun drawPreview(view: View, padding: Int, glass: ClockGlassController?, tuning: Tuning?): Bitmap {
        val width = view.measuredWidth + padding * 2
        val height = view.measuredHeight + padding * 2
        val effect = if (tuning?.shadowEnabled == true) ClockEffects.shadow(tuning.shadowColor,
            tuning.shadowOpacity, tuning.shadowSoftness, view.resources.displayMetrics.density) else null
        if (glass != null || effect != null) {
            // The glyph shader can disappear on a software Canvas after hiding the text.
            // Render it on the GPU, then copy back for shadow extraction and tile cropping.
            val node = RenderNode("ClockGlassPreview")
            try {
                node.setPosition(0, 0, width, height)
                node.setRenderEffect(effect)
                val canvas = node.beginRecording(width, height)
                try {
                    canvas.translate(padding.toFloat(), padding.toFloat())
                    view.draw(canvas)
                } finally {
                    node.endRecording()
                }
                val hardware = checkNotNull(HardwareRenderer.createHardwareBitmap(node, width, height))
                try {
                    return checkNotNull(hardware.copy(Bitmap.Config.ARGB_8888, false))
                } finally {
                    hardware.recycle()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not render glass preview; drawing plain text", e)
            } finally {
                node.discardDisplayList()
                glass?.clear()
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            canvas.translate(padding.toFloat(), padding.toFloat())
            view.draw(canvas)
        }
        return addShadow(bitmap, view.context, tuning)
    }

    /**
     * SystemUI's clock views only need a context, so they're loaded from its code. Its other
     * views (weather and the like) need SystemUI itself; plain views keep the layout's shape.
     */
    private class SystemUiViews(private val loader: ClassLoader?) : LayoutInflater.Factory2 {
        override fun onCreateView(parent: View?, name: String, context: Context,
                attrs: AttributeSet): View? {
            if (!name.contains('.') || name.startsWith("android.")) return null
            // Weather controllers depend on SystemUI's process-local Dependency singleton.
            // Use the plain placeholders below without constructing those controllers.
            if (loader != null && name.startsWith("$SYSTEMUI.") &&
                    !name.startsWith("$SYSTEMUI.weather.")) {
                try {
                    return loader.loadClass(name).asSubclass(View::class.java)
                        .getConstructor(Context::class.java, AttributeSet::class.java)
                        .newInstance(context, attrs)
                } catch (e: ReflectiveOperationException) {
                    Log.w(TAG, "Could not build $name", e)
                } catch (e: LinkageError) {
                    Log.w(TAG, "Could not build $name", e)
                }
            }
            return if (name.endsWith("TextView")) TextView(context, attrs) else View(context, attrs)
        }

        override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? =
            onCreateView(null, name, context, attrs)
    }

    private fun prepare(view: View, color: Int?) {
        if (view is TextView) {
            // Detached views never tick, and single-line centred text is only scrolled into
            // place once attached, so both are done by hand.
            if (view is TextClock) view.refreshTime()
            if (view.isSingleLine) {
                view.setSingleLine(false)
                view.maxLines = 1
            }
            // As SystemUI does: only text that was white takes the chosen colour.
            if (color != null && (view.currentTextColor and 0xFFFFFF) == 0xFFFFFF) {
                view.setTextColor(color)
            }
        }
        if (view is ViewGroup) {
            view.clipChildren = false
            view.clipToPadding = false
            for (i in 0 until view.childCount) prepare(view.getChildAt(i), color)
        }
    }

    private fun addShadow(bitmap: Bitmap, context: Context, tuning: Tuning?): Bitmap {
        if (tuning == null || Color.alpha(tuning.shadowArgb) == 0) return bitmap
        val density = context.resources.displayMetrics.density
        val radius = tuning.shadowSoftness.coerceIn(0, 30) * density
        val blur = Paint().apply {
            if (radius > 0f) maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        val offset = IntArray(2)
        val mask = bitmap.extractAlpha(blur, offset)
        val preview = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(preview)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tuning.shadowArgb }
        canvas.drawBitmap(mask, offset[0].toFloat(), offset[1] + 2f * density, paint)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        mask.recycle()
        bitmap.recycle()
        return preview
    }

    /** Match ClockStyle's vertical gradient and HyperClockView colour API after measuring. */
    private fun prepareColours(view: View, root: View, color: Int, tuning: Tuning?,
            x: Float = 0f, y: Float = 0f) {
        val shader = if (tuning?.gradient == true) {
            val center = root.height * tuning.gradientAnchorY / 100f
            val radius = root.height * tuning.gradientRadius / 200f
            LinearGradient(root.width / 2f - x, center - radius - y,
                root.width / 2f - x, center + radius - y,
                tuning.gradientStart, tuning.gradientEnd, Shader.TileMode.CLAMP)
        } else null
        if (view is TextView && view.currentTextColor == color) {
            view.paint.shader = shader
        }
        if (view.javaClass.name == "$SYSTEMUI.clocks.HyperClockView") {
            // Keep rendering even if a newer SystemUI changes one of the optional preview hooks.
            // The clock itself is still useful without gradient/blur customisation.
            val cls = view.javaClass
            runCatching { cls.getMethod("refreshTime").invoke(view) }
            runCatching {
                cls.getMethod("setDigitColor", Int::class.javaPrimitiveType)
                    .invoke(view, if (shader != null) Color.WHITE else color)
            }
            runCatching {
                cls.getMethod("setColonFollowsDigits", Boolean::class.javaPrimitiveType)
                    .invoke(view, shader != null || color != Color.WHITE)
            }
            runCatching { cls.getMethod("setGradientShader", Shader::class.java).invoke(view, shader) }
            runCatching {
                cls.getMethod("setBlurEnabled", Boolean::class.javaPrimitiveType)
                    .invoke(view, tuning?.hyperBlur == true && shader == null)
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                prepareColours(child, root, color, tuning, x + child.left, y + child.top)
            }
        }
    }

    /** Crops away the empty space the layouts leave for the rest of the lock screen. */
    private fun trimmed(bitmap: Bitmap): Bitmap? {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
        var left = w
        var top = h
        var right = -1
        var bottom = -1
        for (y in 0 until h) for (x in 0 until w) {
            if (pixels[y * w + x] ushr 24 > 16) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < left || bottom < top) return null
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1)
    }

    /**
     * Applies [style] as Settings would. Switching between the default clock and a custom one
     * needs SystemUI to restart, as parts of the keyguard only read it at startup.
     */
    fun apply(context: Context, style: Int, color: Int?, tuning: Tuning? = null, design: ClockDesign? = null) {
        val resolver = context.contentResolver
        if (style == ClockDesign.STYLE) {
            Settings.Secure.putString(resolver, ClockDesign.SETTING, (design ?: ClockDesign()).toJson().toString())
        }
        val before = runCatching { Settings.Secure.getInt(resolver, KEY_STYLE, 0) }.getOrDefault(0)
        Settings.Secure.putInt(resolver, KEY_STYLE, style)
        val stockClockVisibilityChanged = (before == 0) != (style == 0)
        if (stockClockVisibilityChanged) setAospClockHidden(context, style != 0)
        if (style != 0) {
            // Without new tuning, e.g. from the switcher, the current accent or gradient stays.
            val current = tuning ?: Tuning.load(context)
            // As Settings does: a gradient is drawn in the custom colour mode.
            val mode = when {
                current.gradient -> "custom"
                current.accent -> COLOR_MODE_ACCENT
                color != null -> "custom"
                else -> "default"
            }
            Settings.Secure.putString(resolver, KEY_COLOR_MODE, mode)
            if (color != null) Settings.Secure.putInt(resolver, KEY_CUSTOM_COLOR, color)
            tuning?.save(context)
        }
        if (stockClockVisibilityChanged) {
            // Lunaris applies the stock clock's hideclock overlays through ThemeOverlayController;
            // the rest of the keyguard only reads the style at startup.
            org.lunaris.lockstyles.settings.Lunaris.restartSystemUI(context)
        }
    }

    /**
     * As Singularity's clock picker does: a custom style hides SystemUI's own clock and
     * smartspace through their theme overlays.
     */
    private fun setAospClockHidden(context: Context, hidden: Boolean) {
        org.lunaris.lockstyles.settings.Lunaris.setOverlay(context, CATEGORY_HIDE_CLOCK,
            if (hidden) OVERLAY_HIDE_CLOCK else "android", "android")
        org.lunaris.lockstyles.settings.Lunaris.setOverlay(context, CATEGORY_SMARTSPACE,
            if (hidden) OVERLAY_HIDE_SMARTSPACE else SYSTEMUI, SYSTEMUI)
    }
}
