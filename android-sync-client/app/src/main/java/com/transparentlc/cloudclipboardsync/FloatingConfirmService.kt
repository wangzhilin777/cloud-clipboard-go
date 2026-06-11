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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.transparentlc.cloudclipboardsync.sync.PayloadCacheStore
import com.transparentlc.cloudclipboardsync.sync.PayloadEntry
import com.transparentlc.cloudclipboardsync.sync.SettingsStore
import com.transparentlc.cloudclipboardsync.sync.SyncService
import java.util.Locale
import kotlin.math.roundToInt

class FloatingConfirmService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val pendingPayloadIds = ArrayDeque<String>()
    private var currentPayloadId: String? = null
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val hideRunnable = Runnable { dismissCurrent(showNext = true) }
    private var countdownRunnable: Runnable? = null
    private var countdownTargetAt = 0L
    private var alertMode = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS) {
            dismissCurrent(showNext = false)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_SHOW_ALERT) {
            showAlertOverlay(
                intent.getStringExtra(EXTRA_ALERT_TITLE).orEmpty(),
                intent.getStringExtra(EXTRA_ALERT_MESSAGE).orEmpty(),
            )
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_SHOW_PREVIEW) {
            showPreviewOverlay()
            return START_NOT_STICKY
        }
        val payloadId = intent?.getStringExtra(EXTRA_PAYLOAD_ID)
        if (!payloadId.isNullOrBlank()) {
            enqueuePayload(payloadId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlayView?.let { windowManager?.removeViewImmediate(it) }
        overlayView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun enqueuePayload(payloadId: String) {
        if (!canDrawOverlays()) {
            stopSelf()
            return
        }
        val entry = PayloadCacheStore.get(this, payloadId) ?: return
        if (PayloadCacheStore.isSnoozed(entry)) return
        if (payloadId == currentPayloadId || pendingPayloadIds.contains(payloadId)) return
        pendingPayloadIds.addLast(payloadId)
        if (currentPayloadId == null) {
            showNext()
        }
    }

    private fun showNext() {
        alertMode = false
        while (pendingPayloadIds.isNotEmpty()) {
            val nextId = pendingPayloadIds.removeFirst()
            val entry = PayloadCacheStore.get(this, nextId) ?: continue
            currentPayloadId = nextId
            showOverlay(entry)
            return
        }
        stopSelf()
    }

    private fun showOverlay(entry: PayloadEntry) {
        if (!canDrawOverlays()) {
            stopSelf()
            return
        }
        FloatingClipboardOverlayService.dismiss(this)
        handler.removeCallbacks(hideRunnable)
        countdownRunnable?.let(handler::removeCallbacks)
        overlayView?.let { windowManager?.removeViewImmediate(it) }

        val config = SettingsStore.load(this)
        val snoozeMinutes = config.floatingSnoozeMinutes.coerceAtLeast(1)
        val snoozeDurationMs = snoozeMinutes * 60_000L
        val root = LayoutInflater.from(this).inflate(R.layout.view_floating_confirm, null)
        val dragHandle = root.findViewById<View>(R.id.floatingConfirmDragHandle)
        val badgeText = root.findViewById<TextView>(R.id.floatingBadgeText)
        val titleText = root.findViewById<TextView>(R.id.floatingTitleText)
        val metaText = root.findViewById<TextView>(R.id.floatingMetaText)
        val sourceText = root.findViewById<TextView>(R.id.floatingSourceText)
        val hintText = root.findViewById<TextView>(R.id.floatingActionHintText)
        val closeButton = root.findViewById<TextView>(R.id.floatingCloseButton)
        val confirmButton = root.findViewById<Button>(R.id.floatingConfirmButton)
        val openButton = root.findViewById<Button>(R.id.floatingOpenButton)
        val ignoreButton = root.findViewById<Button>(R.id.floatingIgnoreButton)
        val ignoreAllButton = root.findViewById<Button>(R.id.floatingIgnoreAllButton)

        badgeText.text = pendingBadgeText()
        titleText.text = entry.title
        metaText.text = getString(
            R.string.floating_meta_format,
            describeKind(entry.kind),
            entry.size.sizeLabel(),
        )
        sourceText.text = getString(
            R.string.floating_source_format,
            entry.sourceDeviceId.ifBlank { getString(R.string.floating_source_unknown_device) },
            entry.room.ifBlank { getString(R.string.floating_source_default_room) },
        )
        hintText.text = buildActionHint()
        closeButton.setOnClickListener { dismissCurrent(showNext = true) }
        applyCompactMode(config, metaText, sourceText, hintText)
        confirmButton.apply {
            text = getString(
                if (config.floatingCompactEnabled) R.string.floating_compact_confirm_button else R.string.floating_confirm_button,
            )
            setOnClickListener {
                PayloadCacheStore.clearSnooze(this@FloatingConfirmService, entry.payloadId)
                SyncService.confirmPayload(this@FloatingConfirmService, entry.payloadId)
                openReceivedPage(entry.payloadId)
                dismissCurrent(showNext = true)
            }
        }
        openButton.apply {
            text = getString(
                if (config.floatingCompactEnabled) R.string.floating_compact_open_button else R.string.floating_open_button,
            )
            setOnClickListener {
                PayloadCacheStore.clearSnooze(this@FloatingConfirmService, entry.payloadId)
                openReceivedPage(entry.payloadId)
                dismissCurrent(showNext = true)
            }
        }
        ignoreButton.apply {
            text = if (config.floatingCompactEnabled) {
                getString(R.string.floating_compact_ignore_button)
            } else {
                getString(R.string.floating_ignore_button_minutes, snoozeMinutes)
            }
            setOnClickListener {
                PayloadCacheStore.markSnoozed(
                    this@FloatingConfirmService,
                    entry.payloadId,
                    System.currentTimeMillis() + snoozeDurationMs,
                )
                dismissCurrent(showNext = true)
            }
        }
        ignoreAllButton.apply {
            text = if (config.floatingCompactEnabled) {
                getString(R.string.floating_compact_ignore_all_button)
            } else {
                getString(R.string.floating_ignore_all_button_minutes, snoozeMinutes)
            }
            setOnClickListener {
                val snoozedUntil = System.currentTimeMillis() + snoozeDurationMs
                pendingPayloadIds.forEach { payloadId ->
                    PayloadCacheStore.markSnoozed(this@FloatingConfirmService, payloadId, snoozedUntil)
                }
                PayloadCacheStore.markSnoozed(this@FloatingConfirmService, entry.payloadId, snoozedUntil)
                pendingPayloadIds.clear()
                dismissCurrent(showNext = false)
            }
        }

        val params = WindowManager.LayoutParams(
            dp(config.floatingWidthDp),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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
        bindCountdown(root.findViewById(R.id.floatingCountdownText), config.floatingShowSeconds.coerceAtLeast(5))
    }

    private fun showAlertOverlay(title: String, message: String) {
        if (!canDrawOverlays()) {
            stopSelf()
            return
        }
        FloatingClipboardOverlayService.dismiss(this)
        alertMode = true
        handler.removeCallbacks(hideRunnable)
        countdownRunnable?.let(handler::removeCallbacks)
        overlayView?.let { windowManager?.removeViewImmediate(it) }

        val config = SettingsStore.load(this)
        val root = LayoutInflater.from(this).inflate(R.layout.view_floating_confirm, null)
        val dragHandle = root.findViewById<View>(R.id.floatingConfirmDragHandle)
        root.findViewById<TextView>(R.id.floatingBadgeText).text = getString(R.string.reconnect_failure_alert_title)
        root.findViewById<TextView>(R.id.floatingTitleText).text = title.ifBlank { getString(R.string.reconnect_failure_alert_title) }
        root.findViewById<TextView>(R.id.floatingMetaText).text = message
        root.findViewById<TextView>(R.id.floatingSourceText).visibility = View.GONE
        root.findViewById<TextView>(R.id.floatingActionHintText).text = getString(R.string.floating_action_hint_alert)
        root.findViewById<TextView>(R.id.floatingCloseButton).setOnClickListener { dismissCurrent(showNext = false) }
        root.findViewById<Button>(R.id.floatingConfirmButton).apply {
            text = getString(R.string.reconnect_failure_alert_button)
            setOnClickListener { dismissCurrent(showNext = false) }
        }
        root.findViewById<Button>(R.id.floatingOpenButton).visibility = View.GONE
        root.findViewById<Button>(R.id.floatingIgnoreButton).visibility = View.GONE
        root.findViewById<Button>(R.id.floatingIgnoreAllButton).visibility = View.GONE

        val params = WindowManager.LayoutParams(
            dp(config.floatingWidthDp),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = config.floatingPosX
            y = config.floatingPosY
        }

        attachDragSupport(dragHandle, root, params)
        layoutParams = params
        overlayView = root
        if (!attachOverlayView(root, params)) {
            return
        }
        applyClampedPosition(root, params, save = false)
        bindCountdown(root.findViewById(R.id.floatingCountdownText), config.floatingShowSeconds.coerceAtLeast(5))
    }

    private fun showPreviewOverlay() {
        if (!canDrawOverlays()) {
            stopSelf()
            return
        }
        FloatingClipboardOverlayService.dismiss(this)
        alertMode = true
        handler.removeCallbacks(hideRunnable)
        countdownRunnable?.let(handler::removeCallbacks)
        overlayView?.let { windowManager?.removeViewImmediate(it) }

        val config = SettingsStore.load(this)
        val root = LayoutInflater.from(this).inflate(R.layout.view_floating_confirm, null)
        val dragHandle = root.findViewById<View>(R.id.floatingConfirmDragHandle)
        root.findViewById<TextView>(R.id.floatingBadgeText).text = getString(R.string.floating_preview_badge)
        root.findViewById<TextView>(R.id.floatingTitleText).text = getString(R.string.floating_preview_title)
        val metaText = root.findViewById<TextView>(R.id.floatingMetaText)
        val sourceText = root.findViewById<TextView>(R.id.floatingSourceText)
        val hintText = root.findViewById<TextView>(R.id.floatingActionHintText)
        metaText.text = getString(
            R.string.floating_meta_format,
            getString(R.string.payload_kind_image),
            "2.4 MB",
        )
        sourceText.text = getString(
            R.string.floating_source_format,
            getString(R.string.floating_preview_source_device),
            getString(R.string.floating_preview_source_room),
        )
        hintText.text = getString(R.string.floating_action_hint_preview)
        root.findViewById<TextView>(R.id.floatingCloseButton).setOnClickListener {
            dismissCurrent(showNext = false)
        }
        applyCompactMode(config, metaText, sourceText, hintText)
        root.findViewById<Button>(R.id.floatingConfirmButton).apply {
            text = getString(
                if (config.floatingCompactEnabled) R.string.floating_compact_confirm_button else R.string.floating_confirm_button,
            )
            setOnClickListener {
                dismissCurrent(showNext = false)
            }
        }
        root.findViewById<Button>(R.id.floatingOpenButton).apply {
            text = getString(
                if (config.floatingCompactEnabled) R.string.floating_compact_open_button else R.string.floating_open_button,
            )
            setOnClickListener {
                dismissCurrent(showNext = false)
            }
        }
        root.findViewById<Button>(R.id.floatingIgnoreButton).apply {
            text = if (config.floatingCompactEnabled) {
                getString(R.string.floating_compact_ignore_button)
            } else {
                getString(R.string.floating_ignore_button)
            }
            setOnClickListener {
                dismissCurrent(showNext = false)
            }
        }
        root.findViewById<Button>(R.id.floatingIgnoreAllButton).apply {
            text = if (config.floatingCompactEnabled) {
                getString(R.string.floating_compact_ignore_all_button)
            } else {
                getString(R.string.floating_ignore_all_button)
            }
            setOnClickListener {
                dismissCurrent(showNext = false)
            }
        }

        val params = WindowManager.LayoutParams(
            dp(config.floatingWidthDp),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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
        bindCountdown(root.findViewById(R.id.floatingCountdownText), config.floatingShowSeconds.coerceAtLeast(5))
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

    private fun applyCompactMode(
        config: SettingsStore.Config,
        metaText: TextView,
        sourceText: TextView,
        hintText: TextView,
    ) {
        val compact = config.floatingCompactEnabled
        metaText.visibility = if (compact) View.GONE else View.VISIBLE
        sourceText.visibility = if (compact) View.GONE else View.VISIBLE
        hintText.visibility = if (compact) View.GONE else View.VISIBLE
    }

    private fun bindCountdown(countdownView: TextView, seconds: Int) {
        countdownTargetAt = System.currentTimeMillis() + seconds * 1000L
        val runnable = object : Runnable {
            override fun run() {
                val remaining = ((countdownTargetAt - System.currentTimeMillis()) / 1000.0).toInt().coerceAtLeast(0)
                countdownView.text = getString(R.string.floating_countdown_format, remaining)
                if (remaining <= 0) {
                    dismissCurrent(showNext = true)
                } else {
                    handler.postDelayed(this, 1000L)
                }
            }
        }
        countdownRunnable = runnable
        runnable.run()
        handler.postDelayed(hideRunnable, seconds * 1000L)
    }

    private fun pendingBadgeText(): String = if (pendingPayloadIds.isEmpty()) {
        getString(R.string.floating_queue_single)
    } else {
        getString(R.string.floating_queue_multiple, pendingPayloadIds.size)
    }

    private fun buildActionHint(): String = if (pendingPayloadIds.isEmpty()) {
        getString(R.string.floating_action_hint_download)
    } else {
        getString(R.string.floating_action_hint_queue)
    }

    private fun canDrawOverlays(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    private fun openReceivedPage(payloadId: String) {
        startActivity(
            Intent(this, ReceivedPayloadActivity::class.java)
                .putExtra(SyncService.EXTRA_PAYLOAD_ID, payloadId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun dismissCurrent(showNext: Boolean) {
        handler.removeCallbacks(hideRunnable)
        countdownRunnable?.let(handler::removeCallbacks)
        countdownRunnable = null
        overlayView?.let { windowManager?.removeViewImmediate(it) }
        overlayView = null
        currentPayloadId = null
        if (showNext && !alertMode) {
            showNext()
        } else {
            alertMode = false
            stopSelf()
        }
    }

    private fun describeKind(kind: String): String = when (kind) {
        "image" -> getString(R.string.payload_kind_image)
        "file" -> getString(R.string.payload_kind_file)
        else -> getString(R.string.payload_kind_unknown)
    }

    private fun Long.sizeLabel(): String {
        if (this <= 0) {
            return getString(R.string.payload_size_unknown)
        }
        val value = this.toDouble()
        return when {
            value >= 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f GB", value / (1024 * 1024 * 1024))
            value >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", value / (1024 * 1024))
            value >= 1024 -> String.format(Locale.getDefault(), "%.1f KB", value / 1024)
            else -> "${this} B"
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val ACTION_DISMISS = "com.transparentlc.cloudclipboardsync.action.DISMISS_FLOATING_CONFIRM"
        private const val ACTION_SHOW_ALERT = "com.transparentlc.cloudclipboardsync.action.SHOW_ALERT"
        private const val ACTION_SHOW_PREVIEW = "com.transparentlc.cloudclipboardsync.action.SHOW_PREVIEW"
        private const val EXTRA_PAYLOAD_ID = "payload_id"
        private const val EXTRA_ALERT_TITLE = "alert_title"
        private const val EXTRA_ALERT_MESSAGE = "alert_message"

        fun show(context: Context, payloadId: String) {
            val intent = Intent(context, FloatingConfirmService::class.java).putExtra(EXTRA_PAYLOAD_ID, payloadId)
            context.startService(intent)
        }

        fun showSyncAlert(context: Context, title: String, message: String) {
            val intent = Intent(context, FloatingConfirmService::class.java)
                .setAction(ACTION_SHOW_ALERT)
                .putExtra(EXTRA_ALERT_TITLE, title)
                .putExtra(EXTRA_ALERT_MESSAGE, message)
            context.startService(intent)
        }

        fun showPreview(context: Context) {
            val intent = Intent(context, FloatingConfirmService::class.java)
                .setAction(ACTION_SHOW_PREVIEW)
            context.startService(intent)
        }

        fun dismiss(context: Context) {
            val intent = Intent(context, FloatingConfirmService::class.java)
                .setAction(ACTION_DISMISS)
            context.startService(intent)
        }
    }
}
