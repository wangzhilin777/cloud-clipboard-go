package com.transparentlc.cloudclipboardsync.sync

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

object ShareUploadClient {
    private val client = OkHttpClient()

    data class SharedItem(
        val uri: Uri,
        val name: String,
        val mime: String,
        val size: Long,
        val kind: String,
    )

    data class SharedResult(
        val sharedCount: Int,
        val resultMessage: String,
    )

    fun shareText(
        context: Context,
        config: SettingsStore.Config,
        text: String,
    ): SharedResult {
        val normalized = text.trim()
        require(normalized.isNotBlank()) { "没有可发送的文本内容" }
        val baseUrl = config.serverBase.trimEnd('/')
        val request = Request.Builder()
            .url("$baseUrl/text${roomQuery(config.room)}")
            .header("Content-Type", "text/plain; charset=utf-8")
            .applyAuth(config)
            .post(normalized.toRequestBody("text/plain; charset=utf-8".toMediaTypeOrNull()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("文本发送失败：HTTP ${response.code}")
            }
        }
        return SharedResult(
            sharedCount = 1,
            resultMessage = "文本已发送到同步房间",
        )
    }

    fun shareFiles(
        context: Context,
        config: SettingsStore.Config,
        items: List<SharedItem>,
    ): SharedResult {
        require(items.isNotEmpty()) { "没有可发送的文件或图片" }
        val resolver = context.contentResolver
        val baseUrl = config.serverBase.trimEnd('/')
        items.forEach { item ->
            val uploadResponse = uploadSingleFile(context, baseUrl, config, resolver, item)
            publishPayloadNotice(baseUrl, config, item, uploadResponse.contentUrl)
        }
        val allImages = items.all { it.kind == "image" }
        return SharedResult(
            sharedCount = items.size,
            resultMessage = if (allImages) {
                "已发送 ${items.size} 张图片到同步房间"
            } else {
                "已发送 ${items.size} 个文件到同步房间"
            },
        )
    }

    fun resolveSharedItems(context: Context, uris: List<Uri>): List<SharedItem> {
        val resolver = context.contentResolver
        return uris.distinct().map { uri ->
            val mime = resolver.getType(uri)?.ifBlank { null } ?: "application/octet-stream"
            val info = resolver.query(uri, null, null, null, null)?.use(::readOpenableInfo)
            val name = info?.first?.ifBlank { uri.lastPathSegment ?: UUID.randomUUID().toString() }
                ?: (uri.lastPathSegment ?: UUID.randomUUID().toString())
            val size = info?.second ?: -1L
            SharedItem(
                uri = uri,
                name = name,
                mime = mime,
                size = size,
                kind = if (mime.startsWith("image/")) "image" else "file",
            )
        }
    }

    private data class UploadResponse(
        val contentUrl: String,
    )

    private fun uploadSingleFile(
        context: Context,
        baseUrl: String,
        config: SettingsStore.Config,
        resolver: ContentResolver,
        item: SharedItem,
    ): UploadResponse {
        val tempFile = File.createTempFile("share_", "_" + item.name, context.cacheDir)
        resolver.openInputStream(item.uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取共享内容：${item.name}")

        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    item.name,
                    tempFile.asRequestBody(item.mime.toMediaTypeOrNull()),
                )
                .build()

            val request = Request.Builder()
                .url("$baseUrl/upload${roomQuery(config.room)}")
                .applyAuth(config)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("上传失败：HTTP ${response.code}")
                }
                val body = JSONObject(response.body?.string().orEmpty())
                val contentUrl = body.optString("url").trim()
                if (contentUrl.isBlank()) {
                    error("上传成功但没有返回内容地址")
                }
                return UploadResponse(contentUrl = contentUrl)
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun publishPayloadNotice(
        baseUrl: String,
        config: SettingsStore.Config,
        item: SharedItem,
        contentUrl: String,
    ) {
        val payload = JSONObject()
            .put("payloadId", UUID.randomUUID().toString())
            .put("sourceDeviceId", config.deviceId)
            .put("room", config.room)
            .put("kind", item.kind)
            .put("title", item.name)
            .put("mime", item.mime)
            .put("size", item.size.coerceAtLeast(0L))
            .put("actionUrl", contentUrl)
            .put("downloadUrl", contentUrl)
            .put("createdAt", System.currentTimeMillis())

        val request = Request.Builder()
            .url("$baseUrl/api/sync/payload/notice")
            .header("Content-Type", "application/json")
            .applyAuth(config)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("发送接收通知失败：HTTP ${response.code}")
            }
        }
    }

    private fun roomQuery(room: String): String {
        val normalized = room.trim()
        return if (normalized.isBlank()) {
            ""
        } else {
            "?room=" + URLEncoder.encode(normalized, StandardCharsets.UTF_8.toString())
        }
    }

    private fun Request.Builder.applyAuth(config: SettingsStore.Config): Request.Builder = apply {
        if (config.roomPassword.isNotBlank()) {
            header("Authorization", "Bearer ${config.roomPassword}")
        }
    }

    private fun readOpenableInfo(cursor: Cursor): Pair<String, Long> {
        var name = ""
        var size = -1L
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameIndex >= 0) {
                name = cursor.getString(nameIndex).orEmpty()
            }
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                size = cursor.getLong(sizeIndex)
            }
        }
        return name to size
    }
}
