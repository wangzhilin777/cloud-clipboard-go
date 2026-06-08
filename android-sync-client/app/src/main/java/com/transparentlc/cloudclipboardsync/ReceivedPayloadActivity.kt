package com.transparentlc.cloudclipboardsync

import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.transparentlc.cloudclipboardsync.sync.PayloadCacheStore
import com.transparentlc.cloudclipboardsync.sync.PayloadEntry
import com.transparentlc.cloudclipboardsync.sync.SyncService
import java.io.File
import java.text.DateFormat
import android.content.BroadcastReceiver
import android.content.ActivityNotFoundException
import android.content.Context

class ReceivedPayloadActivity : AppCompatActivity() {
    enum class FilterMode {
        ALL,
        PENDING,
        PROCESSED,
        SNOOZED,
    }

    private lateinit var titleText: TextView
    private lateinit var collapsedSummaryText: TextView
    private lateinit var headerDetailGroup: View
    private lateinit var headerToggleButton: Button
    private lateinit var metaText: TextView
    private lateinit var originText: TextView
    private lateinit var expiryText: TextView
    private lateinit var statusText: TextView
    private lateinit var indexText: TextView
    private lateinit var actionHintText: TextView
    private lateinit var imagePreview: ImageView
    private lateinit var downloadButton: Button
    private lateinit var openButton: Button
    private lateinit var shareButton: Button
    private lateinit var saveButton: Button
    private lateinit var markProcessedButton: Button
    private lateinit var clearProcessedButton: Button
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button
    private lateinit var filterAllButton: Button
    private lateinit var filterPendingButton: Button
    private lateinit var filterProcessedButton: Button
    private lateinit var filterSnoozedButton: Button

    private var currentPayloadId: String? = null
    private var pendingSaveEntry: PayloadEntry? = null
    private var entries: List<PayloadEntry> = emptyList()
    private var filterMode = FilterMode.ALL
    private var headerExpanded = false

