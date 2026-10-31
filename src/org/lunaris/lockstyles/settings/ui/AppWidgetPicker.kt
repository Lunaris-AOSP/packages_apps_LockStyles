/* SPDX-License-Identifier: Apache-2.0 */
package org.lunaris.lockstyles.settings.ui

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Process
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lunaris.lockscreenwidgets.R as WidgetR
import org.lunaris.lockscreenwidgets.WidgetEntry
import org.lunaris.lockscreenwidgets.WidgetSizes
import org.lunaris.lockstyles.R
import org.lunaris.lockstyles.ui.ExpressiveLoadingIndicator

private data class WidgetChoice(val info: AppWidgetProviderInfo, val label: String, val sizes: List<Pair<Int, Int>>)
private data class WidgetApp(val packageName: String, val label: String, val icon: Drawable?, val widgets: List<WidgetChoice>)

@Composable
internal fun AppWidgetPicker(onPick: (AppWidgetProviderInfo, Pair<Int, Int>) -> Unit) {
    val context = LocalContext.current
    val state = LocalAppWidgets.current
    val configuration = LocalConfiguration.current
    var query by rememberSaveable { mutableStateOf("") }
    var retry by rememberSaveable { mutableStateOf(0) }
    val apps by produceState<Result<List<WidgetApp>>?>(null, state.gridWidth, configuration, retry) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching {
            val pm = context.packageManager
            val collator = Collator.getInstance()
            val sizes = WidgetSizes(context, state.gridWidth)
            AppWidgetManager.getInstance(context).getInstalledProvidersForProfile(
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN or AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD,
                Process.myUserHandle(), null)
                .mapNotNull { info ->
                    val allowed = sizes.allowed(info)
                    if (allowed.isEmpty()) null else WidgetChoice(info, info.loadLabel(pm).toString(), allowed)
                }.groupBy { it.info.provider.packageName }.mapNotNull { (pkg, widgets) ->
                    val app = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@mapNotNull null
                    WidgetApp(pkg, pm.getApplicationLabel(app).toString(),
                        runCatching { pm.getApplicationIcon(app) }.getOrNull(),
                        widgets.sortedWith { a, b -> collator.compare(a.label, b.label) })
                }.sortedWith { a, b -> collator.compare(a.label, b.label) }
        } }
    }
    Column {
        Text(stringResource(R.string.app_widget_picker_summary),
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text(stringResource(R.string.app_widget_search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp))
        val loaded = apps
        when {
            loaded == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                ExpressiveLoadingIndicator()
            }
            loaded.isFailure -> Column(Modifier.padding(24.dp)) {
                Text(stringResource(WidgetR.string.app_widgets_failed), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retry++ }) { Text(stringResource(WidgetR.string.app_widgets_retry)) }
            }
            else -> {
                val all = loaded.getOrThrow()
                val search = query.trim()
                val filtered = all.mapNotNull { app ->
                    val widgets = if (app.label.contains(search, ignoreCase = true)) app.widgets
                        else app.widgets.filter { it.label.contains(search, ignoreCase = true) }
                    app.copy(widgets = widgets).takeIf { widgets.isNotEmpty() }
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    if (filtered.isEmpty()) item {
                        Text(stringResource(if (all.isEmpty()) WidgetR.string.app_widgets_no_providers
                            else R.string.app_widget_no_results), Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(filtered, key = { it.packageName }) { app ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DrawableImage(app.icon, Modifier.size(40.dp))
                                Column {
                                    Text(app.label, style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(R.string.app_widget_count, app.widgets.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            LazyRow(contentPadding = PaddingValues(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(app.widgets, key = { it.info.provider.flattenToString() }) { widget ->
                                    val preferred = widget.info.targetCellWidth to widget.info.targetCellHeight
                                    val sizes = widget.sizes.sortedWith(compareBy<Pair<Int, Int>> { it != preferred }
                                        .thenBy { it.first * it.second })
                                    val available = sizes.firstOrNull { (w, h) ->
                                        WidgetEntry.place(Int.MAX_VALUE, w, h, state.entries) != null
                                    }
                                    val size = available ?: sizes.first()
                                    Card(onClick = { available?.let { onPick(widget.info, it) } },
                                        enabled = available != null && state.connected,
                                        shape = MaterialTheme.shapes.large,
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                        modifier = Modifier.width(240.dp)) {
                                        WidgetProviderPreview(widget.info, size, state.gridWidth,
                                            Modifier.fillMaxWidth().height(176.dp).padding(16.dp))
                                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(widget.label, style = MaterialTheme.typography.titleSmall,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(stringResource(R.string.app_widget_dimensions, size.first, size.second),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (available == null) Text(stringResource(WidgetR.string.app_widgets_no_space),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class ProviderPreview(val remoteViews: RemoteViews? = null, val image: Drawable? = null)

@Composable
private fun WidgetProviderPreview(info: AppWidgetProviderInfo, size: Pair<Int, Int>, gridWidth: Int, modifier: Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val preview by produceState<ProviderPreview?>(null, info, configuration) {
        value = withContext(Dispatchers.IO) {
            val manager = AppWidgetManager.getInstance(context)
            val generated = listOf(AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD,
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN).firstNotNullOfOrNull { category ->
                if (info.generatedPreviewCategories and category == 0) null else runCatching {
                    manager.getWidgetPreview(info.provider, info.profile, category)
                }.getOrNull()
            }
            val layout = info.previewLayout.takeIf { it != 0 }
            val image = if (generated == null && layout == null) runCatching {
                info.loadPreviewImage(context, context.resources.displayMetrics.densityDpi)
            }.getOrNull() else null
            ProviderPreview(remoteViews = generated ?: layout?.let { RemoteViews(info.provider.packageName, it) }, image = image)
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val loaded = preview
        when {
            loaded == null -> ExpressiveLoadingIndicator(Modifier.size(24.dp))
            loaded.remoteViews != null -> AndroidView(factory = { ProviderPreviewView(it) },
                modifier = Modifier.matchParentSize(), update = {
                    val sizes = WidgetSizes(context, gridWidth)
                    it.render(info, loaded.remoteViews, sizes.pixels(size.first), sizes.pixels(size.second))
                })
            loaded.image != null -> DrawableImage(loaded.image, Modifier.matchParentSize())
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Widgets, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.app_widget_no_preview), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DrawableImage(drawable: Drawable?, modifier: Modifier) {
    AndroidView(factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        modifier = modifier, update = { it.setImageDrawable(drawable) })
}

private class ProviderPreviewView(context: Context) : ViewGroup(context) {
    private val host = AppWidgetHostView(context, RemoteViews.InteractionHandler { _, _, _ -> true })
    private var contentWidth = 1
    private var contentHeight = 1
    private var remote: RemoteViews? = null
    init {
        addView(host)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    fun render(info: AppWidgetProviderInfo, views: RemoteViews, width: Int, height: Int) {
        contentWidth = width.coerceAtLeast(1)
        contentHeight = height.coerceAtLeast(1)
        if (remote !== views) {
            remote = views
            host.setAppWidget(AppWidgetManager.INVALID_APPWIDGET_ID, info)
            host.updateAppWidget(views)
        }
        requestLayout()
    }
    override fun dispatchTouchEvent(event: MotionEvent) = false
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        host.measure(MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY))
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        host.layout(0, 0, contentWidth, contentHeight)
        val scale = minOf(width / contentWidth.toFloat(), height / contentHeight.toFloat(), 1f)
        host.pivotX = 0f
        host.pivotY = 0f
        host.scaleX = scale
        host.scaleY = scale
        host.translationX = (width - contentWidth * scale) / 2f
        host.translationY = (height - contentHeight * scale) / 2f
    }
}
