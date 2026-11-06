/* SPDX-License-Identifier: Apache-2.0 */
package org.lunaris.lockstyles.settings.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.widget.FrameLayout
import org.lunaris.extrainfowidgets.ExtraInfoGridView
import org.lunaris.extrainfowidgets.InfoWidget
import org.lunaris.extrainfowidgets.InfoWidgetView

private class ExtraInfoBackdrop(context: Context) : View(context) {
    var wallpaper: Bitmap? = null
    var wallpaperOffset = 0f
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
    }
}
