/*
 * SPDX-FileCopyrightText: 2026 Lunaris-AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles.settings

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.lunaris.lockstyles.GalleryStore
import org.lunaris.lockstyles.DynamicClocks

/** Which settings table a key lives in. */
enum class Table { SYSTEM, SECURE }

/**
 * One lock screen setting, as Singularity's SystemSetting / SecureSetting preferences stored it:
 * booleans as 0 and 1, lists as strings or ints.
 */
sealed class LockSetting<T>(val table: Table, val key: String, val default: T) {

    internal abstract fun decode(value: String?): T
    internal abstract fun encode(value: T): String?

    fun read(context: Context): T = decode(getString(context))
    fun write(context: Context, value: T) = putString(context, encode(value))

    fun uri(): Uri = when (table) {
        Table.SYSTEM -> Settings.System.getUriFor(key)
        Table.SECURE -> Settings.Secure.getUriFor(key)
    }

    internal fun getString(context: Context): String? = runCatching {
        when (table) {
            Table.SYSTEM -> Settings.System.getString(context.contentResolver, key)
            Table.SECURE -> Settings.Secure.getString(context.contentResolver, key)
        }
    }.getOrNull()

    internal fun putString(context: Context, value: String?) {
        when (table) {
            Table.SYSTEM -> Settings.System.putString(context.contentResolver, key, value)
            Table.SECURE -> Settings.Secure.putString(context.contentResolver, key, value)
        }
    }

    class Bool(table: Table, key: String, default: Boolean) :
        LockSetting<Boolean>(table, key, default) {
        override fun decode(value: String?) = value?.toIntOrNull()?.let { it != 0 } ?: default
        override fun encode(value: Boolean) = if (value) "1" else "0"
    }

    class Num(table: Table, key: String, default: Int) : LockSetting<Int>(table, key, default) {
        override fun decode(value: String?) = value?.toIntOrNull() ?: default
        override fun encode(value: Int) = value.toString()
    }

    class Text(table: Table, key: String, default: String?) :
        LockSetting<String?>(table, key, default) {
        override fun decode(value: String?) = value ?: default
        override fun encode(value: String?) = value
    }
}

/** The setting as Compose state, following changes made anywhere else too. */
@Composable
fun <T> LockSetting<T>.state(): MutableState<T> {
    val profile = LocalExtraInfoSettings.current
    if (profile != null && ExtraInfoSettings.contains(this)) {
        return remember(this, profile) { profile.state(this) }
    }
    val context = LocalContext.current
    val state = remember(key) {
        object : MutableState<T> {
            val inner = mutableStateOf(read(context))
            override var value: T
                get() = inner.value
                set(v) {
                    inner.value = v
                    if (this@state === Keys.DYNAMIC_CLOCK) {
                        val configuration = DynamicClocks.update(context) { it.copy(enabled = v == true) }
                        GalleryStore.get(context).saveCurrentDynamicClock(configuration)
                    } else write(context, v)
                    if (ExtraInfoSettings.contains(this@state)) {
                        GalleryStore.get(context).saveCurrentExtraInfo()
                    }
                }
            override fun component1() = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
    DisposableEffect(key) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                state.inner.value = read(context)
            }
        }
        context.contentResolver.registerContentObserver(uri(), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return state
}
