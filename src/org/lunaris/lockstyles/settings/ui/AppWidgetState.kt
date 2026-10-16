/* SPDX-License-Identifier: Apache-2.0 */
package org.lunaris.lockstyles.settings.ui

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.ContextWrapper
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.lunaris.lockscreenwidgets.WidgetClient
import org.lunaris.lockscreenwidgets.WidgetContract
import org.lunaris.lockscreenwidgets.WidgetEntry

internal class AppWidgetState(private val context: Context) {
    var snapshot by mutableStateOf(Bundle())
        private set
    var error by mutableStateOf<WidgetClient.Error?>(null)
        private set
    var connected by mutableStateOf(false)
        private set
    private var client: WidgetClient? = null
    private val remoteViews = mutableMapOf<Int, RemoteViews?>()
    private val hosts = mutableMapOf<Int, MutableSet<AppWidgetHostView>>()
    val entries: List<WidgetEntry> get() = WidgetEntry.decode(snapshot.getString("config"))
    val enabled: Boolean get() = snapshot.getBoolean("enabled")
    val gridWidth: Int get() = snapshot.getInt("gridWidth").takeIf { it > 0 }
        ?: (context.resources.displayMetrics.widthPixels - 32 * context.resources.displayMetrics.density).toInt()

    fun info(id: Int): AppWidgetProviderInfo? =
        snapshot.getParcelable(id.toString(), AppWidgetProviderInfo::class.java)

    fun connect() {
        if (client != null) return
        client = WidgetClient(context, onSnapshot = {
            snapshot = it
            val ids = entries.map { entry -> entry.id }.toSet()
            remoteViews.keys.retainAll(ids)
            error = null
            connected = true
        }, onViews = { id, views ->
            remoteViews[id] = views
            hosts[id]?.forEach { it.updateAppWidget(views) }
        }, onDataChanged = { id, viewId -> hosts[id]?.forEach { it.onViewDataChanged(viewId) } },
            onError = {
                error = it
                if (it == WidgetClient.Error.HOST_UNAVAILABLE) connected = false
            }).also { it.start() }
    }

    fun disconnect() {
        client?.close()
        client = null
        connected = false
    }

    fun refresh() { client?.refresh() }
    fun change(method: String, arg: String? = null, extras: Bundle? = null) {
        client?.change(method, arg, extras)
    }

    fun attach(id: Int, host: AppWidgetHostView) {
        hosts.getOrPut(id) { mutableSetOf() }.add(host)
        host.updateAppWidget(remoteViews[id])
    }

    fun detach(id: Int, host: AppWidgetHostView) {
        hosts[id]?.let { if (it.remove(host) && it.isEmpty()) hosts.remove(id) }
    }
}

internal val LocalAppWidgets = staticCompositionLocalOf<AppWidgetState> {
    error("AppWidgetStateProvider is required")
}

@Composable
internal fun AppWidgetStateProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val state = remember(context) { AppWidgetState(context) }
    val lifecycle = rememberWidgetLifecycle()
    DisposableEffect(state, lifecycle) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { state.refresh() }
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver(WidgetContract.URI, true, observer)
        for (key in listOf(WidgetContract.CONFIG, WidgetContract.ENABLED)) {
            resolver.registerContentObserver(Settings.Secure.getUriFor(key), false, observer)
        }
        val events = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> state.connect()
                Lifecycle.Event.ON_PAUSE -> state.disconnect()
                else -> Unit
            }
        }
        lifecycle.addObserver(events)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) state.connect()
        onDispose {
            resolver.unregisterContentObserver(observer)
            lifecycle.removeObserver(events)
            state.disconnect()
        }
    }
    CompositionLocalProvider(LocalAppWidgets provides state, content = content)
}

@Composable
internal fun rememberWidgetLifecycle(): Lifecycle {
    val context = LocalContext.current
    return remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<ComponentActivity>().first().lifecycle
    }
}
