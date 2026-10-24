package org.videopocket.probe.vault

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.videopocket.probe.core.*
import org.videopocket.probe.engine.ProbeEngine
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object VaultManager {
    private const val VAULT_DIR_NAME = "vault"
    private const val INDEX_FILE_NAME = "vault_index.json"
    private const val NOMEDIA_FILE_NAME = ".nomedia"

    private val _items = MutableStateFlow<List<VaultItem>>(emptyList())
    val items: StateFlow<List<VaultItem>> = _items.asStateFlow()

    private val _lockState = MutableStateFlow(VaultLockState.LOCKED)
    val lockState: StateFlow<VaultLockState> = _lockState.asStateFlow()

    private val lock = Any()

    fun lock() {
        _lockState.value = VaultLockState.LOCKED
    }

    fun unlock() {
        _lockState.value = VaultLockState.UNLOCKED
    }

    private fun getVaultDir(context: Context): File {
        val dir = File(context.filesDir, VAULT_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val noMedia = File(dir, NOMEDIA_FILE_NAME)
        if (!noMedia.exists()) {
            runCatching { noMedia.createNewFile() }
        }
        return dir
    }

    private fun getIndexFile(context: Context): File = File(getVaultDir(context), INDEX_FILE_NAME)

    fun initialize(context: Context) = synchronized(lock) {
        val indexFile = getIndexFile(context)
        if (!indexFile.exists()) {
            _items.value = emptyList()
            return@synchronized
        }
        try {
            val content = indexFile.readText()
            val array = JSONArray(content)
            val list = mutableListOf<VaultItem>()
            val vaultDir = getVaultDir(context)
            for (i in 0 until array.length()) {
                val item = VaultItem.fromJson(array.getJSONObject(i))
                val file = File(vaultDir, item.encryptedFileName)
                if (file.exists()) {
                    list.add(item)
                }
            }
            _items.value = list
        } catch (_: Exception) {
            _items.value = emptyList()
        }
    }

    private fun saveIndex(context: Context) = synchronized(lock) {
        try {
            val array = JSONArray()
            _items.value.forEach { array.put(it.toJson()) }
            getIndexFile(context).writeText(array.toString(2))
        } catch (_: Exception) {}
    }

    suspend fun importMedia(context: Context, uri: Uri, customTitle: String? = null): VaultItem = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val detectedMime = resolver.getType(uri) ?: "video/mp4"
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(detectedMime) ?: "mp4"

        var size = 0L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeCol = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (sizeCol != -1 && cursor.moveToFirst()) {
                size = cursor.getLong(sizeCol)
            }
        }

        val id = UUID.randomUUID().toString()
        val encFileName = "enc_${id}.bin"
        val vaultDir = getVaultDir(context)
        val encFile = File(vaultDir, encFileName)

        resolver.openInputStream(uri)?.use { input ->
            encFile.outputStream().use { output ->
                VaultCryptoEngine.encrypt(input, output)
            }
        } ?: throw IllegalStateException("Could not open input stream from source URI")

        val title = customTitle?.takeIf { it.isNotBlank() } ?: "Protected Media"
        val item = VaultItem(
            id = id,
            title = title,
            extension = ext,
            mimeType = detectedMime,
            originalBytes = if (size > 0) size else encFile.length(),
            encryptedAt = System.currentTimeMillis(),
            encryptedFileName = encFileName
        )

        synchronized(lock) {
            _items.value = listOf(item) + _items.value
            saveIndex(context)
        }
        item
    }

    suspend fun exportMedia(context: Context, item: VaultItem): String = withContext(Dispatchers.IO) {
        val vaultDir = getVaultDir(context)
        val encFile = File(vaultDir, item.encryptedFileName)
        if (!encFile.exists()) throw IllegalStateException("Encrypted file not found on disk")

        val tempDecrypted = File(context.cacheDir, "dec_${item.id}.${item.extension}")
        try {
            encFile.inputStream().use { input ->
                tempDecrypted.outputStream().use { output ->
                    VaultCryptoEngine.decrypt(input, output)
                }
            }

            val action = if (MediaTypes.isAudio(item.extension)) ProbeAction.MP3 else ProbeAction.VIDEO
            val savedUri = ProbeEngine(context).publishWithName(tempDecrypted, action, item.title)

            // Register back to library for convenience
            runCatching {
                PocketLibraryManager.addItem(
                    context,
                    PocketLibraryItem(
                        id = UUID.randomUUID().toString(),
                        title = item.title + " (Restored)",
                        uriString = savedUri,
                        duration = item.durationMs?.let { ClipRequest.formatSeconds(it / 1000.0) } ?: "—",
                        resolution = if (MediaTypes.isAudio(item.extension)) "Audio" else "—",
                        format = item.extension,
                        fileSizeBytes = item.originalBytes,
                        platform = "Vault",
                        mimeType = item.mimeType
                    )
                )
            }
            savedUri
        } finally {
            if (tempDecrypted.exists()) {
                tempDecrypted.delete()
            }
        }
    }

    suspend fun deleteItem(context: Context, item: VaultItem) = withContext(Dispatchers.IO) {
        val vaultDir = getVaultDir(context)
        val file = File(vaultDir, item.encryptedFileName)
        if (file.exists()) {
            file.delete()
        }
        synchronized(lock) {
            _items.value = _items.value.filter { it.id != item.id }
            saveIndex(context)
        }
    }

    fun decryptToStream(context: Context, item: VaultItem, output: java.io.OutputStream): Long {
        val vaultDir = getVaultDir(context)
        val file = File(vaultDir, item.encryptedFileName)
        if (!file.exists()) throw IllegalStateException("Vault encrypted file missing")
        return file.inputStream().use { input ->
            VaultCryptoEngine.decrypt(input, output)
        }
    }

    fun findItemById(id: String): VaultItem? = _items.value.firstOrNull { it.id == id }
}
