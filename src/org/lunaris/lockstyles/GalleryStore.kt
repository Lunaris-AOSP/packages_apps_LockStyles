package org.lunaris.lockstyles

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject

/** Saved lock screens, added wallpaper packs and what the live wallpaper should draw. */
class GalleryStore private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("wallpaper_gallery", Context.MODE_PRIVATE)
    private val listeners = mutableListOf<() -> Unit>()
    private val pendingPhotos = mutableMapOf<String, Int>()
    // Held strongly: SharedPreferences only keeps a weak reference.
    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> listeners.forEach { it() } }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    val lockScreens: List<LockScreen>
        get() {
            val array = JSONArray(prefs.getString(KEY_LOCK_SCREENS, "[]"))
            return List(array.length()) { LockScreen.fromJson(array.getJSONObject(it)) }
        }

    val currentId: String?
        get() = prefs.getString(KEY_CURRENT, null)

    @Synchronized
    fun save(lockScreen: LockScreen, makeCurrent: Boolean = false) {
        val list = lockScreens.toMutableList()
        val index = list.indexOfFirst { it.id == lockScreen.id }
        if (index >= 0) list[index] = lockScreen else list += lockScreen
        write(list, if (makeCurrent) lockScreen.id else currentId)
    }

    @Synchronized
    fun saveInitial(lockScreen: LockScreen): Boolean {
        if (lockScreens.isNotEmpty()) return false
        save(lockScreen, makeCurrent = true)
        return true
    }

    @Synchronized
    fun delete(id: String) {
        write(lockScreens.filter { it.id != id }, currentId?.takeUnless { it == id })
    }

    private fun write(list: List<LockScreen>, current: String?) {
        prefs.edit().putString(KEY_LOCK_SCREENS,
            JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }.toString())
            .putString(KEY_CURRENT, current).apply()
    }

    fun newId(): String = UUID.randomUUID().toString()

    // Packs added with "Get".

    fun isAdded(pack: String) = prefs.getStringSet(KEY_PACKS, emptySet())!!.contains(pack)

    fun setAdded(pack: String, added: Boolean) {
        val packs = prefs.getStringSet(KEY_PACKS, emptySet())!!.toMutableSet()
        if (added) packs += pack else packs -= pack
        prefs.edit().putStringSet(KEY_PACKS, packs).apply()
    }

    // What the live wallpaper service draws, per screen.

    fun liveWallpaper(lock: Boolean): GalleryWallpaper? =
        prefs.getString(if (lock) KEY_LIVE_LOCK else KEY_LIVE_HOME, null)?.let {
            GalleryWallpaper.fromJson(JSONObject(it))
        }

    fun setLiveWallpaper(lock: Boolean, wallpaper: GalleryWallpaper?) {
        val key = if (lock) KEY_LIVE_LOCK else KEY_LIVE_HOME
        prefs.edit().apply {
            if (wallpaper == null) remove(key) else putString(key, wallpaper.toJson().toString())
        }.commit()
    }

    var shuffleIndex: Int
        get() = prefs.getInt(KEY_SHUFFLE_INDEX, 0)
        set(value) = prefs.edit().putInt(KEY_SHUFFLE_INDEX, value).apply()

    var shuffleChangedAt: Long
        get() = prefs.getLong(KEY_SHUFFLE_AT, 0)
        set(value) = prefs.edit().putLong(KEY_SHUFFLE_AT, value).apply()

    /** Copies a picked photo into the app, scaled to the screen, and returns its path. */
    fun importPhoto(uri: Uri): String? {
        var bitmap: Bitmap? = null
        return try {
            val maxSide = max(context.resources.displayMetrics.widthPixels,
                context.resources.displayMetrics.heightPixels).coerceAtLeast(1).toLong() * 3 / 2
            val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(
                context.contentResolver, uri)) { decoder, info, _ ->
                val side = max(info.size.width, info.size.height)
                if (side > maxSide) {
                    decoder.setTargetSize((info.size.width * maxSide / side).toInt().coerceAtLeast(1),
                        (info.size.height * maxSide / side).toInt().coerceAtLeast(1))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            bitmap = decoded
            importBitmap(decoded)
        } catch (e: Exception) {
            Log.w("GalleryStore", "Could not import a wallpaper photo", e)
            null
        } finally {
            bitmap?.recycle()
        }
    }

    fun importBitmap(bitmap: Bitmap): String? {
        var file: File? = null
        return try {
            val dir = File(context.filesDir, "gallery").apply { mkdirs() }
            val destination = File(dir, "${UUID.randomUUID()}.jpg")
            file = destination
            retainImportedPhotos(listOf(destination.absolutePath))
            FileOutputStream(destination).use {
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it))
            }
            destination.absolutePath
        } catch (e: Exception) {
            file?.let { destination ->
                synchronized(this) {
                    destination.delete()
                    pendingPhotos -= destination.absolutePath
                }
            }
            Log.w("GalleryStore", "Could not import a wallpaper photo", e)
            null
        }
    }

    @Synchronized
    fun retainImportedPhotos(paths: Collection<String>) {
        paths.toSet().forEach { pendingPhotos[it] = (pendingPhotos[it] ?: 0) + 1 }
    }

    @Synchronized
    fun discardImportedPhotos(paths: Collection<String>) {
        paths.toSet().forEach { path ->
            val count = pendingPhotos[path] ?: 0
            if (count <= 1) pendingPhotos.remove(path) else pendingPhotos[path] = count - 1
        }
        val used = usedPhotos()
        val dir = File(context.filesDir, "gallery")
        paths.forEach { path ->
            val file = File(path)
            if (file.parentFile == dir && path !in used) file.delete()
        }
    }

    private fun usedPhotos(): Set<String> =
        lockScreens.flatMap { it.wallpaper.photos }.toSet() + pendingPhotos.keys +
            listOfNotNull(liveWallpaper(true), liveWallpaper(false)).flatMap { it.photos }

    /** Removes imported photos no saved lock screen uses any more. */
    @Synchronized
    fun pruneImportedPhotos() {
        val used = usedPhotos()
        File(context.filesDir, "gallery").listFiles()?.forEach {
            if (it.absolutePath !in used) it.delete()
        }
    }

    companion object {
        private const val KEY_LOCK_SCREENS = "lock_screens"
        private const val KEY_CURRENT = "current"
        private const val KEY_PACKS = "packs"
        private const val KEY_LIVE_LOCK = "live_lock"
        private const val KEY_LIVE_HOME = "live_home"
        private const val KEY_SHUFFLE_INDEX = "shuffle_index"
        private const val KEY_SHUFFLE_AT = "shuffle_at"

        const val PACK_EMOJI = "emoji"
        const val PACK_KALEIDOSCOPE = "kaleidoscope"

        @Volatile private var instance: GalleryStore? = null

        fun get(context: Context): GalleryStore =
            instance ?: synchronized(this) {
                instance ?: GalleryStore(context.applicationContext).also { instance = it }
            }
    }
}
