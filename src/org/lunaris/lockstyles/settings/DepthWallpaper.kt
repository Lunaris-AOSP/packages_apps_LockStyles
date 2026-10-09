/*
 * SPDX-FileCopyrightText: 2026 Lunaris-AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/** The extraction service remains in Singularity; both LockStyle entry points use its API. */
object DepthWallpaper {
    private val SERVICE = ComponentName("com.android.settings",
        "org.lunaris.settings.utils.WallpaperSubjectExtractorService")
    private const val ACTION = "org.lunaris.intent.action.EXTRACT_DEPTH_SUBJECT_NOW"

    fun extractNow(context: Context): Boolean = runCatching {
        context.startService(Intent(ACTION).setComponent(SERVICE)) != null
    }.onFailure { Log.w("DepthWallpaper", "Could not start the subject extractor", it) }
        .getOrDefault(false)

    fun clearSubject(context: Context) {
        Keys.DEPTH_AUTO_SUBJECT.write(context, false)
        Keys.DEPTH_SUBJECT.write(context, null)
    }
}
