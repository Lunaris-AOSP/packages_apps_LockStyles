/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lunaris.lockstyles.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.lunaris.clockmaker.ClockDesign
import org.lunaris.clockmaker.ClockDesignAppearance
import org.lunaris.clockmaker.ClockDesignPositioning
import org.lunaris.clockmaker.ClockDesignView
import org.lunaris.clockmaker.ClockEffects
import org.lunaris.lockstyles.ClockStyle
import org.lunaris.lockstyles.CustomClocks
import org.lunaris.lockstyles.GalleryWallpaper

@Composable
fun MakerClockLayer(style: ClockStyle, tuning: CustomClocks.Tuning, modifier: Modifier = Modifier,
    thumbnail: Boolean = false, dozing: Boolean = false, selectedId: String? = null, onSelect: ((String) -> Unit)? = null,
    onMove: ((String, Float, Float) -> Unit)? = null, onDragFinished: (() -> Unit)? = null,
    onPositioningChanged: ((ClockDesignPositioning) -> Unit)? = null,
    onBottomChanged: ((Float) -> Unit)? = null, wallpaper: GalleryWallpaper? = null,
    onPinchStart: (() -> Unit)? = null, onPinch: ((Float) -> Unit)? = null,
    onPinchEnd: (() -> Unit)? = null) {
    val context = LocalContext.current
    val fallback = remember { ClockDesign() }
    val selectionColor = MaterialTheme.colorScheme.primary.toArgb()
    val glass = rememberClockGlass(wallpaper, !dozing && style.design?.elements?.any { it.glass && it.visible } == true,
        style.design?.elements?.filter { it.glass && it.visible }?.map { it.glassStyle.blurRadius }?.distinct().orEmpty())
    val wantGlass = !dozing && style.design?.elements?.any { it.glass && it.visible } == true
    val hold = holdPreview(wantGlass && wallpaper != null, glass != null, wallpaper)
    val shown by animateFloatAsState(if (hold) 0f else 1f, tween(160), label = "glass_ready")
    AndroidView(factory = { MakerPreview(it) }, modifier = modifier.fillMaxSize().alpha(shown), update = {
        it.onPinchStart = onPinchStart
        it.onPinch = onPinch
        it.onPinchEnd = onPinchEnd
        it.clock.setGlassPreview(glass, it.stage)
        it.thumbnail = thumbnail
        it.tuning = tuning
        it.clock.selectedId = selectedId
        it.clock.selectionColor = selectionColor
        it.clock.onSelect = onSelect
        it.onMove = onMove
        it.onPositioningChanged = onPositioningChanged
        it.onBottomChanged = onBottomChanged
        it.clock.onDragFinished = onDragFinished
        // AOD previews must use the same non-animated image path as the dozing lock screen.
        it.clock.animationsEnabled = !thumbnail && !dozing && tuning.opacity > 0
        val design = style.design ?: fallback
        if (it.clock.design != design) it.clock.setDesign(design)
        it.clock.setAppearance(ClockDesignAppearance(
            color = if (tuning.accent) context.getColor(android.R.color.system_accent1_100) else style.color ?: Color.WHITE,
            gradient = tuning.gradient, gradientStart = tuning.gradientStart, gradientEnd = tuning.gradientEnd,
            gradientAnchor = tuning.gradientAnchorY, gradientRadius = tuning.gradientRadius,
            weather = tuning.weather, dozing = dozing, aodAnimation = tuning.aodAnimation))
        it.setPreviewDozing(dozing)
        it.requestLayout()
    })
}

private class MakerPreview(context: Context) : ViewGroup(context) {
    val clock = ClockDesignView(context)
    var tuning = CustomClocks.Tuning()
    var thumbnail = false
    private var dozing = false
    private var aodProgress = 0f
    private var aodAnimator: ValueAnimator? = null
    var onMove: ((String, Float, Float) -> Unit)? = null
    var onPositioningChanged: ((ClockDesignPositioning) -> Unit)? = null
    var onBottomChanged: ((Float) -> Unit)? = null
    var onPinchStart: (() -> Unit)? = null
    var onPinch: ((Float) -> Unit)? = null
    var onPinchEnd: (() -> Unit)? = null
    private var pinching = false
    private val pinch = TwoFingerPinch(context, { onPinchStart?.invoke() }, { onPinch?.invoke(it) },
        { onPinchEnd?.invoke() })
    private var positioning: ClockDesignPositioning? = null
    private var frame = CustomClocks.frame(context, tuning, ClockDesign.STYLE)

