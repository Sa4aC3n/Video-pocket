package org.videopocket.probe.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object PocketLibraryManager {
    private val changes = MutableStateFlow(0L)
    val revisions = changes.asStateFlow()
    private const val FILE_NAME = "pocket_library.json"

    private fun getFile(context: Context): File = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun getItems(context: Context): List<PocketLibraryItem> {
        val file = getFile(context)
        if (!file.exists() && !File(file.path + ".bak").exists()) return emptyList()
        return try {
            val text = AtomicFile(file).openRead().bufferedReader().use { it.readText() }
            val array = JSONArray(text)
            val items = mutableListOf<PocketLibraryItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val collectionsArray = obj.optJSONArray("collections")
                val colList = mutableListOf<String>()
                if (collectionsArray != null) {
                    for (c in 0 until collectionsArray.length()) {
                        colList.add(collectionsArray.getString(c))
                    }
                }
                items.add(
                    PocketLibraryItem(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        uriString = obj.optString("uriString"),
                        isClip = obj.optBoolean("isClip"),
                        clipRange = obj.optString("clipRange").takeIf { it.isNotBlank() },
                        duration = obj.optString("duration"),
                        resolution = obj.optString("resolution"),
                        format = obj.optString("format"),
                        fileSizeBytes = obj.optLong("fileSizeBytes"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        thumbnailUri = obj.optString("thumbnailUri").takeIf { it.isNotBlank() },
                        platform = obj.optString("platform").takeIf { it.isNotBlank() },
                        isFavorite = obj.optBoolean("isFavorite", false),
                        collections = colList,
                        sourceMediaId = obj.optString("sourceMediaId").takeIf { it.isNotBlank() },
                        sourceUrl = obj.optString("sourceUrl").takeIf { it.isNotBlank() },
                        mimeType = obj.optString("mimeType").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
            items.sortedByDescending { it.createdAt }
        } catch (failure: Exception) {
            throw java.io.IOException("Library index could not be read; original retained", failure)
        }
    }

    @Synchronized
    fun saveItems(context: Context, items: List<PocketLibraryItem>) {
        val atomic = AtomicFile(getFile(context))
        var stream: java.io.FileOutputStream? = null
        try {
            val array = JSONArray()
            items.forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("uriString", item.uriString)
                    put("isClip", item.isClip)
                    put("clipRange", item.clipRange ?: "")
                    put("duration", item.duration)
                    put("resolution", item.resolution)
                    put("format", item.format)
                    put("fileSizeBytes", item.fileSizeBytes)
                    put("createdAt", item.createdAt)
                    put("thumbnailUri", item.thumbnailUri ?: "")
                    put("platform", item.platform ?: "")
                    put("isFavorite", item.isFavorite)
                    val cols = JSONArray()
                    item.collections.forEach { cols.put(it) }
                    put("collections", cols)
                    put("sourceMediaId", item.sourceMediaId ?: "")
                    put("sourceUrl", item.sourceUrl ?: "")
                    put("mimeType", item.mimeType ?: "")
                }
                array.put(obj)
            }
            stream = atomic.startWrite()
            stream.write(array.toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
            changes.value += 1
        } catch (failure: Exception) {
            atomic.failWrite(stream)
            throw java.io.IOException("Library index could not be saved; previous index retained", failure)
        }
    }

    @Synchronized
    fun toggleFavorite(context: Context, id: String) {
        val current = getItems(context)
        val updated = current.map { item ->
            if (item.id == id) item.copy(isFavorite = !item.isFavorite) else item
        }
        saveItems(context, updated)
    }

    @Synchronized
    fun updateCollections(context: Context, id: String, collections: List<String>) {
        val current = getItems(context)
        val updated = current.map { item ->
            if (item.id == id) item.copy(collections = collections) else item
        }
        saveItems(context, updated)
    }

    @Synchronized
    fun addItem(context: Context, item: PocketLibraryItem) {
        addItems(context, listOf(item))
    }

    @Synchronized
    fun addItems(context: Context, items: List<PocketLibraryItem>) {
        val incomingUris = items.map { it.uriString }.toSet()
        saveItems(context, items + getItems(context).filter { it.uriString !in incomingUris })
    }

    @Synchronized
    fun deleteItem(context: Context, id: String) {
        val current = getItems(context)
        val item = current.firstOrNull { it.id == id }
        if (item != null) {
            val uri = Uri.parse(item.uriString)
            check(context.contentResolver.delete(uri, null, null) > 0) { "Media was not deleted; library entry retained" }
        }
        val remaining = current.filter { it.id != id }
        saveItems(context, remaining)
    }

    @Synchronized
    fun renameItem(context: Context, id: String, newTitle: String) {
        val current = getItems(context)
        val updated = current.map { item ->
            if (item.id == id) item.copy(title = newTitle) else item
        }
        saveItems(context, updated)
    }

    fun playItem(context: Context, item: PocketLibraryItem) {
        try {
            val mime = item.mimeType ?: context.contentResolver.getType(Uri.parse(item.uriString)) ?: MediaTypes.fallbackMime(item.format)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(item.uriString), mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) { showOpenFailure(context) }
    }

    fun shareItem(context: Context, item: PocketLibraryItem) {
        try {
            val uri = Uri.parse(item.uriString)
            val mime = item.mimeType ?: context.contentResolver.getType(uri) ?: MediaTypes.fallbackMime(item.format)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, item.title)
                clipData = android.content.ClipData.newRawUri(item.title, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, item.title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) { showOpenFailure(context) }
    }

    private fun showOpenFailure(context: Context) {
        android.widget.Toast.makeText(context,
            if (AppSettings.isArabic) "تعذر فتح الملف. تحقق من وجوده ومن تثبيت مشغل متوافق." else "Unable to open file. Check that it exists and a compatible player is installed.",
            android.widget.Toast.LENGTH_LONG).show()
    }
}
