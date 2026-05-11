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
import android.content.Context

class ReceivedPayloadActivity : AppCompatActivity() {
    private lateinit var titleText: TextView
    private lateinit var metaText: TextView
    private lateinit var statusText: TextView
    private lateinit var indexText: TextView
    private lateinit var imagePreview: ImageView
    private lateinit var downloadButton: Button
    private lateinit var openButton: Button
    private lateinit var shareButton: Button
    private lateinit var saveButton: Button
    private lateinit var markProcessedButton: Button
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button

    private var currentPayloadId: String? = null
    private var pendingSaveEntry: PayloadEntry? = null
    private var entries: List<PayloadEntry> = emptyList()

    private val payloadUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val payloadId = intent?.getStringExtra(SyncService.EXTRA_PAYLOAD_ID) ?: return
            if (currentPayloadId == payloadId) {
                bindEntry(PayloadCacheStore.get(this@ReceivedPayloadActivity, payloadId))
            }
        }
    }

    private val saveFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        val entry = pendingSaveEntry ?: return@registerForActivityResult
        if (uri != null) {
            copyToUri(entry, uri)
            PayloadCacheStore.markProcessed(this, entry.payloadId)
            bindEntry(PayloadCacheStore.get(this, entry.payloadId))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_received_payload)

        titleText = findViewById(R.id.payloadTitleText)
        metaText = findViewById(R.id.payloadMetaText)
        statusText = findViewById(R.id.payloadStatusText)
        indexText = findViewById(R.id.payloadIndexText)
        imagePreview = findViewById(R.id.payloadImagePreview)
        downloadButton = findViewById(R.id.downloadButton)
        openButton = findViewById(R.id.openButton)
        shareButton = findViewById(R.id.shareButton)
        saveButton = findViewById(R.id.saveButton)
        markProcessedButton = findViewById(R.id.markProcessedButton)
        previousButton = findViewById(R.id.previousButton)
        nextButton = findViewById(R.id.nextButton)

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
                bindEntry(PayloadCacheStore.get(this, payloadId))
            }
        }
        previousButton.setOnClickListener { showRelativeEntry(-1) }
        nextButton.setOnClickListener { showRelativeEntry(1) }
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

    private fun showRelativeEntry(offset: Int) {
        if (entries.isEmpty()) return
        val currentIndex = entries.indexOfFirst { it.payloadId == currentPayloadId }.takeIf { it >= 0 } ?: 0
        val nextIndex = (currentIndex + offset).coerceIn(0, entries.lastIndex)
        currentPayloadId = entries[nextIndex].payloadId
        bindEntry(entries[nextIndex])
    }

    private fun bindEntry(entry: PayloadEntry?) {
        entries = PayloadCacheStore.list(this)
        if (entry == null) {
            titleText.text = getString(R.string.payload_empty_title)
            metaText.text = getString(R.string.payload_empty_text)
            statusText.text = getString(R.string.payload_empty_text)
            indexText.text = getString(R.string.payload_index_format, 0, 0)
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
        currentPayloadId = entry.payloadId
        titleText.text = entry.title
        metaText.text = buildMeta(entry)
        statusText.text = buildStatus(entry)
        val currentIndex = entries.indexOfFirst { it.payloadId == entry.payloadId }.takeIf { it >= 0 } ?: 0
        indexText.text = getString(R.string.payload_index_format, currentIndex + 1, entries.size.coerceAtLeast(1))
        downloadButton.isEnabled = true
        downloadButton.text = if (entry.isDownloaded) getString(R.string.payload_redownload_button) else getString(R.string.payload_download_button)
        openButton.isEnabled = entry.isDownloaded
        shareButton.isEnabled = entry.isDownloaded
        saveButton.isEnabled = entry.isDownloaded
        markProcessedButton.isEnabled = true
        previousButton.isEnabled = currentIndex > 0
        nextButton.isEnabled = currentIndex < entries.lastIndex

        val file = entry.localPath?.let(::File)
        if (entry.isImage && file?.exists() == true) {
            imagePreview.visibility = View.VISIBLE
            imagePreview.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
        } else {
            imagePreview.visibility = View.GONE
            imagePreview.setImageDrawable(null)
        }
    }

    private fun buildMeta(entry: PayloadEntry): String {
        val created = DateFormat.getDateTimeInstance().format(entry.createdAt)
        val size = if (entry.size > 0) "${entry.size} B" else getString(R.string.payload_size_unknown)
        return getString(R.string.payload_meta_format, describeKind(entry.kind), entry.mime, size, created)
    }

    private fun buildStatus(entry: PayloadEntry): String {
        val processed = if (entry.processedAt != null) getString(R.string.payload_status_processed) else getString(R.string.payload_status_pending)
        val cacheState = if (entry.isDownloaded) getString(R.string.payload_status_cached) else getString(R.string.payload_status_not_downloaded)
        return "$processed / $cacheState"
    }

    private fun openFile(entry: PayloadEntry) {
        val uri = entry.localPath?.let(::File)?.takeIf { it.exists() }?.let(::buildFileUri) ?: return
        PayloadCacheStore.markProcessed(this, entry.payloadId)
        startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, entry.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    private fun shareFile(entry: PayloadEntry) {
        val uri = entry.localPath?.let(::File)?.takeIf { it.exists() }?.let(::buildFileUri) ?: return
        PayloadCacheStore.markProcessed(this, entry.payloadId)
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType(entry.mime)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                getString(R.string.payload_share_button),
            ),
        )
    }

    private fun copyToUri(entry: PayloadEntry, uri: Uri) {
        val file = entry.localPath?.let(::File)?.takeIf { it.exists() } ?: return
        contentResolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        }
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
}