    fun setPreviewDozing(value: Boolean) {
        if (dozing == value) return
        dozing = value
        aodAnimator?.cancel()
        aodAnimator = null
        val target = if (value) 1f else 0f
        if (!isAttachedToWindow || thumbnail || !tuning.aodAnimation) {
            aodProgress = target
            clock.setAodProgress(target)
            return
        }
        aodAnimator = ValueAnimator.ofFloat(aodProgress, target).apply {
            duration = 500L
            addUpdateListener {
                aodProgress = it.animatedValue as Float
                clock.setAodProgress(aodProgress)
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        aodAnimator?.cancel()
        aodAnimator = null
        aodProgress = if (dozing) 1f else 0f
        clock.setAodProgress(aodProgress)
        super.onDetachedFromWindow()
    }

    val stage = object : ViewGroup(context) {
        init {
            clipChildren = false
            clipToPadding = false
            addView(clock)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            clock.measure(MeasureSpec.makeMeasureSpec(frame.width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val w = clock.measuredWidth
            val h = clock.measuredHeight
            clock.layout(frame.side, frame.top, frame.side + w, frame.top + h)
            clock.pivotX = w / 2f
            clock.pivotY = clock.transformPivotY
            clock.scaleX = frame.scale
            clock.scaleY = frame.scale
            clock.rotation = tuning.rotation.toFloat()
            clock.rotationX = tuning.tiltX.toFloat()
            clock.rotationY = tuning.tiltY.toFloat()
            clock.cameraDistance = maxOf(w * 4f, 1280f * resources.displayMetrics.density)
            clock.alpha = frame.alpha
            clock.setRenderEffect(if (tuning.shadowEnabled && !dozing) ClockEffects.shadow(tuning.shadowColor,
                tuning.shadowOpacity, tuning.shadowSoftness, resources.displayMetrics.density) else null)
            val matrix = Matrix(clock.matrix).apply {
                postTranslate(clock.left.toFloat(), clock.top.toFloat())
            }
            val placement = ClockDesignPositioning(w, measuredWidth, measuredHeight, matrix)
            positioning = placement
            onPositioningChanged?.invoke(placement)
        }
    }

    init {
        clipChildren = false
        clipToPadding = false
        addView(stage)
        clock.onMove = { id, x, y ->
            val position = positioning?.move(x, y) ?: (x to y)
            onMove?.invoke(id, position.first, position.second)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (clock.onSelect == null) return super.dispatchTouchEvent(event)
        if (onPinch != null) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) pinching = false
            if (event.pointerCount >= 2 || pinching) {
                if (!pinching) {
                    pinching = true
                    val cancel = MotionEvent.obtain(event.downTime, event.eventTime, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
                    try { clock.dispatchTouchEvent(cancel) } finally { cancel.recycle() }
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
        }
        val matrix = Matrix()
        clock.transformMatrixToGlobal(matrix)
        transformMatrixToLocal(matrix)
        val inverse = Matrix()
        if (!matrix.invert(inverse)) return false
        val mapped = MotionEvent.obtain(event)
        return try {
            mapped.transform(inverse)
            clock.dispatchTouchEvent(mapped)
        } finally {
            mapped.recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val h = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1)
        setMeasuredDimension(w, h)
        frame = CustomClocks.frame(context, tuning, ClockDesign.STYLE)
        stage.forceLayout()
        stage.measure(MeasureSpec.makeMeasureSpec(resources.displayMetrics.widthPixels, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(resources.displayMetrics.heightPixels, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        stage.layout(0, 0, stage.measuredWidth, stage.measuredHeight)
        val bounds = RectF(0f, 0f, clock.width.toFloat(), clock.height.toFloat())
        if (thumbnail || onBottomChanged != null) {
            for (index in 0 until clock.childCount) {
                val child = clock.getChildAt(index)
                if (child.visibility != VISIBLE) continue
                val childBounds = RectF(0f, 0f, child.width.toFloat(), child.height.toFloat())
                child.matrix.mapRect(childBounds)
                childBounds.offset(child.left.toFloat(), child.top.toFloat())
                bounds.union(childBounds)
            }
        }
        clock.matrix.mapRect(bounds)
        bounds.offset(clock.left.toFloat(), clock.top.toFloat())
        val scale = if (thumbnail) minOf(width / bounds.width().coerceAtLeast(1f),
            height / bounds.height().coerceAtLeast(1f)) * 0.9f else width / stage.width.toFloat()
        stage.pivotX = 0f
        stage.pivotY = 0f
        stage.scaleX = scale
        stage.scaleY = scale
        stage.translationX = if (thumbnail) width / 2f - bounds.centerX() * scale else 0f
        stage.translationY = if (thumbnail) height / 2f - bounds.centerY() * scale else 0f
        onBottomChanged?.invoke(bounds.bottom * scale + stage.translationY)
    }
}
