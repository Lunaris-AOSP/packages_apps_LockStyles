/*
 * SPDX-FileCopyrightText: 2026 Lunaris-AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lunaris.lockstyles

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.util.Log

/**
 * The lock screen's bottom shortcuts, read and written through SystemUI's CustomizationProvider,
 * as ThemePicker's KeyguardQuickAffordancePickerInteractor does.
 */
class Shortcuts(private val context: Context) {

    data class Slot(val id: String)
    data class Affordance(val id: String, val name: String, val iconResourceId: Int, val isEnabled: Boolean)
    data class Selection(val slotId: String, val affordanceId: String)

    private val resolver get() = context.contentResolver

    fun slots(): List<Slot> = query(SLOTS) { Slot(it.string("id")) }

    fun affordances(): List<Affordance> =
        query(AFFORDANCES) {
            Affordance(it.string("id"), it.string("name"), it.int("icon"), it.int("is_enabled") != 0)
        }

    fun selections(): List<Selection> =
        query(SELECTIONS) { Selection(it.string("slot_id"), it.string("affordance_id")) }

    fun select(slotId: String, affordanceId: String) {
        // One shortcut per slot, as the picker does.
        unselectAll(slotId)
        runCatching {
            resolver.insert(SELECTIONS, ContentValues().apply {
                put("slot_id", slotId)
                put("affordance_id", affordanceId)
            })
        }.onFailure { Log.w(TAG, "Could not select $affordanceId", it) }
    }

    fun unselectAll(slotId: String) {
        runCatching { resolver.delete(SELECTIONS, null, arrayOf(slotId)) }
            .onFailure { Log.w(TAG, "Could not clear $slotId", it) }
    }

    private class Row(private val cursor: android.database.Cursor) {
        fun string(column: String): String =
            cursor.getColumnIndex(column).takeIf { it >= 0 }?.let(cursor::getString) ?: ""
        fun int(column: String): Int =
            cursor.getColumnIndex(column).takeIf { it >= 0 }?.let(cursor::getInt) ?: 0
    }

    private fun <T> query(uri: Uri, map: (Row) -> T): List<T> =
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { c ->
                val row = Row(c)
                buildList { while (c.moveToNext()) add(map(row)) }
            } ?: emptyList()
        }.onFailure { Log.w(TAG, "Could not read $uri", it) }.getOrDefault(emptyList())

    companion object {
        private const val TAG = "Shortcuts"
        private val BASE: Uri =
            Uri.parse("content://com.android.systemui.customization/lockscreen_quickaffordance")
        private val SLOTS: Uri = BASE.buildUpon().appendPath("slots").build()
        private val AFFORDANCES: Uri = BASE.buildUpon().appendPath("affordances").build()
        private val SELECTIONS: Uri = BASE.buildUpon().appendPath("selections").build()
    }
}
