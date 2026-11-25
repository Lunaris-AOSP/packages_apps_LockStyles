package org.lunaris.lockstyles.models

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.ParcelFileDescriptor

class ModelProvider : ContentProvider() {
    override fun onCreate() = true
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode == "r")
        return ParcelFileDescriptor.open(DepthModels.open(requireNotNull(context), uri.lastPathSegment),
            ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun getType(uri: Uri) = "application/octet-stream"
    override fun query(uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?): android.database.Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, args: Array<String>?): Int = throw UnsupportedOperationException()
}
