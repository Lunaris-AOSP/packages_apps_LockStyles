/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lunaris.lockstyles.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lunaris.clockmaker.DynamicClockSettings
import org.lunaris.lockstyles.DynamicClockConfiguration
import org.lunaris.lockstyles.DynamicClocks
import org.lunaris.lockstyles.GalleryStore
import org.lunaris.lockstyles.GalleryWallpaper
import org.lunaris.lockstyles.R
import org.lunaris.lockstyles.settings.ui.LockStylesTheme

class DynamicClockActivity : ComponentActivity() {
    private var initial by mutableStateOf<DynamicClockConfiguration?>(null)
    private var wallpaper by mutableStateOf(GalleryWallpaper(GalleryWallpaper.Kind.COLOUR))
    private var size by mutableStateOf<String?>(null)
    private var request by mutableStateOf(0)
    private var saving by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        request = savedInstanceState?.getInt("dynamic_clock_request") ?: 0
        readRequest(intent)
        setContent {
            LockStylesTheme {
                initial?.let { configuration ->
                    DynamicClockScreen(configuration, wallpaper, "active_dynamic_clock:$request",
                        allowImport = true, onCancel = { finish() },
                        onSave = { save(it) }, initialSize = size)
                }
                if (saving) Dialog(onDismissRequest = {}, properties = DialogProperties(
                    dismissOnBackPress = false, dismissOnClickOutside = false)) {
                    Surface(shape = MaterialTheme.shapes.extraLarge) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(R.string.gallery_saving))
                            LinearProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!saving) {
            request++
            readRequest(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("dynamic_clock_request", request)
        super.onSaveInstanceState(outState)
    }

    private fun readRequest(intent: Intent) {
        initial = DynamicClocks.load(this)
        size = intent.getStringExtra(DynamicClockSettings.EXTRA_SIZE)
            ?.takeIf { it == DynamicClockSettings.SMALL || it == DynamicClockSettings.LARGE }
        val store = GalleryStore.get(this)
        wallpaper = store.lockScreens.firstOrNull { it.id == store.currentId }?.wallpaper
            ?: GalleryWallpaper(GalleryWallpaper.Kind.COLOUR)
    }

    private fun save(configuration: DynamicClockConfiguration) {
        if (saving) return
        saving = true
        val editingSize = size
        lifecycleScope.launch {
            val result = withContext(NonCancellable + Dispatchers.IO) {
                runCatching {
                    val saved = DynamicClocks.update(this@DynamicClockActivity) { current ->
                        when (editingSize) {
                            DynamicClockSettings.SMALL -> current.copy(small = configuration.small)
                            DynamicClockSettings.LARGE -> current.copy(large = configuration.large)
                            else -> configuration
                        }
                    }
                    GalleryStore.get(this@DynamicClockActivity).saveCurrentDynamicClock(saved)
                }
            }
            saving = false
            if (result.isSuccess) finish() else Toast.makeText(this@DynamicClockActivity,
                R.string.dynamic_clock_save_failed, Toast.LENGTH_LONG).show()
        }
    }
}
