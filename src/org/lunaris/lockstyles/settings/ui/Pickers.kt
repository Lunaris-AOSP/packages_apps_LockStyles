/*
 * SPDX-FileCopyrightText: 2016-2026 crDroid Android Project
 * SPDX-FileCopyrightText: 2023-2024 The risingOS Android Project
 * SPDX-FileCopyrightText: 2024-2026 Lunaris AOSP
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles.settings.ui

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.lunaris.lockstyles.settings.ui.components.ModernSettingsCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lunaris.lockstyles.CustomClocks
import org.lunaris.lockstyles.R
import org.lunaris.lockstyles.settings.Keys
import org.lunaris.lockstyles.settings.LockSetting
import org.lunaris.lockstyles.settings.Lunaris
import org.lunaris.lockstyles.settings.state



private const val FONT_CATEGORY = "android.theme.customization.lockscreen_clock_font"

/** Singularity's LockClockFontsPreference: the clock font overlays, previewed in their font. */
@Composable
fun ClockFontPicker() {
    val context = LocalContext.current
    val askRestart = LocalAskRestart.current
    var version by remember { mutableStateOf(0) }
    val fonts by produceState(emptyList<Pair<String, Typeface?>>(), version) {
        value = withContext(Dispatchers.IO) {
            listOf("android" to fontOf(context, "android")) +
                Lunaris.overlays(context, FONT_CATEGORY).map {
                    it.packageName to fontOf(context, it.packageName)
                }
        }
    }
    val applied by produceState("android", version) {
        value = withContext(Dispatchers.IO) {
            Lunaris.overlays(context, FONT_CATEGORY).firstOrNull { it.isEnabled }?.packageName
                ?: "android"
        }
    }
    if (fonts.size <= 1) return
    ModernSettingsCard(title = stringResource(R.string.theme_customization_lock_clock_title),
        icon = Icons.Rounded.TextFields) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(fonts) { _, (pkg, typeface) ->
            val on = pkg == applied
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(88.dp).clickable {
                    if (on) return@clickable
                    // Same order as Singularity: switch the old one off, then the new one on.
                    Lunaris.setOverlay(context, FONT_CATEGORY, applied, applied)
                    if (pkg != "android") Lunaris.setOverlay(context, FONT_CATEGORY, pkg, "android")
                    version++
                    askRestart()
                }) {
                Box(
                    Modifier.size(88.dp).pickTile(on),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("12", color = MaterialTheme.colorScheme.onSurface, fontSize = 34.sp,
                        fontFamily = typeface?.let { FontFamily(it) })
                }
                Text(if (pkg == "android") stringResource(R.string.clock_font_default)
                    else labelOf(context, pkg), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp,
                    maxLines = 1, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
}

private fun fontOf(context: Context, pkg: String): Typeface? = runCatching {
    val res = if (pkg == "android") Resources.getSystem()
        else context.packageManager.getResourcesForApplication(pkg)
    val id = res.getIdentifier("config_clockFontFamily", "string", pkg)
    if (id == 0) null else Typeface.create(res.getString(id), Typeface.NORMAL)
}.getOrNull()

private fun labelOf(context: Context, pkg: String): String = runCatching {
    val pm = context.packageManager
    pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
}.getOrDefault(pkg)

/** The info widgets' colours, from rising_settings_lockscreen_widgets. */
@Composable
fun WidgetColorGroup(modes: List<Pair<String?, String>>, enabled: Boolean) {
    Group(null, listOf(
        choiceRow(stringResource(R.string.lockscreen_widgets_color_mode_title),
            Keys.WIDGETS_COLOR_MODE, modes, enabled = enabled),
        colorRow(stringResource(R.string.custom_color), Keys.WIDGETS_CUSTOM_COLOR,
            enabled = enabled),
    ))
}

/**
 * Singularity's LockScreenWidgets: two main and four extra widgets, applied together as the
 * comma separated lockscreen_widgets and lockscreen_widgets_extras SystemUI reads.
 */
@Composable
fun WidgetSlots(enabled: Boolean) {
    val context = LocalContext.current
    val labels = stringArrayResource(R.array.widget_entries).toList()
    val values = stringArrayResource(R.array.widget_values).toList()
    val styles = stringArrayResource(R.array.lockscreen_widgets_style_values).toList()
        .zip(stringArrayResource(R.array.lockscreen_widgets_style_entries).toList())
    val slotTitles = listOf(R.string.main_custom_widgets1, R.string.main_custom_widgets2,
        R.string.custom_widgets1, R.string.custom_widgets2, R.string.custom_widgets3,
        R.string.custom_widgets4).map { stringResource(it) }

    fun split(value: String?, count: Int) =
        (value?.split(",")?.map { it.trim() } ?: emptyList()).let { list ->
            List(count) { list.getOrNull(it)?.takeIf { v -> v.isNotEmpty() } ?: "none" }
        }
    val saved = remember {
        mutableStateListOf<String>().apply {
            addAll(split(Keys.WIDGETS_MAIN.read(context), 2) +
                split(Keys.WIDGETS_EXTRAS.read(context), 4))
        }
    }
    val slots = remember { mutableStateListOf<String>().apply { addAll(saved) } }
    val changed = slots.toList() != saved.toList()

    Group(null, buildList {
        add(choiceRow(stringResource(R.string.lockscreen_widgets_style_title),
            Keys.WIDGETS_STYLE, styles, enabled = enabled))
        add(sliderRow(stringResource(R.string.lockscreen_widgets_transparency_title),
            stringResource(R.string.lockscreen_widgets_transparency_summary),
            Keys.WIDGETS_TRANSPARENCY, 0..100, unit = "%", enabled = enabled))
        slotTitles.forEachIndexed { i, title ->
            add(localChoiceRow(title, slots[i], values.zip(labels),
                // The first main widget works without the rest, as in Singularity.
                enabled = enabled || i == 0) { slots[i] = it })
        }
    })
    Button(
        onClick = {
            Keys.WIDGETS_MAIN.write(context, slots.subList(0, 2).joinToString(","))
            Keys.WIDGETS_EXTRAS.write(context, slots.subList(2, 6).joinToString(","))
            saved.clear()
            saved.addAll(slots)
        },
        enabled = changed,
        colors = ButtonDefaults.buttonColors(containerColor = Accent,
            contentColor = Color(0xFF1B1340)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            .height(52.dp),
    ) { Text(stringResource(R.string.apply_change), fontSize = 16.sp) }
}

/** The drawables Lunaris ships for the under-display fingerprint sensor. */
enum class UdfpsPacks(val pkg: String, val names: String, val previews: String?,
        val titles: String?) {
    ICONS("com.lunaris.udfps.icons", "udfps_icons", null, null),
    ANIMATIONS("com.lunaris.udfps.animations", "udfps_animation_styles",
        "udfps_animation_previews", "udfps_animation_titles");

    class Item(val index: Int, val preview: Drawable?, val title: String?)

    fun load(context: Context): List<Item> = runCatching {
        val res = context.packageManager.getResourcesForApplication(pkg)
        fun array(name: String?) = name?.let {
            res.getIdentifier(it, "array", pkg).takeIf { id -> id != 0 }?.let(res::getStringArray)
        }
        val names = array(names) ?: return emptyList()
        val previews = array(previews) ?: names
        val titles = array(titles)
        List(minOf(names.size, previews.size)) { i ->
            val id = res.getIdentifier(previews[i], "drawable", pkg)
            Item(i, if (id != 0) res.getDrawable(id, null) else null, titles?.getOrNull(i))
        }
    }.getOrDefault(emptyList())
}

@Composable
fun UdfpsResourcePicker(title: String, pack: UdfpsPacks, setting: LockSetting<Int>,
        enabled: Boolean) {
    val context = LocalContext.current
    var selected by setting.state()
    val items by produceState(emptyList<UdfpsPacks.Item>(), pack) {
        value = withContext(Dispatchers.IO) { pack.load(context) }
    }
    if (items.isEmpty()) return
    val px = with(LocalDensity.current) { 64.dp.roundToPx() }
    ModernSettingsCard(title = title, icon = Icons.Rounded.Fingerprint) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.alpha(if (enabled) 1f else 0.4f)) {
        itemsIndexed(items) { _, item ->
            val on = item.index == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(88.dp).clickable(enabled = enabled) {
                    selected = item.index
                }) {
                Box(
                    Modifier.size(88.dp).pickTile(on),
                    contentAlignment = Alignment.Center,
                ) {
                    val bitmap = remember(item) {
                        item.preview?.let { d ->
                            Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also {
                                d.setBounds(0, 0, px, px)
                                d.draw(android.graphics.Canvas(it))
                            }
                        }
                    }
                    bitmap?.let { Image(it.asImageBitmap(), item.title, Modifier.size(64.dp)) }
                }
                item.title?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, maxLines = 1,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
}
