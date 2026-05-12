package com.transparentlc.cloudclipboardsync.sync

import org.json.JSONObject

data class PayloadNotice(
    val payloadId: String,
    val sourceDeviceId: String,
    val room: String,
    val kind: String,
    val title: String,
    val mime: String,
    val size: Long,
    val actionUrl: String?,
    val downloadUrl: String?,
    val createdAt: Long,
) {
    companion object {
        fun fromJson(json: JSONObject): PayloadNotice = PayloadNotice(
            payloadId = json.optString("payloadId"),
            sourceDeviceId = json.optString("sourceDeviceId"),
            room = json.optString("room"),
            kind = json.optString("kind"),
            title = json.optString("title").ifBlank { "未命名内容" },
            mime = json.optString("mime").ifBlank { "application/octet-stream" },
            size = json.optLong("size", 0L),
            actionUrl = json.optString("actionUrl").ifBlank { null },
            downloadUrl = json.optString("downloadUrl").ifBlank { null },
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
        )
    }
}

data class PayloadEntry(
    val payloadId: String,
    val sourceDeviceId: String,
    val room: String,
    val kind: String,
    val title: String,
    val mime: String,
    val size: Long,
    val actionUrl: String?,
    val downloadUrl: String?,
    val createdAt: Long,
    val localPath: String?,
    val downloadedAt: Long?,
    val expiresAt: Long,
    val processedAt: Long?,
    val snoozedUntil: Long?,
) {
    val isDownloaded: Boolean
        get() = !localPath.isNullOrBlank()

    val isImage: Boolean
        get() = mime.startsWith("image/")

    fun toJson(): JSONObject = JSONObject()
        .put("payloadId", payloadId)
        .put("sourceDeviceId", sourceDeviceId)
        .put("room", room)
        .put("kind", kind)
        .put("title", title)
        .put("mime", mime)
        .put("size", size)
        .put("actionUrl", actionUrl)
        .put("downloadUrl", downloadUrl)
        .put("createdAt", createdAt)
        .put("localPath", localPath)
        .put("downloadedAt", downloadedAt)
        .put("expiresAt", expiresAt)
        .put("processedAt", processedAt)
        .put("snoozedUntil", snoozedUntil)

    companion object {
        fun fromJson(json: JSONObject): PayloadEntry = PayloadEntry(
            payloadId = json.optString("payloadId"),
            sourceDeviceId = json.optString("sourceDeviceId"),
            room = json.optString("room"),
            kind = json.optString("kind"),
            title = json.optString("title").ifBlank { "未命名内容" },
            mime = json.optString("mime").ifBlank { "application/octet-stream" },
            size = json.optLong("size", 0L),
            actionUrl = json.optString("actionUrl").ifBlank { null },
            downloadUrl = json.optString("downloadUrl").ifBlank { null },
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            localPath = json.optString("localPath").ifBlank { null },
            downloadedAt = json.optLong("downloadedAt").takeIf { json.has("downloadedAt") },
            expiresAt = json.optLong("expiresAt", System.currentTimeMillis() + PayloadCacheStore.DEFAULT_RETENTION_MS),
            processedAt = json.optLong("processedAt").takeIf { json.has("processedAt") },
            snoozedUntil = json.optLong("snoozedUntil").takeIf { json.has("snoozedUntil") },
        )
    }
}
