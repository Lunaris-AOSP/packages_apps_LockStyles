/*
 * SPDX-FileCopyrightText: 2026 Lunaris-AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles

import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

/**
 * SystemUI's own flex clock, written straight to the settings its ClockRegistry reads, in place
 * of ThemePicker's ClockPickerInteractor.
 */
object StockClock {

    private const val KEY_CLOCK_FACE = "lock_screen_custom_clock_face"
    private const val KEY_DOUBLE_LINE = "lockscreen_use_double_line_clock"
    private const val DEFAULT_CLOCK_ID = "DEFAULT"

    val PRESETS = listOf(800f to 30f, 700f to 55f, 600f to 80f, 500f to 100f, 400f to 108f,
        300f to 116f, 200f to 120f)

    fun load(context: Context): ClockStyle {
        val resolver = context.contentResolver
        val json = runCatching {
            JSONObject(Settings.Secure.getString(resolver, KEY_CLOCK_FACE) ?: "{}")
        }.getOrDefault(JSONObject())
        val axes = buildMap<String, Float> {
            val array = json.optJSONArray("axes") ?: return@buildMap
            for (index in 0 until array.length()) {
                val axis = array.optJSONObject(index) ?: continue
                val key = axis.optString("key")
                val value = axis.optDouble("value").toFloat()
                if (key.isNotEmpty() && value.isFinite()) put(key, value)
            }
        }
        return ClockStyle(
            preset = PRESETS.indexOfFirst { (weight, width) ->
                (axes["wght"] ?: 500f) == weight && (axes["wdth"] ?: 100f) == width
            }.takeIf { it >= 0 },
            rounded = (axes["ROND"] ?: 100f) >= 50f,
            color = if (json.isNull("seedColor")) null else json.optInt("seedColor"),
            small = Settings.Secure.getInt(resolver, KEY_DOUBLE_LINE, 1) == 0,
            stockAxes = axes,
        )
    }

    /** [axes] are the flex clock's font axes, e.g. wght and wdth; null keeps the current ones. */
    fun apply(context: Context, small: Boolean, seedColor: Int?, axes: Map<String, Float>?) {
        val resolver = context.contentResolver
        val current = runCatching {
            JSONObject(Settings.Secure.getString(resolver, KEY_CLOCK_FACE) ?: "{}")
        }.getOrDefault(JSONObject())
        val json = JSONObject()
            .put("clockId", DEFAULT_CLOCK_ID)
            .put("seedColor", seedColor ?: JSONObject.NULL)
            .put("metadata", current.optJSONObject("metadata") ?: JSONObject())
            .put("axes", axes?.let { map ->
                JSONArray().also { array ->
                    map.forEach { (key, value) ->
                        array.put(JSONObject().put("key", key).put("value", value.toDouble()))
                    }
                }
            } ?: current.optJSONArray("axes") ?: JSONArray())
        Settings.Secure.putString(resolver, KEY_CLOCK_FACE, json.toString())
        Settings.Secure.putInt(resolver, KEY_DOUBLE_LINE, if (small) 0 else 1)
    }
}