    private val payloadUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val payloadId = intent?.getStringExtra(SyncService.EXTRA_PAYLOAD_ID) ?: return
            entries = PayloadCacheStore.list(this@ReceivedPayloadActivity)
            if (currentPayloadId == null || currentPayloadId == payloadId) {
                currentPayloadId = payloadId
                bindEntry(PayloadCacheStore.get(this@ReceivedPayloadActivity, payloadId))
                return
            }
            bindEntry(currentEntry())
        }
    }

    private val saveFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        val entry = pendingSaveEntry ?: return@registerForActivityResult
        pendingSaveEntry = null
        if (uri != null) {
            if (!copyToUri(entry, uri)) {
                Toast.makeText(this, R.string.payload_save_failed_toast, Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            PayloadCacheStore.markProcessed(this, entry.payloadId)
            notifyPayloadCollectionChanged(entry.payloadId)
            moveAfterHandling(entry.payloadId)
            Toast.makeText(this, R.string.payload_save_success_toast, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_received_payload)

        titleText = findViewById(R.id.payloadTitleText)
        collapsedSummaryText = findViewById(R.id.payloadCollapsedSummaryText)
        headerDetailGroup = findViewById(R.id.payloadHeaderDetailGroup)
        headerToggleButton = findViewById(R.id.payloadHeaderToggleButton)
        metaText = findViewById(R.id.payloadMetaText)
        originText = findViewById(R.id.payloadOriginText)
        expiryText = findViewById(R.id.payloadExpiryText)
        statusText = findViewById(R.id.payloadStatusText)
        indexText = findViewById(R.id.payloadIndexText)
        actionHintText = findViewById(R.id.payloadActionHintText)
        imagePreview = findViewById(R.id.payloadImagePreview)
        downloadButton = findViewById(R.id.downloadButton)
        openButton = findViewById(R.id.openButton)
        shareButton = findViewById(R.id.shareButton)
        saveButton = findViewById(R.id.saveButton)
        markProcessedButton = findViewById(R.id.markProcessedButton)
        clearProcessedButton = findViewById(R.id.clearProcessedButton)
        previousButton = findViewById(R.id.previousButton)
        nextButton = findViewById(R.id.nextButton)
        filterAllButton = findViewById(R.id.filterAllButton)
        filterPendingButton = findViewById(R.id.filterPendingButton)
        filterProcessedButton = findViewById(R.id.filterProcessedButton)
        filterSnoozedButton = findViewById(R.id.filterSnoozedButton)
        headerToggleButton.setOnClickListener {
            headerExpanded = !headerExpanded
            syncHeaderExpansion()
        }

        entries = PayloadCacheStore.list(this)
        currentPayloadId = intent.getStringExtra(SyncService.EXTRA_PAYLOAD_ID)
        if (currentPayloadId == null) {
            currentPayloadId = entries.firstOrNull()?.payloadId
        }

        downloadButton.setOnClickListener {
            currentPayloadId?.let { payloadId -> SyncService.confirmPayload(this, payloadId) }
        }
        openButton.setOnClickListener {
            currentEntry()?.let(::openFile)
        }
        shareButton.setOnClickListener {
            currentEntry()?.let(::shareFile)
        }
        saveButton.setOnClickListener {
            currentEntry()?.let { entry ->
                pendingSaveEntry = entry
                saveFileLauncher.launch(entry.title)
            }
        }
        markProcessedButton.setOnClickListener {
            currentPayloadId?.let { payloadId ->
                PayloadCacheStore.markProcessed(this, payloadId)
                notifyPayloadCollectionChanged(payloadId)
                moveAfterHandling(payloadId)
            }
        }
        clearProcessedButton.setOnClickListener { clearProcessedEntries() }
        findViewById<Button>(R.id.restoreSnoozedButton).setOnClickListener { restoreSnoozedEntries() }
        previousButton.setOnClickListener { showRelativeEntry(-1) }
        nextButton.setOnClickListener { showRelativeEntry(1) }
        filterAllButton.setOnClickListener { switchFilter(FilterMode.ALL) }
        filterPendingButton.setOnClickListener { switchFilter(FilterMode.PENDING) }
        filterProcessedButton.setOnClickListener { switchFilter(FilterMode.PROCESSED) }
        filterSnoozedButton.setOnClickListener { switchFilter(FilterMode.SNOOZED) }
        handlePayloadIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        PayloadCacheStore.pruneExpired(this)
        entries = PayloadCacheStore.list(this)
        if (currentPayloadId == null) {
            currentPayloadId = entries.firstOrNull()?.payloadId
        }
        bindEntry(currentEntry())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePayloadIntent(intent)
        bindEntry(currentEntry())
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            payloadUpdateReceiver,
            IntentFilter(SyncService.ACTION_PAYLOAD_UPDATED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(payloadUpdateReceiver)
    }

    private fun currentEntry(): PayloadEntry? = currentPayloadId?.let { PayloadCacheStore.get(this, it) }

    private fun filteredEntries(): List<PayloadEntry> = entries.filter { entry ->
        when (filterMode) {
            FilterMode.ALL -> true
            FilterMode.PENDING -> entry.processedAt == null
            FilterMode.PROCESSED -> entry.processedAt != null
            FilterMode.SNOOZED -> PayloadCacheStore.isSnoozed(entry)
        }
    }

    private fun switchFilter(mode: FilterMode) {
        filterMode = mode
        entries = PayloadCacheStore.list(this)
        currentPayloadId = filteredEntries().firstOrNull()?.payloadId
        bindEntry(currentEntry())
    }

    private fun handlePayloadIntent(intent: Intent?) {
        filterMode = intent?.getStringExtra(EXTRA_FILTER_MODE)
            ?.let { value -> FilterMode.entries.firstOrNull { it.name == value } }
            ?: filterMode
        val payloadId = intent?.getStringExtra(SyncService.EXTRA_PAYLOAD_ID) ?: return
        currentPayloadId = payloadId
        PayloadCacheStore.clearSnooze(this, payloadId)
        entries = PayloadCacheStore.list(this)
    }

    private fun showRelativeEntry(offset: Int) {
        val filtered = filteredEntries()
        if (filtered.isEmpty()) return
        val currentIndex = filtered.indexOfFirst { it.payloadId == currentPayloadId }.takeIf { it >= 0 } ?: 0
        val nextIndex = (currentIndex + offset).coerceIn(0, filtered.lastIndex)
        currentPayloadId = filtered[nextIndex].payloadId
        bindEntry(filtered[nextIndex])
    }

    private fun bindEntry(entry: PayloadEntry?) {
        entries = PayloadCacheStore.list(this)
        val filtered = filteredEntries()
        syncFilterButtons()
        syncHeaderExpansion()
        clearProcessedButton.isEnabled = entries.any { it.processedAt != null }
        findViewById<Button>(R.id.restoreSnoozedButton).isEnabled = entries.any { PayloadCacheStore.isSnoozed(it) }
        val targetEntry = entry?.takeIf { filtered.any { item -> item.payloadId == it.payloadId } } ?: filtered.firstOrNull()
        if (targetEntry == null) {
            currentPayloadId = null
            titleText.text = getString(R.string.payload_empty_title)
            collapsedSummaryText.text = getString(
                R.string.payload_collapsed_summary_format,
                getString(R.string.payload_kind_unknown),
                getString(R.string.payload_status_pending),
                getString(R.string.payload_empty_text),
            )
            metaText.text = getString(R.string.payload_empty_text)
            originText.text = ""
            expiryText.text = getString(R.string.payload_cache_empty_hint)
            statusText.text = getString(R.string.payload_empty_text)
            indexText.text = getString(R.string.payload_index_format, 0, filtered.size)
            actionHintText.text = getString(R.string.payload_action_hint_empty)
            imagePreview.visibility = View.GONE
            downloadButton.isEnabled = false
            openButton.isEnabled = false
            shareButton.isEnabled = false
            saveButton.isEnabled = false
            markProcessedButton.isEnabled = false
            previousButton.isEnabled = false
            nextButton.isEnabled = false
            return
        }
        currentPayloadId = targetEntry.payloadId
        val localFile = targetEntry.localPath?.let(::File)
        val fileReady = localFile?.exists() == true
        titleText.text = targetEntry.title
        metaText.text = buildMeta(targetEntry)
        collapsedSummaryText.text = getString(
            R.string.payload_collapsed_summary_format,
            describeKind(targetEntry.kind),
            buildStatus(targetEntry),
            if (fileReady) getString(R.string.payload_status_cached) else getString(R.string.payload_status_not_downloaded),
        )
        originText.text = getString(
            R.string.payload_origin_format,
            targetEntry.sourceDeviceId.ifBlank { "-" },
            targetEntry.room.ifBlank { "默认房间" },
        )
        expiryText.text = getString(
            R.string.payload_cache_expires_format,
            DateFormat.getDateTimeInstance().format(targetEntry.expiresAt),
        )
        statusText.text = buildStatus(targetEntry)
        val currentIndex = filtered.indexOfFirst { it.payloadId == targetEntry.payloadId }.takeIf { it >= 0 } ?: 0
        indexText.text = getString(R.string.payload_index_format, currentIndex + 1, filtered.size.coerceAtLeast(1))
        actionHintText.text = buildActionHint(targetEntry)
        downloadButton.isEnabled = true
        downloadButton.text = if (fileReady) getString(R.string.payload_redownload_button) else getString(R.string.payload_download_button)
        openButton.isEnabled = fileReady
        shareButton.isEnabled = fileReady
        saveButton.isEnabled = fileReady
        val processed = targetEntry.processedAt != null
        markProcessedButton.isEnabled = !processed
        markProcessedButton.text = if (processed) {
            getString(R.string.payload_mark_processed_done_button)
        } else {
            getString(R.string.payload_mark_processed_button)
        }
        previousButton.isEnabled = currentIndex > 0
        nextButton.isEnabled = currentIndex < filtered.lastIndex

        if (targetEntry.isImage && fileReady) {
            val bitmap = runCatching { BitmapFactory.decodeFile(localFile?.absolutePath) }.getOrNull()
            if (bitmap != null) {
                imagePreview.visibility = View.VISIBLE
                imagePreview.setImageBitmap(bitmap)
            } else {
                imagePreview.visibility = View.GONE
                imagePreview.setImageDrawable(null)
            }
        } else {
            imagePreview.visibility = View.GONE
            imagePreview.setImageDrawable(null)
        }
    }

    private fun syncHeaderExpansion() {
        headerDetailGroup.visibility = if (headerExpanded) View.VISIBLE else View.GONE
        collapsedSummaryText.visibility = if (headerExpanded) View.GONE else View.VISIBLE
        headerToggleButton.text = getString(
            if (headerExpanded) R.string.home_collapse_button else R.string.home_expand_button,
        )
    }

    private fun buildMeta(entry: PayloadEntry): String {
        val created = DateFormat.getDateTimeInstance().format(entry.createdAt)
        val size = if (entry.size > 0) "${entry.size} B" else getString(R.string.payload_size_unknown)
        return getString(R.string.payload_meta_format, describeKind(entry.kind), entry.mime, size, created)
    }

    private fun buildStatus(entry: PayloadEntry): String {
        val processed = if (entry.processedAt != null) getString(R.string.payload_status_processed) else getString(R.string.payload_status_pending)
        val cacheState = if (entry.localPath?.let(::File)?.exists() == true) getString(R.string.payload_status_cached) else getString(R.string.payload_status_not_downloaded)
        val snoozeState = if (PayloadCacheStore.isSnoozed(entry)) {
            " / ${getString(R.string.payload_status_snoozed)}"
        } else {
            ""
        }
        return "$processed / $cacheState$snoozeState"
    }

    private fun buildActionHint(entry: PayloadEntry): String = when {
        entry.localPath?.let(::File)?.exists() != true -> getString(R.string.payload_action_hint_download)
        entry.processedAt == null && entry.isImage -> getString(R.string.payload_action_hint_image_ready)
        entry.processedAt == null -> getString(R.string.payload_action_hint_file_ready)
        else -> getString(R.string.payload_action_hint_processed)
    }

    private fun moveAfterHandling(handledPayloadId: String) {
        entries = PayloadCacheStore.list(this)
        val filtered = filteredEntries()
        if (filtered.isEmpty()) {
            currentPayloadId = null
            bindEntry(null)
            return
        }
        val handledIndex = filtered.indexOfFirst { it.payloadId == handledPayloadId }
        if (handledIndex == -1) {
            currentPayloadId = filtered.firstOrNull()?.payloadId
            bindEntry(currentEntry())
            return
        }
        val nextEntry = filtered.getOrNull(handledIndex + 1)
        val previousEntry = filtered.getOrNull(handledIndex - 1)
        currentPayloadId = nextEntry?.payloadId ?: previousEntry?.payloadId
        bindEntry(currentEntry())
    }

    private fun clearProcessedEntries() {
        val removed = PayloadCacheStore.clearProcessed(this)
        if (removed <= 0) {
            Toast.makeText(this, R.string.payload_clear_processed_empty_toast, Toast.LENGTH_SHORT).show()
            return
        }
        entries = PayloadCacheStore.list(this)
        currentPayloadId = filteredEntries().firstOrNull()?.payloadId
        notifyPayloadCollectionChanged(currentPayloadId)
        bindEntry(currentEntry())
        Toast.makeText(this, getString(R.string.payload_clear_processed_toast, removed), Toast.LENGTH_SHORT).show()
    }

    private fun restoreSnoozedEntries() {
        val restored = PayloadCacheStore.clearSnoozed(this)
        if (restored <= 0) {
            Toast.makeText(this, R.string.payload_restore_snoozed_empty_toast, Toast.LENGTH_SHORT).show()
            return
        }
        entries = PayloadCacheStore.list(this)
        currentPayloadId = filteredEntries().firstOrNull()?.payloadId
        notifyPayloadCollectionChanged(currentPayloadId)
        bindEntry(currentEntry())
        Toast.makeText(this, getString(R.string.payload_restore_snoozed_toast, restored), Toast.LENGTH_SHORT).show()
    }

    private fun syncFilterButtons() {
        val buttons = listOf(
            filterAllButton to (filterMode == FilterMode.ALL),
            filterPendingButton to (filterMode == FilterMode.PENDING),
            filterProcessedButton to (filterMode == FilterMode.PROCESSED),
            filterSnoozedButton to (filterMode == FilterMode.SNOOZED),
        )
        buttons.forEach { (button, selected) ->
            button.alpha = if (selected) 1f else 0.72f
        }
    }

    private fun openFile(entry: PayloadEntry) {
        val uri = entry.localPath?.let(::File)?.takeIf { it.exists() }?.let(::buildFileUri)
        if (uri == null) {
            Toast.makeText(this, R.string.payload_local_file_missing_toast, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, entry.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.payload_open_no_app_toast, Toast.LENGTH_SHORT).show()
            return
        } catch (_: Exception) {
            Toast.makeText(this, R.string.payload_open_failed_toast, Toast.LENGTH_SHORT).show()
            return
        }
        PayloadCacheStore.markProcessed(this, entry.payloadId)
        notifyPayloadCollectionChanged(entry.payloadId)
        moveAfterHandling(entry.payloadId)
    }

    private fun shareFile(entry: PayloadEntry) {
        val uri = entry.localPath?.let(::File)?.takeIf { it.exists() }?.let(::buildFileUri)
        if (uri == null) {
            Toast.makeText(this, R.string.payload_local_file_missing_toast, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType(entry.mime)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            getString(R.string.payload_share_button),
        )
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.payload_share_no_app_toast, Toast.LENGTH_SHORT).show()
            return
        } catch (_: Exception) {
            Toast.makeText(this, R.string.payload_share_failed_toast, Toast.LENGTH_SHORT).show()
            return
        }
        PayloadCacheStore.markProcessed(this, entry.payloadId)
        notifyPayloadCollectionChanged(entry.payloadId)
        moveAfterHandling(entry.payloadId)
    }

    private fun copyToUri(entry: PayloadEntry, uri: Uri): Boolean {
        val file = entry.localPath?.let(::File)?.takeIf { it.exists() } ?: return false
        return runCatching {
            contentResolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: return false
            true
        }.getOrDefault(false)
    }

    private fun notifyPayloadCollectionChanged(payloadId: String?) {
        sendBroadcast(Intent(SyncService.ACTION_PAYLOAD_UPDATED).apply {
            setPackage(packageName)
            payloadId?.let { putExtra(SyncService.EXTRA_PAYLOAD_ID, it) }
        })
    }

    private fun buildFileUri(file: File): Uri = FileProvider.getUriForFile(
        this,
        "$packageName.fileprovider",
        file,
    )

    private fun describeKind(kind: String): String = when (kind) {
        "image" -> getString(R.string.payload_kind_image)
        "file" -> getString(R.string.payload_kind_file)
        else -> getString(R.string.payload_kind_unknown)
    }

    companion object {
        private const val EXTRA_FILTER_MODE = "extra_filter_mode"

        fun createIntent(context: Context, filterMode: FilterMode): Intent = Intent(context, ReceivedPayloadActivity::class.java)
            .putExtra(EXTRA_FILTER_MODE, filterMode.name)
    }
}
