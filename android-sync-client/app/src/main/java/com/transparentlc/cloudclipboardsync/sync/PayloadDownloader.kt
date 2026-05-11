package com.transparentlc.cloudclipboardsync.sync

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.net.URI

object PayloadDownloader {
    private val client = OkHttpClient()

    fun download(context: Context, config: SettingsStore.Config, entry: PayloadEntry): PayloadEntry {
        PayloadCacheStore.pruneExpired(context)
        val targetUrl = entry.downloadUrl ?: entry.actionUrl
            ?: error("缺少可下载地址")
        val resolvedUrl = resolveUrl(config.serverBase, targetUrl)
        val cacheFile = PayloadCacheStore.createCacheFile(context, entry.payloadId, entry.title)
        val requestBuilder = Request.Builder().url(resolvedUrl)
        if (config.roomPassword.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${config.roomPassword}")
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                error("下载失败：HTTP ${response.code}")
            }
            val body = response.body ?: error("下载失败：响应体为空")
            FileOutputStream(cacheFile).use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
            val finalMime = response.header("Content-Type")?.substringBefore(';')?.ifBlank { null } ?: entry.mime
            val finalSize = cacheFile.length().takeIf { it > 0 } ?: entry.size
            return PayloadCacheStore.markDownloaded(
                context = context,
                payloadId = entry.payloadId,
                localPath = cacheFile.absolutePath,
                size = finalSize,
                mime = finalMime,
            ) ?: error("保存缓存记录失败")
        }
    }

    private fun resolveUrl(serverBase: String, targetUrl: String): String {
        if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
            return targetUrl
        }
        val base = if (serverBase.endsWith("/")) serverBase else "$serverBase/"
        return URI(base).resolve(targetUrl).toString()
    }
}
