package com.transparentlc.cloudclipboardsync.sync

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID

class ClipboardSyncClient(
    private val config: SettingsStore.Config,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onConnected()
        fun onTrustedChanged(trusted: Boolean)
        fun onRemoteText(messageId: String, text: String)
        fun onPayloadNotice(notice: PayloadNotice)
        fun onLog(message: String)
        fun onForbidden()
        fun onDisconnected()
    }

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var trusted = false

    fun connect() {
        val wsUrl = SyncEndpointUrls.webSocketUrl(
            serverBase = config.serverBase,
            path = "sync/ws",
            query = mapOf(
                "room" to config.room,
                "auth" to config.roomPassword,
            ),
        )
        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                callbacks.onConnected()
                val hello = JSONObject()
                    .put("event", "hello")
                    .put("data", JSONObject()
                        .put("deviceId", config.deviceId)
                        .put("name", config.deviceName)
                        .put("room", config.room)
                        .put("platform", "android")
                        .put("clientType", "android-app"))
                webSocket.send(hello.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = JSONObject(text)
                when (event.getString("event")) {
                    "helloAck" -> {
                        val data = event.getJSONObject("data")
                        trusted = data.getJSONObject("device").optBoolean("trusted", false)
                        callbacks.onTrustedChanged(trusted)
                        if (trusted) {
                            emitLatestRecentMessage(data.optJSONArray("recentMessages"))
                        }
                        val payloads = data.optJSONArray("recentPayloads")
                        if (payloads != null) {
                            for (index in 0 until payloads.length()) {
                                callbacks.onPayloadNotice(PayloadNotice.fromJson(payloads.getJSONObject(index)))
                            }
                        }
                        callbacks.onLog(if (trusted) "安卓同步已连接" else "安卓设备等待批准")
                    }
                    "clipboardSync" -> {
                        val data = event.getJSONObject("data")
                        callbacks.onRemoteText(data.optString("messageId"), data.getString("text"))
                    }
                    "payloadNotice" -> {
                        callbacks.onPayloadNotice(PayloadNotice.fromJson(event.getJSONObject("data")))
                    }
                    "clipboardAck" -> {
                        callbacks.onLog("文本同步状态：${event.getJSONObject("data").optString("status")}")
                    }
                    "forbidden" -> callbacks.onForbidden()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                callbacks.onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                callbacks.onLog("同步连接失败：${t.message}")
                callbacks.onDisconnected()
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "bye")
        webSocket = null
    }

    fun refreshTrusted(trusted: Boolean) {
        this.trusted = trusted
        callbacks.onTrustedChanged(trusted)
    }

    fun publishText(text: String) {
        if (!trusted || text.isBlank()) return
        val payload = JSONObject()
            .put("event", "clipboardPublish")
            .put("data", JSONObject()
                .put("messageId", UUID.randomUUID().toString())
                .put("text", text)
                .put("createdAt", System.currentTimeMillis()))
        webSocket?.send(payload.toString())
    }

    private fun emitLatestRecentMessage(messages: org.json.JSONArray?) {
        if (messages == null) return
        for (index in messages.length() - 1 downTo 0) {
            val item = messages.optJSONObject(index) ?: continue
            val text = item.optString("text").trim()
            if (text.isBlank()) continue
            callbacks.onRemoteText(item.optString("messageId"), text)
            return
        }
    }
}
