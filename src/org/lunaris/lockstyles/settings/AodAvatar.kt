package org.lunaris.lockstyles.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

object AodAvatar {
    const val AUTHORITY = "org.lunaris.lockstyles.aod"
    private const val DIRECTORY = "aod_avatar"
    private val FILE_NAME = Regex("[a-f0-9-]{36}\\.png")

    @Synchronized
    fun save(context: Context, source: Uri): String? {
        var output: File? = null
        return try {
            val directory = File(context.filesDir, DIRECTORY)
            check(directory.isDirectory || directory.mkdirs())
            val file = File(directory, "${UUID.randomUUID()}.png")
            output = file
            val bitmap = decode(context, source.toString(), 1024)
            try {
                FileOutputStream(file).use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    stream.fd.sync()
                }
            } finally {
                bitmap.recycle()
            }
            val uri = ContentProvider.maybeAddUserId(Uri.Builder().scheme("content")
                .authority(AUTHORITY).appendPath(file.name).build(), context.userId).toString()
            check(Settings.System.putString(context.contentResolver, Keys.AOD_IMAGE_URI.key, uri))
            output = null
            Keys.AOD_IMAGE.write(context, true)
            directory.listFiles()?.filter { it != file }?.forEach { it.delete() }
            uri
        } catch (e: Exception) {
            output?.delete()
            Log.e("AodAvatar", "Could not save avatar", e)
            null
        }
    }

    @Synchronized
    fun clear(context: Context) {
        Keys.AOD_IMAGE.write(context, false)
        if (Settings.System.putString(context.contentResolver, Keys.AOD_IMAGE_URI.key, null)) {
            File(context.filesDir, DIRECTORY).listFiles()?.forEach { it.delete() }
        }
    }

    fun decode(context: Context, path: String, maxSide: Int): Bitmap {
        val uri = Uri.parse(path)
        val source = if (uri.scheme == null) ImageDecoder.createSource(File(path))
            else ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val scale = minOf(maxSide.toFloat() / maxOf(info.size.width, info.size.height), 1f)
            decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1),
                (info.size.height * scale).roundToInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    fun file(context: Context, uri: Uri): File {
        val name = uri.pathSegments.singleOrNull()
        if (name == null || !FILE_NAME.matches(name)) throw FileNotFoundException("Invalid avatar")
        return File(File(context.filesDir, DIRECTORY), name)
    }
}

class AodAvatarProvider : ContentProvider() {
    override fun onCreate() = true

    override fun getType(uri: Uri) = "image/png"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Avatars are read only")
        return ParcelFileDescriptor.open(AodAvatar.file(requireNotNull(context), uri),
            ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val file = AodAvatar.file(requireNotNull(context), uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            if (file.isFile) addRow(columns.map {
                when (it) {
                    OpenableColumns.DISPLAY_NAME -> file.name
                    OpenableColumns.SIZE -> file.length()
                    else -> null
                }
            }.toTypedArray())
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Avatars are read only")

    override fun update(uri: Uri, values: ContentValues?, selection: String?,
            selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Avatars are read only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Avatars are read only")
}
