package com.transparentlc.cloudclipboardsync

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.transparentlc.cloudclipboardsync.sync.SettingsStore
import com.transparentlc.cloudclipboardsync.sync.SyncService

class ClipboardInputMethodService : InputMethodService() {
    private val handler = Handler(Looper.getMainLooper())
    private var statusText: TextView? = null
    private var previewText: TextView? = null
    private var sendButton: Button? = null
    private val resetStatusRunnable = Runnable {
        refreshClipboardPreview()
        applyStatus(getString(R.string.clipboard_ime_idle), success = false)
    }

    private val clipboardManager by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        // ime_background 模式：输入法后台自动监听剪贴板变化并同步
        val config = SettingsStore.load(this)
        if (config.clipboardMode == SettingsStore.CLIPBOARD_MODE_IME_BACKGROUND) {
            if (SyncService.isRunning()) {
                // 通知 SyncService 处理新的剪贴板内容
                SyncService.notifyImeClipboardChanged(this)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // 注册剪贴板监听器，实现后台自动同步
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
    }

    override fun onCreateInputView(): View {
        val root = LayoutInflater.from(this).inflate(R.layout.view_clipboard_ime, null)
        statusText = root.findViewById(R.id.imeStatusText)
        previewText = root.findViewById(R.id.imePreviewText)
        sendButton = root.findViewById<Button>(R.id.imeSendButton)
        sendButton?.setOnClickListener { sendClipboardText() }
        root.findViewById<Button>(R.id.imeOpenAppButton).setOnClickListener {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            } else {
                showStatus(getString(R.string.clipboard_ime_open_failed_toast))
            }
        }
        refreshClipboardPreview()
        applyStatus(getString(R.string.clipboard_ime_idle), success = false)
        return root
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        handler.removeCallbacks(resetStatusRunnable)
        refreshClipboardPreview()
        applyStatus(getString(R.string.clipboard_ime_idle), success = false)
    }

    override fun onDestroy() {
        // 注销剪贴板监听器
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun sendClipboardText() {
        handler.removeCallbacks(resetStatusRunnable)
        refreshClipboardPreview()
        ManualClipboardSender.sendCurrentClipboardText(
            context = this,
            route = "ime-panel",
            onStatus = ::showStatus,
        )
    }

    private fun refreshClipboardPreview() {
        val preview = ManualClipboardSender.buildClipboardPreview(this, maxChars = 140)
        previewText?.text = preview.text
        sendButton?.isEnabled = !preview.empty
        sendButton?.alpha = if (preview.empty) 0.6f else 1f
    }

    private fun showStatus(message: String) {
        val isSent = message == getString(R.string.clipboard_ime_sent)
        applyStatus(message, success = isSent)
        if (isSent) {
            handler.postDelayed(resetStatusRunnable, 2200L)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun applyStatus(message: String, success: Boolean) {
        statusText?.let {
            it.text = message
            val background = if (success) R.drawable.ime_status_ready_background else R.drawable.floating_confirm_badge_background
            val color = if (success) R.color.cc_success else R.color.cc_text_muted
            it.setBackgroundResource(background)
            it.setTextColor(getColor(color))
        }
    }
}
