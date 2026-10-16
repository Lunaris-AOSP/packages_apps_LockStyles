/* SPDX-License-Identifier: Apache-2.0 */
package org.lunaris.lockstyles.settings.ui

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.RemoteViews
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.lunaris.lockscreenwidgets.WidgetGridView
import org.lunaris.lockscreenwidgets.viewKey
import org.lunaris.lockscreenwidgets.R as WidgetR

@Composable
internal fun AppWidgetGrid(
    modifier: Modifier = Modifier,
    editing: Boolean = false,
    selectedId: Int = -1,
    onSelect: (Int) -> Unit = {},
) {
    val state = LocalAppWidgets.current
    val snapshot = state.snapshot
    AndroidView(
        factory = { ScaledWidgetGrid(it, state) },
        modifier = modifier,
        update = {
            it.grid.editing = editing
            it.grid.selectedId = selectedId
            it.grid.onSelect = onSelect
            it.render(snapshot.getInt("gridWidth").takeIf { width -> width > 0 } ?: state.gridWidth)
        },
        onRelease = { it.release() },
    )
}

private class ScaledWidgetGrid(context: Context, private val state: AppWidgetState) : ViewGroup(context) {
    val grid = WidgetGridView(context)
    private var sizingWidth = 1
    private var providerKeys = emptyMap<Int, String?>()
    private val hosts = mutableMapOf<Int, AppWidgetHostView>()

    init {
        addView(grid)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    fun release() {
        hosts.forEach { (id, host) -> state.detach(id, host) }
        hosts.clear()
        grid.clearWidgets()
    }

    fun render(width: Int) {
        sizingWidth = width.coerceAtLeast(1)
        val entries = state.entries
        val keys = entries.associate { it.id to state.info(it.id)?.viewKey() }
        if (keys != providerKeys) {
            release()
            providerKeys = keys
        }
        grid.setWidgets(entries) { entry ->
            val info = state.info(entry.id)
            if (info == null) TextView(context).apply { setText(WidgetR.string.app_widgets_unavailable) }
            else AppWidgetHostView(context, RemoteViews.InteractionHandler { _, _, _ -> true }).apply {
                setAppWidget(entry.id, info)
                hosts[entry.id] = this
                state.attach(entry.id, this)
            }
        }
        requestLayout()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        if (grid.editing) super.dispatchTouchEvent(event) else false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        grid.measure(MeasureSpec.makeMeasureSpec(sizingWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val scale = width / sizingWidth.toFloat()
        setMeasuredDimension(width, resolveSize((grid.measuredHeight * scale).toInt(), heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        grid.layout(0, 0, grid.measuredWidth, grid.measuredHeight)
        grid.pivotX = 0f
        grid.pivotY = 0f
        grid.scaleX = width / sizingWidth.toFloat()
        grid.scaleY = grid.scaleX
    }
}
