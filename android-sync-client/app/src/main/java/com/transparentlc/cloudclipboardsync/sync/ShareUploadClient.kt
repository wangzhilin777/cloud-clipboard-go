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
import java.io.IOException
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
        config: SettingsStore.Config,
        text: String,
    ): SharedResult {
        val normalized = text.trim()
        require(normalized.isNotBlank()) { "没有可发送的文本内容" }
        val baseUrl = config.serverBase.trimEnd('/')
        val body = normalized.toRequestBody("text/plain; charset=utf-8".toMediaTypeOrNull())
        executeFirstSuccessful(
            urls = endpointCandidates(baseUrl, config.room, "text", "api/text"),
            actionName = "文本发送",
        ) { url ->
            Request.Builder()
                .url(url)
                .header("Content-Type", "text/plain; charset=utf-8")
                .applyAuth(config)
                .post(body)
                .build()
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
            publishPayloadNotice(baseUrl, config, item, uploadResponse)
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
        val actionUrl: String,
        val downloadUrl: String,
    )

    private fun uploadSingleFile(
        context: Context,
        baseUrl: String,
        config: SettingsStore.Config,
        resolver: ContentResolver,
        item: SharedItem,
    ): UploadResponse {
        val tempFile = File.createTempFile("share_", "_" + safeTempSuffix(item.name), context.cacheDir)
        try {
            resolver.openInputStream(item.uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("无法读取共享内容：${item.name}")

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    item.name,
                    tempFile.asRequestBody(item.mime.toMediaTypeOrNull()),
                )
                .build()

            val responseBody = executeFirstSuccessful(
                urls = endpointCandidates(baseUrl, config.room, "upload", "api/upload"),
                actionName = "上传",
            ) { url ->
                Request.Builder()
                    .url(url)
                    .applyAuth(config)
                    .post(requestBody)
                    .build()
            }
            val body = JSONObject(responseBody)
            val result = body.optJSONObject("result")
            val actionUrl = firstNonBlank(
                body.optString("url"),
                body.optString("actionUrl"),
                result?.optString("url").orEmpty(),
                result?.optString("actionUrl").orEmpty(),
                body.optString("downloadUrl"),
                result?.optString("downloadUrl").orEmpty(),
            )
            val downloadUrl = firstNonBlank(
                body.optString("downloadUrl"),
                result?.optString("downloadUrl").orEmpty(),
                body.optString("actionUrl"),
                result?.optString("actionUrl").orEmpty(),
                body.optString("url"),
                result?.optString("url").orEmpty(),
            )
            if (actionUrl.isBlank() || downloadUrl.isBlank()) {
                error("上传成功但没有返回内容地址")
            }
            return UploadResponse(actionUrl = actionUrl, downloadUrl = downloadUrl)
        } finally {
            tempFile.delete()
        }
    }

    private fun publishPayloadNotice(
        baseUrl: String,
        config: SettingsStore.Config,
        item: SharedItem,
        uploadResponse: UploadResponse,
    ) {
        val payload = JSONObject()
            .put("payloadId", UUID.randomUUID().toString())
            .put("sourceDeviceId", config.deviceId)
            .put("room", config.room)
            .put("kind", item.kind)
            .put("title", item.name)
            .put("mime", item.mime)
            .put("size", item.size.coerceAtLeast(0L))
            .put("actionUrl", uploadResponse.actionUrl)
            .put("downloadUrl", uploadResponse.downloadUrl)
            .put("createdAt", System.currentTimeMillis())

        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        executeFirstSuccessful(
            urls = endpointCandidates(
                baseUrl,
                config.room,
                "api/sync/payload-notice",
                "api/sync/payload/notice",
            ),
            actionName = "发送接收通知",
        ) { url ->
            Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .applyAuth(config)
                .post(body)
                .build()
        }
    }

    private fun executeFirstSuccessful(
        urls: List<String>,
        actionName: String,
        requestFactory: (String) -> Request,
    ): String {
        var lastFailure = "${actionName}失败：没有可用的服务地址"
        urls.forEachIndexed { index, url ->
            try {
                client.newCall(requestFactory(url)).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        return responseBody
                    }
                    val detail = responseBody.trim().takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
                    lastFailure = "${actionName}失败：HTTP ${response.code}$detail"
                    val canTryNext = response.code == 404 || response.code == 405
                    if (!canTryNext || index == urls.lastIndex) {
                        error(lastFailure)
                    }
                }
            } catch (error: IOException) {
                lastFailure = "${actionName}失败：${error.message ?: "网络不可用"}"
                if (index == urls.lastIndex) error(lastFailure)
            }
        }
        error(lastFailure)
    }

    private fun safeTempSuffix(name: String): String {
        val sanitized = name
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), "_")
            .trim('_')
            .ifBlank { UUID.randomUUID().toString() }
        return sanitized.takeLast(120)
    }

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.trim().isNotBlank() }?.trim().orEmpty()

    private fun endpointCandidates(baseUrl: String, room: String, vararg paths: String): List<String> =
        paths.map { endpointUrl(baseUrl, it, room) }.distinct()

    private fun endpointUrl(baseUrl: String, path: String, room: String): String {
        val normalizedBase = baseUrl.trimEnd('/')
        val normalizedPath = normalizeEndpointPath(normalizedBase, path)
        return "$normalizedBase/$normalizedPath${roomQuery(room)}"
    }

    private fun normalizeEndpointPath(baseUrl: String, path: String): String {
        val normalizedPath = path.trim().trimStart('/')
        return if (baseUrl.substringAfterLast('/').equals("api", ignoreCase = true) && normalizedPath.startsWith("api/")) {
            normalizedPath.removePrefix("api/")
        } else {
            normalizedPath
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
