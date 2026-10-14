/* SPDX-License-Identifier: Apache-2.0 */
package org.lunaris.lockstyles.settings.ui

import android.app.ActivityManager
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NetworkCell
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PortableWifiOff
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.lunaris.lockstyles.R
import org.lunaris.lockstyles.settings.Keys
import org.lunaris.lockstyles.settings.state

@Composable
fun DeviceInfoPreview() {
    val context = LocalContext.current
    val enabled by Keys.INFO_WIDGETS.state()
    val mode by Keys.WIDGETS_COLOR_MODE.state()
    val custom by Keys.WIDGETS_CUSTOM_COLOR.state()
    val opacity by Keys.WIDGETS_TRANSPARENCY.state()
    var values by remember { mutableStateOf(listOf(0, 0, 0, 0)) }
    val lifecycle = rememberWidgetLifecycle()
    LaunchedEffect(context, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val memory = ActivityManager.MemoryInfo().also {
                    context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
                }
                val audio = context.getSystemService(AudioManager::class.java)
                val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
                val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                values = listOf(level * 100 / scale.coerceAtLeast(1),
                    ((memory.totalMem - memory.availMem) * 100 / memory.totalMem.coerceAtLeast(1)).toInt(),
                    (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10,
                    audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 /
                        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1))
                delay(5000)
            }
        }
    }
    val tint = when (mode) {
        "custom" -> Color(custom)
        "accent" -> Color(context.getColor(android.R.color.system_accent1_100))
        else -> Color.White
    }
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
        .background(Color(0xFF252525)).padding(16.dp)) {
        Text(stringResource(R.string.lockscreen_display_widgets_title), color = Color.White)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)
            .alpha((opacity / 100f) * if (enabled) 1f else 0.4f),
            horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(Icons.Rounded.BatteryFull, Icons.Rounded.Memory, Icons.Rounded.Thermostat,
                Icons.Rounded.VolumeUp).forEachIndexed { index, icon ->
                Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(60.dp)) {
                        val stroke = 6.dp.toPx()
                        val start = Offset(stroke / 2, stroke / 2)
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        drawArc(tint.copy(alpha = 0.3f), 135f, 275f, false, start, arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round))
                        drawArc(tint, 135f, 275f * values[index].coerceIn(0, 100) / 100f,
                            false, start, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${values[index]}${if (index == 2) "℃" else "%"}", color = tint, fontSize = 13.sp)
                        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

private fun legacyIcon(value: String): ImageVector = when (value) {
    "calculator" -> Icons.Rounded.Calculate
    "media" -> Icons.Rounded.GraphicEq
    "timer" -> Icons.Rounded.Timer
    "torch" -> Icons.Rounded.FlashlightOn
    "weather" -> Icons.Rounded.Cloud
    "wifi" -> Icons.Rounded.Wifi
    "data" -> Icons.Rounded.NetworkCell
    "ringer" -> Icons.Rounded.Notifications
    "bt" -> Icons.Rounded.Bluetooth
    "hotspot" -> Icons.Rounded.PortableWifiOff
    "wallet" -> Icons.Rounded.Wallet
    "qrscanner" -> Icons.Rounded.QrCodeScanner
    else -> Icons.Rounded.GraphicEq
}

@Composable
fun LegacyWidgetPreview(slots: List<String>, enabled: Boolean) {
    val style by Keys.WIDGETS_STYLE.state()
    val opacity by Keys.WIDGETS_TRANSPARENCY.state()
    val labels = stringArrayResource(R.array.widget_values).zip(stringArrayResource(R.array.widget_entries)).toMap()
    val shape = if (style in listOf("1", "2", "4")) RoundedCornerShape(14.dp) else CircleShape
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
        .background(Color(0xFF252525)).padding(16.dp)
        .alpha((opacity / 100f) * if (enabled) 1f else 0.4f),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val main = slots.take(2).filter { it != "none" }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            main.forEach { value ->
                Row(Modifier.weight(1f).height(56.dp).clip(shape)
                    .background(Color.White.copy(alpha = if (style in listOf("4", "5")) 0.12f else 0.22f))
                    .padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(legacyIcon(value), null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text(labels[value].orEmpty(), color = Color.White, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            slots.drop(2).filter { it != "none" }.forEach { value ->
                Box(Modifier.size(48.dp).clip(shape).background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center) {
                    Icon(legacyIcon(value), labels[value], tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
        if (slots.all { it == "none" }) Text(stringResource(R.string.status_bar_date_none), color = Color.White)
    }
}
