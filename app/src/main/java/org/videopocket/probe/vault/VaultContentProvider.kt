package org.videopocket.probe.vault

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileOutputStream

class VaultContentProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        context?.let { VaultManager.initialize(it) }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun getType(uri: Uri): String? {
        val id = uri.lastPathSegment ?: return null
        return VaultManager.findItemById(id)?.mimeType ?: "video/mp4"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val id = uri.lastPathSegment ?: return null
        val ctx = context ?: return null
        val item = VaultManager.findItemById(id) ?: return null

        return openPipeHelper(uri, item.mimeType, null, null) { output, _, _, _, _ ->
            try {
                FileOutputStream(output.fileDescriptor).use { fos ->
                    VaultManager.decryptToStream(ctx, item, fos)
                }
            } catch (_: Exception) {}
        }
    }

    companion object {
        fun buildPlayUri(context: android.content.Context, id: String): Uri {
            return Uri.parse("content://${context.packageName}.vault/play/$id")
        }
    }
}
