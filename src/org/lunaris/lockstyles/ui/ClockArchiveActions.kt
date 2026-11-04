/*
 * SPDX-FileCopyrightText: Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles.ui

import android.app.KeyguardManager
import android.content.ClipData
import android.content.Intent
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.lunaris.clockmaker.ClockArchive
import org.lunaris.lockstyles.ClockMakerStore
import org.lunaris.lockstyles.ClockStyle
import org.lunaris.lockstyles.ClockStyleArchive
import org.lunaris.lockstyles.R

@Composable
internal fun ClockArchiveActions(clock: ClockStyle, store: ClockMakerStore, enabled: Boolean,
    onImported: (ClockStyle) -> Unit) {
    val context = LocalContext.current
    val archive = remember(context, store) { ClockStyleArchive(context, store) }
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var pendingExport by rememberSaveable { mutableStateOf<String?>(null) }
    fun canTransfer() = enabled && context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked != true
    fun message(id: Int) { Toast.makeText(context, id, Toast.LENGTH_LONG).show() }
    fun transfer(operation: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = operation
            } finally {
                busy = false
            }
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && canTransfer()) {
            transfer("import") {
                val imported = withContext(Dispatchers.IO) {
                    val worker = coroutineContext
                    archive.importClock(uri) { worker.ensureActive() }
                }
                if (canTransfer()) {
                    onImported(imported)
                    message(R.string.clock_maker_clock_imported)
                }
            }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ClockArchive.MIME_TYPE)) { uri ->
        val snapshot = pendingExport
        pendingExport = null
        if (uri != null) {
            if (snapshot == null || !canTransfer()) {
                scope.launch(Dispatchers.IO) {
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                }
            } else transfer("export") {
                try {
                    withContext(Dispatchers.IO) {
                        val worker = coroutineContext
                        archive.exportTo(uri, ClockStyle.fromJson(JSONObject(snapshot))) { worker.ensureActive() }
                    }
                    message(R.string.clock_maker_clock_exported)
                } catch (e: Exception) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                    }
                    throw e
                }
            }
        }
    }

    Box {
        IconButton(enabled = enabled && !busy, onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, stringResource(R.string.clock_maker_transfer))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.clock_maker_import_clock)) }, onClick = {
                expanded = false
                if (canTransfer()) runCatching { importer.launch(arrayOf("*/*")) }.onFailure { failure = "import" }
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.clock_maker_export_clock)) },
                enabled = clock.design?.elements?.isNotEmpty() == true, onClick = {
                    expanded = false
                    if (canTransfer()) {
                        pendingExport = clock.toJson().toString()
                        runCatching { exporter.launch(archive.fileName(clock)) }.onFailure {
                            pendingExport = null
                            failure = "export"
                        }
                    }
                })
            DropdownMenuItem(text = { Text(stringResource(R.string.clock_maker_share_clock)) },
                enabled = clock.design?.elements?.isNotEmpty() == true, onClick = {
                    expanded = false
                    if (canTransfer()) {
                        val snapshot = clock
                        transfer("share") {
                            var prepared: java.io.File? = null
                            var shared = false
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    val worker = coroutineContext
                                    archive.exportClock(snapshot) { worker.ensureActive() }.also { prepared = it }
                                }
                                if (canTransfer()) {
                                    val uri = FileProvider.getUriForFile(context,
                                        "${context.packageName}.clockexports", file, archive.fileName(snapshot))
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = ClockArchive.MIME_TYPE
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        clipData = ClipData.newRawUri(archive.fileName(snapshot), uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.clock_maker_share_clock)))
                                    shared = true
                                }
                            } finally {
                                if (!shared) withContext(NonCancellable + Dispatchers.IO) { prepared?.delete() }
                            }
                        }
                    }
                })
        }
    }
    if (busy) {
        AlertDialog(onDismissRequest = {}, confirmButton = {}, text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator()
                Text(stringResource(R.string.clock_maker_transferring))
            }
        })
    }
    failure?.let { operation ->
        val title = when (operation) {
            "import" -> R.string.clock_maker_clock_import_error
            "export" -> R.string.clock_maker_clock_export_error
            else -> R.string.clock_maker_clock_share_error
        }
        val summary = when (operation) {
            "import" -> R.string.clock_maker_clock_import_error_summary
            "export" -> R.string.clock_maker_clock_export_error_summary
            else -> R.string.clock_maker_clock_share_error_summary
        }
        AlertDialog(onDismissRequest = { failure = null }, title = { Text(stringResource(title)) },
            text = { Text(stringResource(summary)) }, confirmButton = {
                TextButton(onClick = { failure = null }) { Text(stringResource(android.R.string.ok)) }
            })
    }
}
