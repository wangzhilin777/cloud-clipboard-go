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

    fun resolveUrl(serverBase: String, targetUrl: String): String {
        val normalizedTarget = targetUrl.trim()
        if (normalizedTarget.startsWith("http://", ignoreCase = true) ||
            normalizedTarget.startsWith("https://", ignoreCase = true)
        ) {
            return normalizedTarget
        }
        if (normalizedTarget.startsWith("/")) {
            val parsedTarget = Uri.parse(normalizedTarget)
            return Uri.parse(serverBase.trim()).buildUpon()
                .encodedPath(parsedTarget.encodedPath)
                .encodedQuery(parsedTarget.encodedQuery)
                .encodedFragment(parsedTarget.encodedFragment)
                .build()
                .toString()
        }
        return httpUrl(serverBase, normalizedTarget)
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
