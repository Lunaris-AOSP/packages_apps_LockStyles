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

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.lunaris.clockmaker.ClockDesign
import org.lunaris.clockmaker.ClockDesignAppearance
import org.lunaris.clockmaker.ClockDesignView
import org.lunaris.clockmaker.ClockEffects
import org.lunaris.lockstyles.ClockStyle
import org.lunaris.lockstyles.CustomClocks

@Composable
fun MakerClockLayer(style: ClockStyle, tuning: CustomClocks.Tuning, modifier: Modifier = Modifier,
    thumbnail: Boolean = false, dozing: Boolean = false, selectedId: String? = null, onSelect: ((String) -> Unit)? = null,
    onMove: ((String, Float, Float) -> Unit)? = null, onDragFinished: (() -> Unit)? = null) {
    val context = LocalContext.current
    val fallback = remember { ClockDesign() }
    val selectionColor = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(factory = { MakerPreview(it) }, modifier = modifier.fillMaxSize(), update = {
        it.thumbnail = thumbnail
        it.dozing = dozing
        it.tuning = tuning
        it.clock.selectedId = selectedId
        it.clock.selectionColor = selectionColor
        it.clock.onSelect = onSelect
        it.clock.onMove = onMove
        it.clock.onDragFinished = onDragFinished
        // AOD previews must use the same non-animated image path as the dozing lock screen.
        it.clock.animationsEnabled = !thumbnail && !dozing && tuning.opacity > 0
        val design = style.design ?: fallback
        if (it.clock.design != design) it.clock.setDesign(design)
        it.clock.setAppearance(ClockDesignAppearance(
            color = if (tuning.accent) context.getColor(android.R.color.system_accent1_100) else style.color ?: Color.WHITE,
            gradient = tuning.gradient, gradientStart = tuning.gradientStart, gradientEnd = tuning.gradientEnd,
            gradientAnchor = tuning.gradientAnchorY, gradientRadius = tuning.gradientRadius,
            weather = tuning.weather, dozing = dozing))
        it.requestLayout()
    })
}

private class MakerPreview(context: Context) : ViewGroup(context) {
    val clock = ClockDesignView(context)
    var tuning = CustomClocks.Tuning()
    var thumbnail = false
    var dozing = false
    private var frame = CustomClocks.frame(context, tuning, ClockDesign.STYLE)
    private val stage = object : ViewGroup(context) {
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
            clock.pivotY = h / 2f
            clock.scaleX = frame.scale
            clock.scaleY = frame.scale
            clock.rotation = tuning.rotation.toFloat()
            clock.rotationX = tuning.tiltX.toFloat()
            clock.rotationY = tuning.tiltY.toFloat()
            clock.cameraDistance = maxOf(maxOf(w, h) * 4f, 1280f * resources.displayMetrics.density)
            clock.alpha = frame.alpha
            clock.setRenderEffect(if (tuning.shadowEnabled && !dozing) ClockEffects.shadow(tuning.shadowColor,
                tuning.shadowOpacity, tuning.shadowSoftness, resources.displayMetrics.density) else null)
        }
    }

    init {
        clipChildren = false
        clipToPadding = false
        addView(stage)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val h = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1)
        setMeasuredDimension(w, h)
        frame = CustomClocks.frame(context, tuning, ClockDesign.STYLE)
        stage.measure(MeasureSpec.makeMeasureSpec(resources.displayMetrics.widthPixels, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(resources.displayMetrics.heightPixels, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        stage.layout(0, 0, stage.measuredWidth, stage.measuredHeight)
        val scale = if (thumbnail) minOf(width / clock.measuredWidth.toFloat(),
            height / clock.measuredHeight.toFloat()) * 0.9f / frame.scale else width / stage.width.toFloat()
        stage.pivotX = 0f
        stage.pivotY = 0f
        stage.scaleX = scale
        stage.scaleY = scale
        stage.translationX = if (thumbnail) width / 2f - (frame.side + clock.width / 2f) * scale else 0f
        stage.translationY = if (thumbnail) height / 2f - (frame.top + clock.height / 2f) * scale else 0f
    }
}
