/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lunaris.lockstyles

import android.content.Context
import android.graphics.fonts.Font
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import org.lunaris.clockmaker.ClockFonts
import java.io.File
import java.util.UUID

class ClockMakerStore(context: Context) {
    private val storage = context.createDeviceProtectedStorageContext()
    private val prefs = storage.getSharedPreferences("clock_maker", Context.MODE_PRIVATE)
    private val resolver = context.contentResolver
    private val fontDir = File(storage.filesDir, "clock_fonts")

    data class ImportedFont(val id: String, val name: String) {
        val family: String get() = "import:$id"
    }

    val designs: List<ClockStyle>
        get() = readArray("designs").let { a ->
            (0 until a.length()).mapNotNull {
                runCatching { ClockStyle.fromJson(a.getJSONObject(it)) }.getOrNull()?.takeIf { it.design != null }
            }
        }

    val fonts: List<ImportedFont>
        get() = readArray("fonts").let { a ->
            (0 until a.length()).mapNotNull { i ->
                a.optJSONObject(i)?.let { j ->
                    ImportedFont(j.optString("id"), j.optString("name")).takeIf { file(it.id)?.isFile == true }
                }
            }
        }

    private fun readArray(key: String): JSONArray =
        runCatching { JSONArray(prefs.getString(key, "[]")) }.getOrDefault(JSONArray())

    fun save(clock: ClockStyle) {
        val all = designs.filter { it.design?.id != clock.design?.id } + clock
        prefs.edit().putString("designs", JSONArray().also { a -> all.forEach { a.put(it.toJson()) } }.toString()).apply()
    }

    fun deleteDesign(id: String) {
        prefs.edit().putString("designs", JSONArray().also { a ->
            designs.filter { it.design?.id != id }.forEach { a.put(it.toJson()) }
        }.toString()).apply()
    }

    fun draft(key: String): ClockStyle? = prefs.getString("draft_$key", null)?.let {
        runCatching { ClockStyle.fromJson(JSONObject(it)) }.getOrNull()
    }
    fun saveDraft(key: String, clock: ClockStyle) {
        prefs.edit().putString("draft_$key", clock.toJson().toString()).apply()
    }
    fun clearDraft(key: String) { prefs.edit().remove("draft_$key").apply() }

    fun file(id: String): File? = if (id.matches(Regex("[a-f0-9-]{36}\\.(ttf|otf)"))) File(fontDir, id) else null

    fun importFont(uri: Uri): ImportedFont {
        require(fonts.size < 32)
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Font.ttf"
        val extension = displayName.substringAfterLast('.', "").lowercase()
        require(extension == "ttf" || extension == "otf")
        check(fontDir.isDirectory || fontDir.mkdirs())
        val id = "${UUID.randomUUID()}.$extension"
        val target = file(id)!!
        val pending = File(fontDir, "$id.pending")
        try {
            resolver.openInputStream(uri)!!.use { input ->
                pending.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 10 * 1024 * 1024)
                        output.write(buffer, 0, count)
                    }
                    require(total >= 12)
                }
            }
            val header = pending.inputStream().use { input -> ByteArray(4).also { check(input.read(it) == 4) } }
            require(header.contentEquals(byteArrayOf(0, 1, 0, 0)) || header.contentEquals("OTTO".toByteArray()))
            Font.Builder(pending).build()
            check(pending.renameTo(target))
            val imported = ImportedFont(id, displayName.substringBeforeLast('.').take(60))
            val all = fonts + imported
            check(prefs.edit().putString("fonts", JSONArray().also { a ->
                all.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name)) }
            }.toString()).commit())
            ClockFonts.clear()
            resolver.notifyChange(ClockFonts.uri, null)
            return imported
        } catch (e: Exception) {
            pending.delete()
            target.delete()
            throw e
        }
    }

    fun removeFont(font: ImportedFont) {
        check(file(font.id)?.let { !it.exists() || it.delete() } != false)
        prefs.edit().putString("fonts", JSONArray().also { a ->
            fonts.filter { it.id != font.id }.forEach {
                a.put(JSONObject().put("id", it.id).put("name", it.name))
            }
        }.toString()).commit()
        ClockFonts.clear()
        resolver.notifyChange(ClockFonts.uri, null)
    }
}
