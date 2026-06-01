package com.transparentlc.cloudclipboardsync.sync

import android.net.Uri

object SyncEndpointUrls {
    fun httpUrl(
        serverBase: String,
        path: String,
        query: Map<String, String> = emptyMap(),
    ): String {
        val normalizedBase = serverBase.trim().trimEnd('/')
        val normalizedPath = normalizeEndpointPath(normalizedBase, path)
        return appendQuery("$normalizedBase/$normalizedPath", query)
    }

    fun webSocketUrl(
        serverBase: String,
        path: String,
        query: Map<String, String> = emptyMap(),
    ): String {
        val httpUrl = httpUrl(serverBase, path, query)
        return when {
            httpUrl.startsWith("https://", ignoreCase = true) -> "wss://" + httpUrl.substringAfter("://")
            httpUrl.startsWith("http://", ignoreCase = true) -> "ws://" + httpUrl.substringAfter("://")
            else -> httpUrl
        }
    }

    private fun normalizeEndpointPath(baseUrl: String, path: String): String {
        val normalizedPath = path.trim().trimStart('/')
        return if (lastUrlSegment(baseUrl).equals("api", ignoreCase = true) && normalizedPath.startsWith("api/")) {
            normalizedPath.removePrefix("api/")
        } else {
            normalizedPath
        }
    }

    private fun appendQuery(rawUrl: String, query: Map<String, String>): String {
        if (query.isEmpty()) return rawUrl
        val builder = Uri.parse(rawUrl).buildUpon()
        query.forEach { (key, value) ->
            val normalizedValue = value.trim()
            if (key.isNotBlank() && normalizedValue.isNotBlank()) {
                builder.appendQueryParameter(key, normalizedValue)
            }
        }
        return builder.build().toString()
    }

    private fun lastUrlSegment(rawUrl: String): String {
        val parsedPath = runCatching { Uri.parse(rawUrl).path.orEmpty() }.getOrDefault("")
        val source = parsedPath.ifBlank { rawUrl }
        val trimmed = source.trim().trimEnd('/').trim('/')
        if (trimmed.isBlank()) return ""
        return trimmed.substringAfterLast('/')
    }
}
