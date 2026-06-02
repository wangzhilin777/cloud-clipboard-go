package com.transparentlc.cloudclipboardsync.sync

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
            return resolveRelativeUrl(serverBase, normalizedTarget)
        }
        return httpUrl(serverBase, normalizedTarget)
    }

    private fun resolveRelativeUrl(serverBase: String, targetUrl: String): String {
        val path = targetUrl.substringBefore('?').substringBefore('#')
        var resolved = httpUrl(serverBase, path)
        val query = rawQuery(targetUrl)
        if (query.isNotBlank()) {
            resolved += "?$query"
        }
        val fragment = targetUrl.substringAfter('#', missingDelimiterValue = "")
        if (fragment.isNotBlank()) {
            resolved += "#$fragment"
        }
        return resolved
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
        val fragment = rawUrl.substringAfter('#', missingDelimiterValue = "")
        val withoutFragment = rawUrl.substringBefore('#')
        val separator = if (withoutFragment.contains('?')) "&" else "?"
        val queryString = query.entries
            .mapNotNull { (key, value) ->
                val normalizedValue = value.trim()
                if (key.isBlank() || normalizedValue.isBlank()) {
                    null
                } else {
                    "${encodeQueryComponent(key)}=${encodeQueryComponent(normalizedValue)}"
                }
            }
            .joinToString("&")
        if (queryString.isBlank()) return rawUrl
        val withQuery = withoutFragment + separator + queryString
        return if (fragment.isBlank()) withQuery else "$withQuery#$fragment"
    }

    private fun rawQuery(rawUrl: String): String {
        val queryAndFragment = rawUrl.substringAfter('?', missingDelimiterValue = "")
        if (queryAndFragment.isBlank()) return ""
        return queryAndFragment.substringBefore('#')
    }

    private fun encodeQueryComponent(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun lastUrlSegment(rawUrl: String): String {
        val parsedPath = runCatching { URI(rawUrl).path.orEmpty() }.getOrDefault("")
        val source = parsedPath.ifBlank { rawUrl }
        val trimmed = source.trim().trimEnd('/').trim('/')
        if (trimmed.isBlank()) return ""
        return trimmed.substringAfterLast('/')
    }
}
