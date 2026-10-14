/* SPDX-License-Identifier: Apache-2.0 */
package org.lunaris.lockstyles.settings.ui

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.lunaris.lockscreenwidgets.WidgetClient
import org.lunaris.lockscreenwidgets.WidgetContract
import org.lunaris.lockscreenwidgets.WidgetEditorView
import org.lunaris.lockscreenwidgets.WidgetEntry
import org.lunaris.lockscreenwidgets.R as WidgetR

@Composable
fun AppWidgetsPanel() {
    val context = LocalContext.current
    val previews = remember { mutableMapOf<Int, AppWidgetHostView>() }
    val remoteViews = remember { mutableMapOf<Int, RemoteViews?>() }
    var client by remember { mutableStateOf<WidgetClient?>(null) }
    var snapshot by remember { mutableStateOf(Bundle()) }
    var error by remember { mutableStateOf<WidgetClient.Error?>(null) }
    var connected by remember { mutableStateOf(false) }
    val lifecycle = rememberWidgetLifecycle()
    fun configure(id: Int = -1) {
        runCatching {
            context.startActivity(Intent().setClassName("com.android.systemui", WidgetContract.ACTIVITY)
                .putExtra("id", id))
        }.onFailure { Toast.makeText(context, WidgetR.string.app_widgets_failed, Toast.LENGTH_LONG).show() }
    }
    DisposableEffect(context, lifecycle) {
        fun connect() {
            if (client != null) return
            client = WidgetClient(context, onSnapshot = {
                snapshot = it
                val ids = WidgetEntry.decode(it.getString("config")).map { entry -> entry.id }.toSet()
                previews.keys.retainAll(ids)
                remoteViews.keys.retainAll(ids)
                error = null
                connected = true
            }, onViews = { id, views ->
                remoteViews[id] = views
                previews[id]?.updateAppWidget(views)
            }, onDataChanged = { id, viewId -> previews[id]?.onViewDataChanged(viewId) },
                onError = {
                    error = it
                    if (it == WidgetClient.Error.HOST_UNAVAILABLE) connected = false
                }).also { it.start() }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> connect()
                Lifecycle.Event.ON_PAUSE -> { client?.close(); client = null; connected = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) connect()
        onDispose { lifecycle.removeObserver(observer); client?.close(); client = null; previews.clear() }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(WidgetR.string.app_widgets_title), style = MaterialTheme.typography.titleLarge)
            Switch(checked = snapshot.getBoolean("enabled"),
                enabled = connected,
                onCheckedChange = { client?.change("enable", it.toString()) })
        }
        Text(stringResource(WidgetR.string.app_widgets_summary), style = MaterialTheme.typography.bodyMedium)
        error?.let { failure ->
            Text(stringResource(failure.messageResId), color = MaterialTheme.colorScheme.error)
            if (failure == WidgetClient.Error.HOST_UNAVAILABLE) {
                TextButton(onClick = { client?.start() }) {
                    Text(stringResource(WidgetR.string.app_widgets_retry))
                }
            }
        }
        if (connected) AndroidView(
            factory = { ctx -> WidgetEditorView(ctx) },
            modifier = Modifier.fillMaxWidth(),
            update = { editor ->
                editor.onAdd = { configure() }
                editor.onConfigure = { configure(it) }
                editor.onRemove = { client?.change("remove", it.toString()) }
                editor.onChange = { entry -> client?.change("change", extras = entry.bundle()) }
                editor.sizingWidth = snapshot.getInt("gridWidth")
                val entries = WidgetEntry.decode(snapshot.getString("config"))
                val infos = entries.mapNotNull { e ->
                    snapshot.getParcelable(e.id.toString(), AppWidgetProviderInfo::class.java)?.let { e.id to it }
                }.toMap()
                editor.update(entries, infos) { entry ->
                    val info = infos[entry.id]
                    if (info == null) TextView(context).apply { setText(WidgetR.string.app_widgets_unavailable) }
                    else AppWidgetHostView(context, RemoteViews.InteractionHandler { _, _, _ -> true }).apply {
                        setAppWidget(entry.id, info)
                        updateAppWidget(remoteViews[entry.id])
                        previews[entry.id] = this
                    }
                }
            },
            onRelease = { previews.clear() },
        )
    }
}

@Composable
internal fun rememberWidgetLifecycle(): Lifecycle {
    val context = LocalContext.current
    return remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<ComponentActivity>().first().lifecycle
    }
}
