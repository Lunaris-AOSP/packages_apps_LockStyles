package org.lunaris.lockstyles.models

import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.lunaris.lockstyles.R
import org.lunaris.lockstyles.settings.ui.LockStylesTheme
import org.lunaris.lockstyles.settings.ui.SettingsPage
import java.io.File
import java.text.NumberFormat

class ModelActivity : ComponentActivity() {
    private var running by mutableStateOf(false)
    private var bytes by mutableStateOf(0L)
    private var hasFiles by mutableStateOf(false)
    private var error by mutableStateOf("")
    private var updates: Job? = null

    private fun refresh() {
        running = DepthModels.running
        bytes = if (running) DepthModels.downloadedBytes else DepthModels.installed(this)
        hasFiles = DepthModels.hasStoredFiles(this)
        error = DepthModels.status
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        enableEdgeToEdge()
        File(cacheDir, "spatial-midas-v1.tflite").delete()
        File(cacheDir, "spatial-model.tmp").delete()
        setContent {
            LockStylesTheme {
                val ready = !running && bytes == DepthModels.total
                val fraction = (bytes.toFloat() / DepthModels.total).coerceIn(0f, 1f)
                SettingsPage(stringResource(R.string.depth_wallpaper_models_title), { finish() }) {
                    item {
                        Surface(shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.fillMaxWidth().padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(when {
                                    running -> R.string.models_downloading
                                    ready -> R.string.models_ready
                                    else -> R.string.models_offline
                                }), style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.models_size,
                                    Formatter.formatShortFileSize(this@ModelActivity, bytes),
                                    Formatter.formatShortFileSize(this@ModelActivity, DepthModels.total),
                                    NumberFormat.getPercentInstance().format(fraction.toDouble())),
                                    style = MaterialTheme.typography.bodyMedium)
                                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                                if (!ready && !running) Button(onClick = {
                                    DepthModels.download(applicationContext)
                                    refresh()
                                }) { Text(stringResource(R.string.models_download)) }
                                if (hasFiles && !running) TextButton(onClick = {
                                    DepthModels.delete(applicationContext)
                                    refresh()
                                }) { Text(stringResource(R.string.models_delete)) }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        updates = lifecycleScope.launch {
            while (true) {
                refresh()
                delay(if (running) 250L else 1000L)
            }
        }
    }

    override fun onStop() {
        updates?.cancel()
        super.onStop()
    }
}
