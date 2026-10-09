package org.lunaris.lockstyles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.provider.Settings
import android.util.AttributeSet
import android.util.Log
import android.util.LruCache
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextClock
import android.widget.TextView
import org.json.JSONObject

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
    private const val PREVIEW_SHADOW = 0x59000000

    /** Styles whose colours SystemUI never changes. */
    private val NO_COLOR = setOf(1, 2, 25, 26)
    private val NO_HORIZONTAL_MARGIN = setOf(45, 73, 74)

    private class Catalogue(
        val names: List<String>,
        /** Per style, a layout in [inflateContext]'s package. */
        val layouts: IntArray,
        val inflateContext: Context,
        val systemUi: Context?,
    )

    /** Where SystemUI puts the clock, in pixels of the real screen. */
    class Frame(val top: Int, val side: Int, val width: Int, val scale: Float, val alpha: Float)

    /** How SystemUI sizes, places and colours a custom style; Settings' clock options. */
    data class Tuning(
        val scale: Int = 100,
        val opacity: Int = 100,
        val marginTop: Int = 15,
        val marginStart: Int = 0,
        val rotation: Int = 0,
        val tiltX: Int = 0,
        val tiltY: Int = 0,
        val hyperBlur: Boolean = false,
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
        companion object {
            fun load(context: Context): Tuning {
                val cr = context.contentResolver
                // Hidden keys are only readable by system apps; a sideloaded build gets defaults.
                fun int(key: String, default: Int) =
                    runCatching { Settings.Secure.getInt(cr, key, default) }.getOrDefault(default)
                val d = Tuning()
                return Tuning(
                    scale = int(KEY_SIZE, d.scale),
                    opacity = int(KEY_OPACITY, d.opacity),
                    marginTop = int(KEY_MARGIN_TOP, d.marginTop),
                    marginStart = int(KEY_MARGIN_START, d.marginStart),
                    rotation = int(KEY_ROTATION, d.rotation).coerceIn(-180, 180),
                    tiltX = int(KEY_TILT_X, d.tiltX).coerceIn(-75, 75),
                    tiltY = int(KEY_TILT_Y, d.tiltY).coerceIn(-75, 75),
                    hyperBlur = int(KEY_HYPER_BLUR, 0) != 0,
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
                    marginTop = json.optInt("marginTop", d.marginTop),
                    marginStart = json.optInt("marginStart", d.marginStart),
                    rotation = json.optInt("rotation", d.rotation),
                    tiltX = json.optInt("tiltX", d.tiltX),
                    tiltY = json.optInt("tiltY", d.tiltY),
                    hyperBlur = json.optBoolean("hyperBlur", d.hyperBlur),
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
            .put("marginTop", marginTop)
            .put("marginStart", marginStart)
            .put("rotation", rotation)
            .put("tiltX", tiltX)
            .put("tiltY", tiltY)
            .put("hyperBlur", hyperBlur)
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
            put(KEY_MARGIN_TOP, marginTop)
            put(KEY_MARGIN_START, marginStart)
            put(KEY_ROTATION, rotation)
            put(KEY_TILT_X, tiltX)
            put(KEY_TILT_Y, tiltY)
            put(KEY_HYPER_BLUR, hyperBlur)
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
        catalogue?.let { return it }
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

    fun frame(context: Context, tuning: Tuning = Tuning.load(context), style: Int = 0): Frame {
        val density = context.resources.displayMetrics.density
        val screen = context.resources.displayMetrics.widthPixels
        val res = load(context)?.systemUi?.resources
        fun dimen(name: String, fallbackDp: Float): Int {
            val id = res?.getIdentifier(name, "dimen", SYSTEMUI) ?: 0
            return if (id != 0) res!!.getDimensionPixelSize(id) else (fallbackDp * density).toInt()
        }
        val statusBar = dimen("status_bar_height", 28f)
        val side = dimen("below_clock_padding_start", 32f)
        return Frame(
            top = (statusBar * 1.25f + tuning.marginTop * density).toInt(),
            side = side + if (supportsHorizontalMargin(style)) {
                (tuning.marginStart * density).toInt()
            } else 0,
            width = screen - side * 2,
            scale = tuning.scale.coerceIn(50, 150) / 100f,
            alpha = tuning.opacity.coerceIn(0, 100) / 100f,
        )
    }

    /**
     * The style drawn at [width] pixels with the current time, or null for the default clock.
     * [trim] crops the empty space around it, for small tiles.
     */
    fun preview(context: Context, style: Int, width: Int, color: Int?,
            trim: Boolean = false, tuning: Tuning? = null): Bitmap? {
        if (style <= 0) return null
        val minute = System.currentTimeMillis() / 60_000
        val key = "$style/$width/$color/$minute/$trim/$tuning"
        previews.get(key)?.let { return it }
        val cat = load(context) ?: return null
        if (style >= cat.layouts.size) return null
        return try {
            val inflater = LayoutInflater.from(cat.inflateContext).cloneInContext(
                cat.inflateContext).apply { factory2 = SystemUiViews(cat.systemUi?.classLoader) }
            val view = inflater.inflate(cat.layouts[style], null)
            prepare(view, if (isColourable(style)) color ?: Color.WHITE else null)
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            if (view.measuredHeight <= 0) return null
            if (isColourable(style)) {
                prepareColours(view, view, color ?: Color.WHITE, tuning)
            }
            Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
                .let {
                    view.draw(Canvas(it))
                    if (trim) trimmed(it) else it
                }
                ?.also { previews.put(key, it) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not draw clock style $style", e)
            null
        }
    }

    /**
     * SystemUI's clock views only need a context, so they're loaded from its code. Its other
     * views (weather and the like) need SystemUI itself; plain views keep the layout's shape.
     */
    private class SystemUiViews(private val loader: ClassLoader?) : LayoutInflater.Factory2 {
        override fun onCreateView(parent: View?, name: String, context: Context,
                attrs: AttributeSet): View? {
            if (!name.contains('.') || name.startsWith("android.")) return null
            if (loader != null && name.startsWith("$SYSTEMUI.clocks.")) {
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
            // The lock screen dims the wallpaper behind the clock and the previews don't, so
            // white clocks vanish on light wallpapers without this.
            view.setShadowLayer(view.textSize * 0.08f, 0f, view.textSize * 0.02f, PREVIEW_SHADOW)
        }
        if (view is ViewGroup) {
            view.clipChildren = false
            for (i in 0 until view.childCount) prepare(view.getChildAt(i), color)
        }
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
            val cls = view.javaClass
            cls.getMethod("refreshTime").invoke(view)
            cls.getMethod("setDigitColor", Int::class.javaPrimitiveType)
                .invoke(view, if (shader != null) Color.WHITE else color)
            cls.getMethod("setColonFollowsDigits", Boolean::class.javaPrimitiveType)
                .invoke(view, shader != null || color != Color.WHITE)
            cls.getMethod("setGradientShader", Shader::class.java).invoke(view, shader)
            cls.getMethod("setBlurEnabled", Boolean::class.javaPrimitiveType)
                .invoke(view, tuning?.hyperBlur == true && shader == null)
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
    fun apply(context: Context, style: Int, color: Int?, tuning: Tuning? = null) {
        val resolver = context.contentResolver
        val before = runCatching { Settings.Secure.getInt(resolver, KEY_STYLE, 0) }.getOrDefault(0)
        Settings.Secure.putInt(resolver, KEY_STYLE, style)
        setAospClockHidden(context, style != 0)
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
        if ((before == 0) != (style == 0)) {
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
