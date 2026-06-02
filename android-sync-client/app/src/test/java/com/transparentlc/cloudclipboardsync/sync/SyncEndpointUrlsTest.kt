package com.transparentlc.cloudclipboardsync.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SyncEndpointUrlsTest {
    @Test
    fun httpUrlDeduplicatesApiBase() {
        val got = SyncEndpointUrls.httpUrl(
            serverBase = "https://example.com/api",
            path = "api/sync/bootstrap",
            query = mapOf("room" to "研发 房"),
        )

        assertEquals("https://example.com/api/sync/bootstrap?room=%E7%A0%94%E5%8F%91%20%E6%88%BF", got)
        assertFalse(got.contains("/api/api/"))
    }

    @Test
    fun resolveUrlDeduplicatesRootApiPath() {
        val got = SyncEndpointUrls.resolveUrl(
            serverBase = "https://example.com/api",
            targetUrl = "/api/file/u/name.png?token=abc#preview",
        )

        assertEquals("https://example.com/api/file/u/name.png?token=abc#preview", got)
        assertFalse(got.contains("/api/api/"))
    }

    @Test
    fun resolveUrlDeduplicatesRelativeApiPath() {
        val got = SyncEndpointUrls.resolveUrl(
            serverBase = "https://example.com/api",
            targetUrl = "api/file/u/name.png",
        )

        assertEquals("https://example.com/api/file/u/name.png", got)
        assertFalse(got.contains("/api/api/"))
    }

    @Test
    fun resolveUrlKeepsAbsoluteTarget() {
        val got = SyncEndpointUrls.resolveUrl(
            serverBase = "https://example.com/api",
            targetUrl = "https://cdn.example.com/api/file/u/name.png?download=true",
        )

        assertEquals("https://cdn.example.com/api/file/u/name.png?download=true", got)
    }

    @Test
    fun webSocketUrlUsesWsScheme() {
        val got = SyncEndpointUrls.webSocketUrl(
            serverBase = "http://127.0.0.1:9501/api",
            path = "api/sync/ws",
            query = mapOf("room" to "default", "deviceId" to "android-1"),
        )

        assertEquals("ws://127.0.0.1:9501/api/sync/ws?room=default&deviceId=android-1", got)
    }
}
