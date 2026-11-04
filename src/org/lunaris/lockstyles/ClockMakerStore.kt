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
import org.lunaris.clockmaker.ClockArchive
import org.lunaris.clockmaker.ClockFonts
import java.io.File
import java.io.InputStream
import java.util.UUID

class ClockMakerStore(private val context: Context) {
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
                    ImportedFont(j.optString("id"), j.optString("name").trim()).takeIf {
                        it.name.isNotEmpty() && file(it.id)?.isFile == true
                    }
                }
            }.distinctBy { it.id }
        }

    private fun readArray(key: String): JSONArray =
        runCatching { JSONArray(prefs.getString(key, "[]")) }.getOrDefault(JSONArray())

    fun save(clock: ClockStyle) = synchronized(assetLock) {
        val all = designs.filter { it.design?.id != clock.design?.id } + clock
        prefs.edit().putString("designs", JSONArray().also { a -> all.forEach { a.put(it.toJson()) } }.toString()).apply()
    }

    fun deleteDesign(id: String) = synchronized(assetLock) {
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

    fun importFont(uri: Uri): ImportedFont = synchronized(assetLock) {
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

    fun removeFont(font: ImportedFont) = synchronized(assetLock) {
        check(file(font.id)?.let { !it.exists() || it.delete() } != false)
        prefs.edit().putString("fonts", JSONArray().also { a ->
            fonts.filter { it.id != font.id }.forEach {
                a.put(JSONObject().put("id", it.id).put("name", it.name))
            }
        }.toString()).commit()
        ClockFonts.clear()
        resolver.notifyChange(ClockFonts.uri, null)
    }

    internal fun installArchive(contents: ClockArchive.Contents, directory: File,
        checkActive: () -> Unit = {}): ClockStyle = synchronized(assetLock) {
        checkActive()
        val parsed = ClockStyle.fromJson(contents.clock)
        val tuning = requireNotNull(parsed.tuning)
        val source = parsed.copy(tuning = tuning.copy(
            scale = tuning.scale.coerceIn(50, 150), opacity = tuning.opacity.coerceIn(0, 100),
            marginTop = tuning.marginTop.coerceIn(0, 200), marginStart = tuning.marginStart.coerceIn(-200, 200),
            rotation = tuning.rotation.coerceIn(-180, 180), tiltX = tuning.tiltX.coerceIn(-75, 75),
            tiltY = tuning.tiltY.coerceIn(-75, 75), gradientAnchorY = tuning.gradientAnchorY.coerceIn(0, 100),
            gradientRadius = tuning.gradientRadius.coerceIn(25, 200)))
        val design = requireNotNull(source.design)
        val imageStore = ClockImageStore(context)
        val fontAssets = contents.assets.filter { it.kind == ClockArchive.Kind.FONT }
        val imageAssets = contents.assets.filter { it.kind == ClockArchive.Kind.IMAGE }
        fun staged(asset: ClockArchive.Asset) = File(directory, asset.path.substringAfter('/'))
        val existingFonts = fonts
        val newFonts = mutableListOf<ImportedFont>()
        val byDigest by lazy {
            existingFonts.associateBy { font ->
                requireNotNull(file(font.id)).inputStream().use { digest(it, checkActive) }
            }.toMutableMap()
        }
        val fontMap = mutableMapOf<String, String>()
        val imageMap = mutableMapOf<String, String>()
        val copies = mutableListOf<Pair<File, File>>()
        fontAssets.forEach { asset ->
            checkActive()
            val pending = staged(asset)
            val hash = pending.inputStream().use { digest(it, checkActive) }
            if (asset.reference.startsWith("bundled:")) {
                val matches = runCatching {
                    ClockFonts.openBundled(context, asset.reference).use { digest(it, checkActive) } == hash
                }.getOrDefault(false)
                checkActive()
                if (matches) {
                    fontMap[asset.reference] = asset.reference
                    return@forEach
                }
            }
            val imported = byDigest[hash] ?: run {
                require(existingFonts.size + newFonts.size < 32)
                require(pending.length() in 12..ClockArchive.MAX_FONT_BYTES.toLong())
                val header = pending.inputStream().use { it.readNBytes(4) }
                require(header.contentEquals(byteArrayOf(0, 1, 0, 0)) ||
                    header.contentEquals("OTTO".toByteArray(Charsets.US_ASCII)))
                Font.Builder(pending).build()
                ImportedFont("${UUID.randomUUID()}.${pending.extension}", asset.name.trim().ifEmpty { "Font" }).also {
                    newFonts += it
                    byDigest[hash] = it
                    copies += pending to requireNotNull(file(it.id))
                }
            }
            fontMap[asset.reference] = imported.family
        }
        require(existingFonts.size + newFonts.size <= 32)
        imageStore.prepareArchive(imageAssets.map(::staged))
        checkActive()
        imageAssets.forEach { asset ->
            val id = "${UUID.randomUUID()}.${staged(asset).extension}"
            imageMap[asset.reference] = id
            copies += staged(asset) to requireNotNull(imageStore.file(id))
        }
        val imported = source.copy(design = design.copy(id = UUID.randomUUID().toString(),
            elements = design.elements.map { element ->
                element.copy(id = UUID.randomUUID().toString(),
                    font = fontMap[element.font] ?: element.font,
                    image = imageMap[element.image] ?: element.image)
            }))
        val oldDesigns = prefs.getString("designs", "[]")
        val oldFonts = prefs.getString("fonts", "[]")
        val installed = mutableListOf<File>()
        var committed = false
        try {
            check(fontDir.isDirectory || fontDir.mkdirs())
            copies.forEach { (pending, target) ->
                checkActive()
                check(target.createNewFile())
                installed += target
                pending.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            }
            checkActive()
            check(prefs.edit()
                .putString("designs", JSONArray().also { a -> (designs + imported).forEach { a.put(it.toJson()) } }.toString())
                .putString("fonts", JSONArray().also { a -> (existingFonts + newFonts).forEach {
                    a.put(JSONObject().put("id", it.id).put("name", it.name))
                } }.toString()).commit())
            committed = true
        } finally {
            if (!committed) {
                prefs.edit().putString("designs", oldDesigns).putString("fonts", oldFonts).commit()
                installed.forEach { it.delete() }
            }
        }
        ClockFonts.clear()
        runCatching { resolver.notifyChange(ClockFonts.uri, null) }
        return imported
    }

    private companion object {
        val assetLock = Any()
    }

    private fun digest(input: InputStream, checkActive: () -> Unit): String {
        val hash = java.security.MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            checkActive()
            val count = input.read(buffer)
            if (count < 0) break
            hash.update(buffer, 0, count)
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
}
