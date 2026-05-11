package com.transparentlc.cloudclipboardsync.sync

import android.content.Context
import org.json.JSONArray
import java.io.File

object PayloadCacheStore {
    const val DEFAULT_RETENTION_MS = 24 * 60 * 60 * 1000L

    private const val PREFS_NAME = "cloud_clipboard_payloads"
    private const val KEY_PAYLOADS = "payloads"
    private const val CACHE_DIR = "received-payloads"

    fun list(context: Context): List<PayloadEntry> = loadEntries(context)
        .sortedByDescending { maxOf(it.downloadedAt ?: 0L, it.createdAt) }

    fun get(context: Context, payloadId: String): PayloadEntry? = loadEntries(context)
        .firstOrNull { it.payloadId == payloadId }

    fun upsertNotice(context: Context, notice: PayloadNotice): PayloadEntry {
        pruneExpired(context)
        val entries = loadEntries(context).toMutableList()
        val index = entries.indexOfFirst { it.payloadId == notice.payloadId }
        val existing = entries.getOrNull(index)
        val updated = PayloadEntry(
            payloadId = notice.payloadId,
            sourceDeviceId = notice.sourceDeviceId,
            room = notice.room,
            kind = notice.kind,
            title = notice.title,
            mime = notice.mime,
            size = notice.size,
            actionUrl = notice.actionUrl,
            downloadUrl = notice.downloadUrl,
            createdAt = notice.createdAt,
            localPath = existing?.localPath,
            downloadedAt = existing?.downloadedAt,
            expiresAt = existing?.expiresAt ?: (notice.createdAt + DEFAULT_RETENTION_MS),
            processedAt = existing?.processedAt,
        )
        if (index >= 0) {
            entries[index] = updated
        } else {
            entries.add(updated)
        }
        saveEntries(context, entries)
        return updated
    }

    fun markDownloaded(context: Context, payloadId: String, localPath: String, size: Long? = null, mime: String? = null): PayloadEntry? {
        val entries = loadEntries(context).toMutableList()
        val index = entries.indexOfFirst { it.payloadId == payloadId }
        if (index == -1) return null
        val updated = entries[index].copy(
            localPath = localPath,
            size = size ?: entries[index].size,
            mime = mime ?: entries[index].mime,
            downloadedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + DEFAULT_RETENTION_MS,
        )
        entries[index] = updated
        saveEntries(context, entries)
        return updated
    }

    fun markProcessed(context: Context, payloadId: String): PayloadEntry? {
        val entries = loadEntries(context).toMutableList()
        val index = entries.indexOfFirst { it.payloadId == payloadId }
        if (index == -1) return null
        val updated = entries[index].copy(processedAt = System.currentTimeMillis())
        entries[index] = updated
        saveEntries(context, entries)
        return updated
    }

    fun pruneExpired(context: Context) {
        val now = System.currentTimeMillis()
        val entries = loadEntries(context)
        val kept = mutableListOf<PayloadEntry>()
        entries.forEach { entry ->
            val file = entry.localPath?.let(::File)
            val expired = entry.expiresAt <= now
            val missingFile = entry.localPath != null && (file == null || !file.exists())
            if (expired || missingFile) {
                if (file?.exists() == true) {
                    file.delete()
                }
            } else {
                kept.add(entry)
            }
        }
        if (kept.size != entries.size) {
            saveEntries(context, kept)
        }
        cacheDir(context).mkdirs()
    }

    fun createCacheFile(context: Context, payloadId: String, title: String): File {
        val dir = cacheDir(context)
        dir.mkdirs()
        val safeName = title
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { payloadId }
        return File(dir, "${payloadId}_$safeName")
    }

    private fun cacheDir(context: Context): File = File(context.cacheDir, CACHE_DIR)

    private fun loadEntries(context: Context): List<PayloadEntry> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PAYLOADS, "[]")
            ?: "[]"
        val array = JSONArray(raw)
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                add(PayloadEntry.fromJson(array.getJSONObject(index)))
            }
        }
    }

    private fun saveEntries(context: Context, entries: List<PayloadEntry>) {
        val array = JSONArray()
        entries.forEach { array.put(it.toJson()) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PAYLOADS, array.toString())
            .apply()
    }
}
