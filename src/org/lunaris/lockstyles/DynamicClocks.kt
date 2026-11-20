/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.lockstyles

import android.content.Context
import android.provider.Settings
import org.json.JSONObject
import org.lunaris.clockmaker.ClockLayoutProfile
import org.lunaris.clockmaker.ClockDesign
import org.lunaris.clockmaker.DynamicClockSettings
import java.util.UUID

data class DynamicClockConfiguration(
    val enabled: Boolean = false,
    val small: ClockStyle,
    val large: ClockStyle,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("enabled", enabled)
        .put(DynamicClockSettings.SMALL, small.toJson())
        .put(DynamicClockSettings.LARGE, large.toJson())

    companion object {
        fun fromJson(json: JSONObject): DynamicClockConfiguration? = runCatching {
            val small = json.getJSONObject(DynamicClockSettings.SMALL)
            val large = json.getJSONObject(DynamicClockSettings.LARGE)
            require(ClockLayoutProfile.parse(small.toString(), true) != null)
            require(ClockLayoutProfile.parse(large.toString(), false) != null)
            DynamicClockConfiguration(json.optBoolean("enabled"),
                ClockStyle.fromJson(small).copy(small = true),
                ClockStyle.fromJson(large).copy(small = false))
        }.getOrNull()
    }
}

object DynamicClocks {
    fun defaults(clock: ClockStyle): DynamicClockConfiguration {
        val tuning = clock.tuning ?: CustomClocks.Tuning()
        fun layout(small: Boolean) = clock.copy(
            small = small,
            tuning = if (small) tuning.copy(marginTop = 0,
                scale = if (clock.face == 0) tuning.scale else (tuning.scale * 0.6f).toInt().coerceIn(50, 150))
                else tuning,
            design = (clock.design ?: if (clock.face == ClockDesign.STYLE) ClockDesign() else null)
                ?.copy(id = UUID.randomUUID().toString()),
            showOnHomeScreen = false, homeLayout = null,
            stockAxes = clock.stockAxes ?: if (clock.face == 0) mapOf(
                "wght" to 500f, "wdth" to 100f, "ROND" to if (clock.rounded) 100f else 0f, "slnt" to 0f) else null,
        )
        return DynamicClockConfiguration(small = layout(true), large = layout(false))
    }

    @Synchronized
    fun load(context: Context): DynamicClockConfiguration {
        val resolver = context.contentResolver
        val saved = runCatching {
            Settings.Secure.getString(resolver, DynamicClockSettings.LAYOUTS)
                ?.let(DynamicClockSettings::decode)?.let(DynamicClockConfiguration::fromJson)
        }.getOrNull()
        val defaults = saved ?: defaults(CustomClocks.appliedStyle(context, ClockStyle()))
        return defaults.copy(enabled = Settings.Secure.getInt(resolver, DynamicClockSettings.ENABLED, 0) != 0)
    }

    @Synchronized
    fun apply(context: Context, configuration: DynamicClockConfiguration) {
        val resolver = context.contentResolver
        check(Settings.Secure.putString(resolver, DynamicClockSettings.LAYOUTS, DynamicClockSettings.encode(configuration.toJson())))
        check(Settings.Secure.putInt(resolver, DynamicClockSettings.ENABLED, if (configuration.enabled) 1 else 0))
    }

    @Synchronized
    fun update(context: Context, transform: (DynamicClockConfiguration) -> DynamicClockConfiguration): DynamicClockConfiguration {
        return transform(load(context)).also { apply(context, it) }
    }
}
