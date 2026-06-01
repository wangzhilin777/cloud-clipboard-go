package com.transparentlc.cloudclipboardsync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.transparentlc.cloudclipboardsync.sync.SettingsStore
import com.transparentlc.cloudclipboardsync.sync.ShareUploadClient

class ShareReceiveActivity : AppCompatActivity() {
    private lateinit var titleText: TextView
    private lateinit var summaryText: TextView
    private lateinit var detailText: TextView
    private lateinit var sendButton: Button
    private lateinit var cancelButton: Button
    private lateinit var openAppButton: Button
    private lateinit var progressText: TextView

    private var sharedText: String? = null
    private var sharedItems: List<ShareUploadClient.SharedItem> = emptyList()
    private var sending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_receive)

        titleText = findViewById(R.id.shareTitleText)
        summaryText = findViewById(R.id.shareSummaryText)
        detailText = findViewById(R.id.shareDetailText)
        sendButton = findViewById(R.id.shareSendButton)
        cancelButton = findViewById(R.id.shareCancelButton)
        openAppButton = findViewById(R.id.shareOpenAppButton)
        progressText = findViewById(R.id.shareProgressText)

        loadSharedContent(intent)
        renderDraft()

        sendButton.setOnClickListener { sendNow() }
        cancelButton.setOnClickListener { finish() }
        openAppButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadSharedContent(intent)
        renderDraft()
    }

    private fun loadSharedContent(intent: Intent?) {
        sharedText = null
        sharedItems = emptyList()
        val action = intent?.action.orEmpty()
        when (action) {
            Intent.ACTION_SEND -> {
                val text = intent?.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
                val singleUri = intent?.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                if (!singleUri.isNullOrBlank()) {
                    val resolvedUri = requireNotNull(singleUri)
                    grantUriPermission(packageName, resolvedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    sharedItems = ShareUploadClient.resolveSharedItems(this, listOf(resolvedUri))
                } else if (text.isNotBlank()) {
                    sharedText = text
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris: List<Uri> = intent
                    ?.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    ?.mapNotNull { it }
                    ?: emptyList()
                uris.forEach { uri ->
                    grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                sharedItems = ShareUploadClient.resolveSharedItems(this, uris)
            }
        }
    }

    private fun Uri?.isNullOrBlank(): Boolean = this == null

    private fun renderDraft() {
        val config = SettingsStore.load(this)
        val roomLabel = config.room.ifBlank { getString(R.string.default_room_label) }
        when {
            sharedItems.isNotEmpty() -> {
                val imageCount = sharedItems.count { it.kind == "image" }
                val fileCount = sharedItems.size - imageCount
                titleText.text = getString(R.string.share_receive_title_files)
                summaryText.text = getString(
                    R.string.share_receive_summary_files,
                    sharedItems.size,
                    roomLabel,
                )
                detailText.text = buildString {
                    append("目标房间：")
                    append(roomLabel)
                    append("\n")
                    append("发送内容：")
                    append(sharedItems.joinToString("\n") { item ->
                        val sizeText = if (item.size > 0) " · ${formatBytes(item.size)}" else ""
                        "${item.name}$sizeText"
                    })
                    if (imageCount > 0 || fileCount > 0) {
                        append("\n\n")
                        append("图片 ")
                        append(imageCount)
                        append(" 项 · 文件 ")
                        append(fileCount)
                        append(" 项")
                    }
                }
            }
            !sharedText.isNullOrBlank() -> {
                titleText.text = getString(R.string.share_receive_title_text)
                summaryText.text = getString(R.string.share_receive_summary_text, roomLabel)
                detailText.text = buildString {
                    append("目标房间：")
                    append(roomLabel)
                    append("\n\n")
                    append(sharedText?.take(400).orEmpty())
                }
            }
            else -> {
                titleText.text = getString(R.string.share_receive_title_empty)
                summaryText.text = getString(R.string.share_receive_summary_empty)
                detailText.text = getString(R.string.share_receive_detail_empty)
                sendButton.isEnabled = false
            }
        }
        updateSendingState()
    }

    private fun sendNow() {
        if (sending) return
        val config = SettingsStore.load(this)
        if (config.serverBase.isBlank()) {
            Toast.makeText(this, R.string.share_receive_missing_config, Toast.LENGTH_LONG).show()
            return
        }
        sending = true
        updateSendingState()
        Thread {
            runCatching {
                when {
                    sharedItems.isNotEmpty() -> ShareUploadClient.shareFiles(this, config, sharedItems)
                    !sharedText.isNullOrBlank() -> ShareUploadClient.shareText(config, sharedText.orEmpty())
                    else -> error("没有可发送的内容")
                }
            }.onSuccess { result ->
                runOnUiThread {
                    Toast.makeText(this, result.resultMessage, Toast.LENGTH_LONG).show()
                    finish()
                }
            }.onFailure { error ->
                runOnUiThread {
                    sending = false
                    updateSendingState(error.message ?: getString(R.string.share_receive_failed))
                    Toast.makeText(this, error.message ?: getString(R.string.share_receive_failed), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun updateSendingState(errorMessage: String? = null) {
        sendButton.isEnabled = !sending && (sharedItems.isNotEmpty() || !sharedText.isNullOrBlank())
        cancelButton.isEnabled = !sending
        progressText.visibility = if (sending || !errorMessage.isNullOrBlank()) View.VISIBLE else View.GONE
        progressText.text = when {
            sending -> getString(R.string.share_receive_sending)
            !errorMessage.isNullOrBlank() -> errorMessage
            else -> ""
        }
    }

    private fun formatBytes(value: Long): String = when {
        value >= 1024L * 1024L * 1024L -> getString(R.string.receive_cache_size_gb, value / (1024f * 1024f * 1024f))
        value >= 1024L * 1024L -> getString(R.string.receive_cache_size_mb, value / (1024f * 1024f))
        value >= 1024L -> getString(R.string.receive_cache_size_kb, value / 1024f)
        else -> getString(R.string.receive_cache_size_bytes, value)
    }
}
