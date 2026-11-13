/*
 * SPDX-FileCopyrightText: 2026 Lunaris-AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles.settings.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The lock screen settings that used to be Singularity's Lock screen page. Singularity's entry
 * opens this, with [EXTRA_PAGE] naming a sub-page to start on.
 */
class LockSettingsActivity : ComponentActivity() {

    private var start by mutableStateOf(Page.MAIN)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        start = pageOf(intent)
        setContent {
            LockStylesTheme {
                LockSettings(start) { finish() }
            }
        }
    }

    // Already open, a new page arrives here rather than in onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        start = pageOf(intent)
    }

    private fun pageOf(intent: Intent): Page =
        intent.getStringExtra(EXTRA_PAGE)
            ?.let { name -> Page.entries.firstOrNull { it.name.equals(name, true) } }
            ?: Page.MAIN

    companion object {
        const val ACTION = "org.lunaris.lockstyles.action.LOCK_SCREEN_SETTINGS"
        const val EXTRA_PAGE = "page"
    }
}
