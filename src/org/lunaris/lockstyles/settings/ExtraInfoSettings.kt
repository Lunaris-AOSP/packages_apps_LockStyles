/* SPDX-License-Identifier: Apache-2.0 */
package org.lunaris.lockstyles.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.json.JSONObject

data class ExtraInfoSettings(private val values: Map<String, String?> = emptyMap()) {
    fun <T> read(setting: LockSetting<T>): T = setting.decode(values[setting.key])

    fun <T> withValue(setting: LockSetting<T>, value: T): ExtraInfoSettings {
        require(contains(setting))
        val encoded = setting.encode(value)
        return copy(
            values =
                if (encoded == null) values - setting.key else values + (setting.key to encoded)
        )
    }

    fun toJson(): JSONObject =
        JSONObject().also { json ->
            SETTINGS.forEach { json.put(it.key, values[it.key] ?: JSONObject.NULL) }
        }

    fun apply(context: Context) {
        Keys.EXTRA_INFO_WIDGETS.write(context, false)
        SETTINGS.filter { it != Keys.EXTRA_INFO_WIDGETS }
            .forEach { it.putString(context, values[it.key]) }
        Keys.EXTRA_INFO_WIDGETS.write(context, read(Keys.EXTRA_INFO_WIDGETS))
    }

    companion object {
        private val SETTINGS =
            listOf(
                Keys.EXTRA_INFO_WIDGETS,
                Keys.EXTRA_INFO_LAYOUT,
                Keys.EXTRA_INFO_INDEPENDENT,
                Keys.EXTRA_INFO_FREEFORM,
                Keys.EXTRA_INFO_BLUR,
                Keys.EXTRA_INFO_BACKGROUND,
                Keys.EXTRA_INFO_GLASS,
                Keys.WIDGETS_COLOR_MODE,
                Keys.WIDGETS_CUSTOM_COLOR,
                Keys.WIDGETS_TRANSPARENCY,
            )

        fun contains(setting: LockSetting<*>): Boolean = setting in SETTINGS

        fun load(context: Context) =
            ExtraInfoSettings(
                SETTINGS.associate { it.key to it.getString(context) }.filterValues { it != null }
            )

        fun fromJson(json: JSONObject) =
            ExtraInfoSettings(
                SETTINGS.associate {
                        it.key to if (json.isNull(it.key)) null else json.optString(it.key)
                    }
                    .filterValues { it != null }
            )
    }
}

internal val LocalExtraInfoSettings = compositionLocalOf<ExtraInfoSettingsScope?> { null }

internal class ExtraInfoSettingsScope(
    private val current: MutableState<ExtraInfoSettings>,
    private val onChange: State<((ExtraInfoSettings) -> Unit)?>,
) {
    fun <T> state(setting: LockSetting<T>): MutableState<T> =
        object : MutableState<T> {
            override var value: T
                get() = current.value.read(setting)
                set(value) {
                    onChange.value?.let { change ->
                        val next = current.value.withValue(setting, value)
                        current.value = next
                        change(next)
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
}

@Composable
fun ExtraInfoSettingsProvider(
    settings: ExtraInfoSettings,
    onChange: ((ExtraInfoSettings) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val current = remember { mutableStateOf(settings) }
    current.value = settings
    val change = rememberUpdatedState(onChange)
    val scope = remember { ExtraInfoSettingsScope(current, change) }
    CompositionLocalProvider(LocalExtraInfoSettings provides scope, content = content)
}
