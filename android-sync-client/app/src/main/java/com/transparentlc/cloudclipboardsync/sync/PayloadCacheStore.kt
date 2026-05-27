package com.transparentlc.cloudclipboardsync.sync

import android.content.Context
import org.json.JSONArray
import java.io.File

object PayloadCacheStore {
    const val DEFAULT_RETENTION_MS = 24 * 60 * 60 * 1000L

    private const val PREFS_NAME = "cloud_clipboard_payloads"
    private const val KEY_PAYLOADS = "payloads"
    private const val CACHE_DIR = "received-payloads"

    data class Summary(
        val totalCount: Int,
        val pendingCount: Int,
        val processedCount: Int,
        val downloadedCount: Int,
        val snoozedCount: Int,
        val totalSizeBytes: Long,
    )

    fun list(context: Context): List<PayloadEntry> = loadEntries(context)
        .sortedByDescending { maxOf(it.downloadedAt ?: 0L, it.createdAt) }

    fun get(context: Context, payloadId: String): PayloadEntry? = loadEntries(context)
        .firstOrNull { it.payloadId == payloadId }

    fun upsertNotice(context: Context, notice: PayloadNotice): PayloadEntry {
        pruneExpired(context)
        val entries = loadEntries(context).toMutableList()
        val index = entries.indexOfFirst { it.payloadId == notice.payloadId }
        val existing = entries.getOrNull(index)
        val retentionMs = retentionMs(context)
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
            expiresAt = existing?.expiresAt ?: (notice.createdAt + retentionMs),
            processedAt = existing?.processedAt,
            snoozedUntil = existing?.snoozedUntil,
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
        val now = System.currentTimeMillis()
        val updated = entries[index].copy(
            localPath = localPath,
            size = size ?: entries[index].size,
            mime = mime ?: entries[index].mime,
            downloadedAt = now,
            expiresAt = now + retentionMs(context),
            snoozedUntil = null,
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

    fun markSnoozed(context: Context, payloadId: String, until: Long): PayloadEntry? {
        val entries = loadEntries(context).toMutableList()
        val index = entries.indexOfFirst { it.payloadId == payloadId }
        if (index == -1) return null
        val updated = entries[index].copy(snoozedUntil = until)
        entries[index] = updated
        saveEntries(context, entries)
        return updated
    }

    fun clearSnooze(context: Context, payloadId: String): PayloadEntry? {
        val entries = loadEntries(context).toMutableList()
        val index = entries.indexOfFirst { it.payloadId == payloadId }
        if (index == -1) return null
        if (entries[index].snoozedUntil == null) return entries[index]
        val updated = entries[index].copy(snoozedUntil = null)
        entries[index] = updated
        saveEntries(context, entries)
        return updated
    }

    fun isSnoozed(entry: PayloadEntry, now: Long = System.currentTimeMillis()): Boolean {
        val until = entry.snoozedUntil ?: return false
        return until > now
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

    fun clearAll(context: Context) {
        loadEntries(context).forEach { entry ->
            entry.localPath?.let(::File)?.takeIf { it.exists() }?.delete()
        }
        cacheDir(context).deleteRecursively()
        saveEntries(context, emptyList())
    }

    fun clearProcessed(context: Context): Int {
        val entries = loadEntries(context)
        var removed = 0
        val kept = entries.filterNot { entry ->
            val shouldRemove = entry.processedAt != null
            if (shouldRemove) {
                entry.localPath?.let(::File)?.takeIf { it.exists() }?.delete()
                removed++
            }
            shouldRemove
        }
        if (removed > 0) {
            saveEntries(context, kept)
        }
        return removed
    }

    fun clearSnoozed(context: Context): Int {
        val entries = loadEntries(context).toMutableList()
        var restored = 0
        entries.replaceAll { entry ->
            if (entry.snoozedUntil != null) {
                restored++
                entry.copy(snoozedUntil = null)
            } else {
                entry
            }
        }
        if (restored > 0) {
            saveEntries(context, entries)
        }
        return restored
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

    fun summary(context: Context): Summary {
        val entries = list(context)
        return Summary(
            totalCount = entries.size,
            pendingCount = entries.count { it.processedAt == null },
            processedCount = entries.count { it.processedAt != null },
            downloadedCount = entries.count { it.isDownloaded },
            snoozedCount = entries.count { isSnoozed(it) },
            totalSizeBytes = entries.filter { it.isDownloaded }.sumOf { it.size.coerceAtLeast(0L) },
        )
    }

    private fun cacheDir(context: Context): File = File(context.cacheDir, CACHE_DIR)

    private fun retentionMs(context: Context): Long {
        val hours = SettingsStore.load(context).cacheRetentionHours.coerceAtLeast(1)
        return hours * 60L * 60L * 1000L
    }

    private fun loadEntries(context: Context): List<PayloadEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PAYLOADS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    runCatching {
                        PayloadEntry.fromJson(array.getJSONObject(index))
                    }.getOrNull()?.let(::add)
                }
            }
        }.getOrElse {
            prefs.edit().remove(KEY_PAYLOADS).apply()
            emptyList()
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
