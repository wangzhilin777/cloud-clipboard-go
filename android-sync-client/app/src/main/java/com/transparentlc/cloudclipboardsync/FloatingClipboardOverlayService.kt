package com.transparentlc.cloudclipboardsync

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.transparentlc.cloudclipboardsync.sync.SettingsStore
import kotlin.math.roundToInt

class FloatingClipboardOverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val dismissRunnable = Runnable { dismiss() }
    private var countdownRunnable: Runnable? = null
    private var countdownTargetAt = 0L
    private var autoConfirmRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS) {
            dismiss()
            return START_NOT_STICKY
        }
        if (!canDrawOverlays()) {
            stopSelf()
            return START_NOT_STICKY
        }
        FloatingConfirmService.dismiss(this)
        showOverlay()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlayView?.let { windowManager?.removeViewImmediate(it) }
        overlayView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        handler.removeCallbacks(dismissRunnable)
        countdownRunnable?.let(handler::removeCallbacks)
        autoConfirmRunnable?.let(handler::removeCallbacks)
        overlayView?.let { windowManager?.removeViewImmediate(it) }

        val config = SettingsStore.load(this)
        val root = LayoutInflater.from(this).inflate(R.layout.view_floating_clipboard_send, null)
        val dragHandle = root.findViewById<View>(R.id.floatingClipboardDragHandle)
        val statusText = root.findViewById<TextView>(R.id.floatingClipboardStatusText)
        val previewText = root.findViewById<TextView>(R.id.floatingClipboardPreviewText)
        val hintText = root.findViewById<TextView>(R.id.floatingClipboardHintText)
        val sendButton = root.findViewById<Button>(R.id.floatingClipboardSendButton)
        val openButton = root.findViewById<Button>(R.id.floatingClipboardOpenAppButton)

        root.findViewById<TextView>(R.id.floatingClipboardCloseButton).setOnClickListener { dismiss() }
        bindClipboardPreview(statusText, previewText, hintText)

        sendButton.setOnClickListener {
            val sent = ManualClipboardSender.sendCurrentClipboardText(
                context = this,
                route = SettingsStore.CLIPBOARD_MODE_FLOATING,
                onStatus = { message ->
                    statusText.text = message
                    updateStatusBadge(statusText, message == getString(R.string.manual_clipboard_sent))
                },
            )
            if (sent) {
                dismiss()
            } else {
                bindClipboardPreview(statusText, previewText, hintText)
            }
        }
        openButton.setOnClickListener {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            }
            dismiss()
        }

        val params = WindowManager.LayoutParams(
            dp(config.floatingWidthDp),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = config.floatingPosX
            y = config.floatingPosY
        }
        root.minimumHeight = dp(config.floatingHeightDp)

        attachDragSupport(dragHandle, root, params)
        layoutParams = params
        overlayView = root
        if (!attachOverlayView(root, params)) {
            return
        }
        applyClampedPosition(root, params, save = false)
        scheduleAutoSendIfNeeded(config, sendButton)
        bindCountdown(root.findViewById(R.id.floatingClipboardCountdownText), config.floatingShowSeconds.coerceAtLeast(5))
    }

    private fun bindClipboardPreview(statusText: TextView, previewText: TextView, hintText: TextView) {
        val clipboardText = ManualClipboardSender.readCurrentClipboardText(this)
        if (clipboardText.isBlank()) {
            statusText.text = getString(R.string.manual_clipboard_empty_clipboard)
            updateStatusBadge(statusText, false)
            previewText.text = getString(R.string.floating_clipboard_preview_empty)
            hintText.text = getString(R.string.floating_clipboard_hint_empty)
            return
        }
        statusText.text = getString(R.string.floating_clipboard_status_ready)
        updateStatusBadge(statusText, true)
        previewText.text = TextUtils.ellipsize(
            clipboardText.replace('\n', ' ').trim(),
            previewText.paint,
            dp(220).toFloat(),
            TextUtils.TruncateAt.END,
        )
        hintText.text = getString(R.string.floating_clipboard_hint_ready)
    }

    private fun updateStatusBadge(statusText: TextView, ready: Boolean) {
        val background = if (ready) R.drawable.ime_status_ready_background else R.drawable.floating_confirm_badge_background
        val color = if (ready) R.color.cc_success else R.color.cc_text_muted
        statusText.setBackgroundResource(background)
        statusText.setTextColor(getColor(color))
    }

    private fun attachOverlayView(view: View, params: WindowManager.LayoutParams): Boolean = try {
        windowManager?.addView(view, params)
        true
    } catch (_: Exception) {
        overlayView = null
        stopSelf()
        false
    }

    private fun attachDragSupport(dragHandle: View, contentView: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).roundToInt()
                    params.y = startY + (event.rawY - touchY).roundToInt()
                    applyClampedPosition(contentView, params, save = false)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    applyClampedPosition(contentView, params, save = true)
                    false
                }
                else -> false
            }
        }
    }

    private fun applyClampedPosition(view: View, params: WindowManager.LayoutParams, save: Boolean) {
        val metrics = resources.displayMetrics
        val viewWidth = view.width.takeIf { it > 0 } ?: params.width.takeIf { it > 0 } ?: dp(220)
        val measuredHeight = view.height.takeIf { it > 0 } ?: view.measuredHeight.takeIf { it > 0 }
        val fallbackHeight = layoutParams?.height?.takeIf { it > 0 } ?: dp(120)
        val viewHeight = measuredHeight ?: fallbackHeight
        val maxX = (metrics.widthPixels - viewWidth).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - viewHeight).coerceAtLeast(0)
        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
        windowManager?.updateViewLayout(view, params)
        if (save) {
            SettingsStore.updateFloatingPosition(this, params.x, params.y)
        }
    }

    private fun bindCountdown(countdownView: TextView, seconds: Int) {
        countdownTargetAt = System.currentTimeMillis() + seconds * 1000L
        val runnable = object : Runnable {
            override fun run() {
                val remaining = ((countdownTargetAt - System.currentTimeMillis()) / 1000.0).toInt().coerceAtLeast(0)
                countdownView.text = getString(R.string.floating_countdown_format, remaining)
                if (remaining <= 0) {
                    dismiss()
                } else {
                    handler.postDelayed(this, 1000L)
                }
            }
        }
        countdownRunnable = runnable
        runnable.run()
        handler.postDelayed(dismissRunnable, seconds * 1000L)
    }

    private fun scheduleAutoSendIfNeeded(
        config: SettingsStore.Config,
        sendButton: Button,
    ) {
        if (!config.floatingAutoSendConfirmEnabled) return
        val runnable = Runnable {
            if (overlayView != null && sendButton.isAttachedToWindow && sendButton.isShown && sendButton.isEnabled) {
                sendButton.performClick()
            }
        }
        autoConfirmRunnable = runnable
        handler.postDelayed(runnable, 220L)
    }

    private fun dismiss() {
        handler.removeCallbacks(dismissRunnable)
        countdownRunnable?.let(handler::removeCallbacks)
        countdownRunnable = null
        autoConfirmRunnable?.let(handler::removeCallbacks)
        autoConfirmRunnable = null
        overlayView?.let { windowManager?.removeViewImmediate(it) }
        overlayView = null
        stopSelf()
    }

    private fun canDrawOverlays(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val ACTION_DISMISS = "com.transparentlc.cloudclipboardsync.action.DISMISS_FLOATING_CLIPBOARD"

        fun show(context: Context) {
            context.startService(Intent(context, FloatingClipboardOverlayService::class.java))
        }

        fun dismiss(context: Context) {
            context.startService(
                Intent(context, FloatingClipboardOverlayService::class.java)
                    .setAction(ACTION_DISMISS),
            )
        }
    }
}
