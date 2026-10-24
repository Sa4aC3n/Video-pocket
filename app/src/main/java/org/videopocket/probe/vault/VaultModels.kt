package org.videopocket.probe.vault

import org.json.JSONObject
import java.util.UUID

enum class VaultLockState {
    LOCKED,
    UNLOCKED,
    AUTHENTICATING
}

data class VaultItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val extension: String,
    val mimeType: String,
    val originalBytes: Long,
    val durationMs: Long? = null,
    val encryptedAt: Long = System.currentTimeMillis(),
    val encryptedFileName: String = "enc_${id}.bin"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("extension", extension)
        put("mimeType", mimeType)
        put("originalBytes", originalBytes)
        put("durationMs", durationMs ?: JSONObject.NULL)
        put("encryptedAt", encryptedAt)
        put("encryptedFileName", encryptedFileName)
    }

    companion object {
        fun fromJson(json: JSONObject): VaultItem = VaultItem(
            id = json.getString("id"),
            title = json.getString("title"),
            extension = json.optString("extension", "bin"),
            mimeType = json.optString("mimeType", "application/octet-stream"),
            originalBytes = json.getLong("originalBytes"),
            durationMs = if (json.isNull("durationMs")) null else json.getLong("durationMs"),
            encryptedAt = json.optLong("encryptedAt", System.currentTimeMillis()),
            encryptedFileName = json.optString("encryptedFileName", "enc_${json.getString("id")}.bin")
        )
    }
}
