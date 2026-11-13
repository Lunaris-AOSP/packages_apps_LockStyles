/*
 * Copyright (C) 2026 RisingOS (revived) Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.android.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lunaris.lockstyles.ui

import android.content.Context
import android.view.MotionEvent
import kotlin.math.hypot
import kotlin.math.pow

internal class TwoFingerPinch(
    context: Context,
    private val onStart: () -> Unit,
    private val onScale: (Float) -> Unit,
    private val onEnd: () -> Unit,
    private val gain: Float = 1.6f,
) {
    private val floor = 6f * context.resources.displayMetrics.density
    private var active = false
    private var startSpan = 1f
    private var firstPointer = -1
    private var secondPointer = -1

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> finish()
            MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount == 2 && !active) {
                firstPointer = event.getPointerId(0)
                secondPointer = event.getPointerId(1)
                startSpan = span(event, 0, 1).coerceAtLeast(floor)
                active = true
                onStart()
            }
            MotionEvent.ACTION_MOVE -> if (active && event.pointerCount >= 2) {
                val first = event.findPointerIndex(firstPointer)
                val second = event.findPointerIndex(secondPointer)
                if (first < 0 || second < 0) finish()
                else onScale((span(event, first, second).coerceAtLeast(floor) / startSpan).pow(gain))
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val lifted = event.getPointerId(event.actionIndex)
                if (lifted == firstPointer || lifted == secondPointer) finish()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> finish()
        }
        return active
    }

    private fun finish() {
        val wasActive = active
        active = false
        firstPointer = -1
        secondPointer = -1
        if (wasActive) onEnd()
    }

    private fun span(event: MotionEvent, first: Int, second: Int) =
        hypot(event.getX(first) - event.getX(second), event.getY(first) - event.getY(second))
}
