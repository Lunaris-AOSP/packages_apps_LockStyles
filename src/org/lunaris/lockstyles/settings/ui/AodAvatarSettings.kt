package org.lunaris.lockstyles.settings.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lunaris.lockstyles.R
import org.lunaris.lockstyles.settings.AodAvatar
import org.lunaris.lockstyles.settings.Keys
import org.lunaris.lockstyles.settings.state
import org.lunaris.lockstyles.ui.ExpressiveLoadingIndicator

@Composable
fun AodAvatarSettings(allowPhotoPicking: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val path by Keys.AOD_IMAGE_URI.state()
    var saving by remember { mutableStateOf(false) }
    val preview by produceState<Bitmap?>(null, path) {
        value = null
        value = withContext(Dispatchers.IO) {
            path?.let { runCatching { AodAvatar.decode(context, it, 256) }.getOrNull() }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (allowPhotoPicking && uri != null) {
            saving = true
            scope.launch {
                try {
                    val saved = withContext(Dispatchers.IO) { AodAvatar.save(context, uri) }
                    if (saved == null) Toast.makeText(context, R.string.aod_avatar_save_failed,
                        Toast.LENGTH_LONG).show()
                } finally {
                    saving = false
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Group(null, listOf(
            switchRow(stringResource(R.string.lockscreen_custom_image_enabled_title),
                stringResource(R.string.lockscreen_custom_image_summary), Keys.AOD_IMAGE,
                enabled = !saving),
            actionRow(stringResource(R.string.lockscreen_custom_image_title),
                stringResource(if (path.isNullOrEmpty()) R.string.lockscreen_custom_image_pick_summary
                    else R.string.aod_avatar_selected), enabled = !saving && allowPhotoPicking) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            actionRow(stringResource(R.string.aod_avatar_remove), null,
                enabled = !saving && !path.isNullOrEmpty()) {
                saving = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { AodAvatar.clear(context) }
                    } finally {
                        saving = false
                    }
                }
            },
        ))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (saving) ExpressiveLoadingIndicator()
            else preview?.let {
                Image(it.asImageBitmap(), stringResource(R.string.aod_avatar_preview),
                    Modifier.size(120.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            }
        }
        Text(stringResource(R.string.aod_avatar_summary))
    }
}
