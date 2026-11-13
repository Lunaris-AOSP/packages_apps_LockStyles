/* SPDX-License-Identifier: Apache-2.0 */
package org.lunaris.lockstyles.settings.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import java.util.concurrent.Executors
import org.lunaris.clockmaker.ClockGlass
import org.lunaris.extrainfowidgets.ExtraInfoFreeformView
import org.lunaris.extrainfowidgets.ExtraInfoGridView
import org.lunaris.extrainfowidgets.InfoWidget
import org.lunaris.extrainfowidgets.InfoWidgetView

private class ExtraInfoBackdrop(context: Context) : View(context) {
    var wallpaper: Bitmap? = null
        set(value) {
            if (field === value) return
            field = value
            invalidate()
            revision++
            material = suppliedMaterial
            onGlassReady?.invoke(material)
            prepareGlass()
        }
    var wallpaperOffset = 0f
        set(value) {
            if (field == value) return
            field = value
            invalidate()
            onGlassReady?.invoke(material)
        }
    var glassEnabled = false
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                onGlassReady?.invoke(material)
                prepareGlass()
            }
        }
    var onGlassReady: ((ClockGlass?) -> Unit)? = null
    private var material: ClockGlass? = null
    private var suppliedMaterial: ClockGlass? = null
    private var revision = 0
    private var preparing = -1

    fun setGlassMaterial(value: ClockGlass?) {
        if (suppliedMaterial === value) return
        suppliedMaterial = value
        revision++
        material = value
        onGlassReady?.invoke(value)
        prepareGlass()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prepareGlass()
    }

    override fun onDetachedFromWindow() {
        revision++
        preparing = -1
        material = suppliedMaterial
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (wallpaper == null && suppliedMaterial == null) {
            revision++
            material = null
        }
        prepareGlass()
        onGlassReady?.invoke(material)
    }

    private fun prepareGlass() {
        if (!glassEnabled || !isAttachedToWindow || width <= 0 || height <= 0 || material != null || preparing == revision) return
        val version = revision
        preparing = version
        val metrics = resources.displayMetrics
        val bitmap = wallpaper ?: run {
            val w = minOf(width, 540)
            val h = (w * metrics.heightPixels.toFloat() / metrics.widthPixels.coerceAtLeast(1)).toInt().coerceAtLeast(1)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                val gradient = Paint().apply {
                    shader = LinearGradient(0f, 0f, w.toFloat(), height * w.toFloat() / width,
                        intArrayOf(0xFF29465B.toInt(), 0xFF4C3557.toInt(), 0xFF1E2635.toInt()), null, Shader.TileMode.CLAMP)
                }
                Canvas(it).drawRect(0f, 0f, w.toFloat(), h.toFloat(), gradient)
            }
        }
        worker.execute {
            val result = runCatching {
                ClockGlass.prepare(bitmap, referenceWidth = metrics.widthPixels, referenceHeight = metrics.heightPixels)
            }.getOrNull()
            main.post {
                if (revision == version) {
                    material = result
                    onGlassReady?.invoke(result)
                }
            }
        }
    }

    companion object {
        private val worker = Executors.newSingleThreadExecutor()
        private val main = Handler(Looper.getMainLooper())
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun onDraw(canvas: Canvas) {
        val bitmap = wallpaper
        if (bitmap != null && !bitmap.isRecycled) {
            paint.shader = null
            val height = width * bitmap.height.toFloat() / bitmap.width
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(0f, -wallpaperOffset, width.toFloat(), height - wallpaperOffset),
                paint,
            )
        } else {
            paint.shader =
                LinearGradient(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    intArrayOf(0xFF29465B.toInt(), 0xFF4C3557.toInt(), 0xFF1E2635.toInt()),
                    null,
                    Shader.TileMode.CLAMP,
                )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }
}

internal class ExtraInfoPreviewHost(context: Context) : FrameLayout(context) {
    val grid = ExtraInfoGridView(context)
    private val source = ExtraInfoBackdrop(context)
    var wallpaper: Bitmap?
        get() = source.wallpaper
        set(value) {
            source.wallpaper = value
        }

    var wallpaperOffset: Float
        get() = source.wallpaperOffset
        set(value) {
            source.wallpaperOffset = value
        }

    init {
        source.visibility = INVISIBLE
        addView(source, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        grid.blurSource = source
        grid.setGlassPreview(null, source)
        source.onGlassReady = { grid.setGlassPreview(it, source, source.wallpaperOffset) }
    }

    fun setGlassEnabled(enabled: Boolean) { source.glassEnabled = enabled }

    fun setGlassMaterial(material: ClockGlass?) { source.setGlassMaterial(material) }

    fun showBackdrop(visible: Boolean) {
        source.visibility = if (visible) VISIBLE else INVISIBLE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        grid.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        setMeasuredDimension(
            grid.measuredWidth,
            resolveSize(grid.measuredHeight, heightMeasureSpec),
        )
        val widthSpec = MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
        source.measure(widthSpec, heightSpec)
        grid.measure(widthSpec, heightSpec)
    }

    fun refreshSource() {
        grid.blurSource = source
    }
}

internal class ExtraInfoDesignPreviewHost(context: Context, design: InfoWidget) :
    FrameLayout(context) {
    val widget = InfoWidgetView(context, design)
    private val source = ExtraInfoBackdrop(context)

    init {
        source.visibility = INVISIBLE
        addView(source, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(widget, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        widget.setBlurSource(source)
        widget.setGlassPreview(null, source)
        source.onGlassReady = { widget.setGlassPreview(it, source) }
    }

    fun setGlassEnabled(enabled: Boolean) { source.glassEnabled = enabled }

    fun setGlassMaterial(material: ClockGlass?) { source.setGlassMaterial(material) }
}

internal class ExtraInfoFreeformPreviewHost(context: Context) : FrameLayout(context) {
    val widgets = ExtraInfoFreeformView(context)
    var onPinchStart: (() -> Unit)? = null
    var onPinch: ((Float) -> Unit)? = null
    var onPinchEnd: (() -> Unit)? = null
    private var pinching = false
    private val pinch = org.lunaris.lockstyles.ui.TwoFingerPinch(context, { onPinchStart?.invoke() },
        { onPinch?.invoke(it) }, { onPinchEnd?.invoke() })

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (onPinch == null) return super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) pinching = false
        if (event.pointerCount >= 2 || pinching) {
            if (!pinching) {
                pinching = true
                val cancel = MotionEvent.obtain(event.downTime, event.eventTime, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
                try { super.dispatchTouchEvent(cancel) } finally { cancel.recycle() }
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            pinch.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                pinching = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            return true
        }
        pinch.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }
    private val source = ExtraInfoBackdrop(context)
    var wallpaper: Bitmap?
        get() = source.wallpaper
        set(value) {
            if (source.wallpaper !== value) {
                source.wallpaper = value
                widgets.blurSource = source
            }
        }

    init {
        source.visibility = INVISIBLE
        addView(source, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(widgets, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        widgets.blurSource = source
        widgets.setGlassPreview(null, source)
        source.onGlassReady = { widgets.setGlassPreview(it, source) }
    }

    fun setGlassEnabled(enabled: Boolean) { source.glassEnabled = enabled }

    fun setGlassMaterial(material: ClockGlass?) { source.setGlassMaterial(material) }
}
