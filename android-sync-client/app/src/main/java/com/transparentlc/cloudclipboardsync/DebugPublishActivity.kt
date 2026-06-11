package com.transparentlc.cloudclipboardsync

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.transparentlc.cloudclipboardsync.sync.SyncService

class DebugPublishActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) {
            finish()
            return
        }
        val safeIntent = intent ?: run {
            finish()
            return
        }
        val text = safeIntent.getStringExtra(EXTRA_TEXT)?.trim().orEmpty()
        when (safeIntent.getStringExtra(EXTRA_ACTION)?.trim().orEmpty()) {
            ACTION_MANUAL_SEND -> {
                if (text.isNotBlank()) {
                    writeClipboard(text)
                    val route = safeIntent.getStringExtra(EXTRA_ROUTE)?.trim().orEmpty().ifBlank { "debug-manual-activity" }
                    SyncService.sendManualText(this, text, route)
                }
            }

            ACTION_SHOW_FLOATING -> {
                if (text.isNotBlank()) {
                    writeClipboard(text)
                }
                FloatingClipboardOverlayService.show(this)
            }

            else -> {
                if (text.isNotBlank()) {
                    ContextCompat.startForegroundService(
                        this,
                        Intent(this, SyncService::class.java)
                            .setAction(SyncService.ACTION_DEBUG_PUBLISH_TEXT)
                            .putExtra(SyncService.EXTRA_DEBUG_TEXT, text),
                    )
                }
            }
        }
        finish()
    }

    private fun writeClipboard(text: String) {
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText("cloud-clipboard-debug", text))
    }

    companion object {
        const val EXTRA_ACTION = "extra_action"
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_ROUTE = "extra_route"
        const val ACTION_MANUAL_SEND = "manual-send"
        const val ACTION_SHOW_FLOATING = "show-floating"
    }
}
