package com.transparentlc.cloudclipboardsync

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.transparentlc.cloudclipboardsync.sync.PayloadCacheStore
import com.transparentlc.cloudclipboardsync.sync.SettingsStore
import com.transparentlc.cloudclipboardsync.sync.SyncService
import kotlin.math.roundToInt

class FloatingConfirmService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val pendingPayloadIds = ArrayDeque<String>()
    private var currentPayloadId: String? = null
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val hideRunnable = Runnable { dismissCurrent(showNext = true) }
    private var alertMode = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW_ALERT) {
            showAlertOverlay(
                intent.getStringExtra(EXTRA_ALERT_TITLE).orEmpty(),
                intent.getStringExtra(EXTRA_ALERT_MESSAGE).orEmpty(),
            )
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
            showOverlay(entry.title, "${describeKind(entry.kind)} · ${entry.size.sizeLabel()}", nextId)
            return
        }
        stopSelf()
    }

    private fun showOverlay(title: String, meta: String, payloadId: String) {
        handler.removeCallbacks(hideRunnable)
        overlayView?.let { windowManager?.removeViewImmediate(it) }

        val config = SettingsStore.load(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        val titleView = TextView(this).apply {
            text = title
            textSize = 16f
        }
        val metaView = TextView(this).apply {
            text = meta
            textSize = 12f
        }
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val confirmButton = Button(this).apply {
            text = getString(R.string.floating_confirm_button)
            setOnClickListener {
                SyncService.confirmPayload(this@FloatingConfirmService, payloadId)
                startActivity(
                    Intent(this@FloatingConfirmService, ReceivedPayloadActivity::class.java)
                        .putExtra(SyncService.EXTRA_PAYLOAD_ID, payloadId)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                dismissCurrent(showNext = true)
            }
        }
        val ignoreButton = Button(this).apply {
            text = getString(R.string.floating_ignore_button)
            setOnClickListener { dismissCurrent(showNext = true) }
        }
        buttonRow.addView(confirmButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttonRow.addView(ignoreButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(10)
        })
        root.addView(titleView)
        root.addView(metaView)
        root.addView(buttonRow)

        val params = WindowManager.LayoutParams(
            dp(config.floatingWidthDp),
            dp(config.floatingHeightDp).coerceAtLeast(WindowManager.LayoutParams.WRAP_CONTENT),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = config.floatingPosX
            y = config.floatingPosY
        }

        attachDragSupport(root, params)
        layoutParams = params
        overlayView = root
        windowManager?.addView(root, params)
        handler.postDelayed(hideRunnable, config.floatingShowSeconds.coerceAtLeast(5) * 1000L)
    }

    private fun showAlertOverlay(title: String, message: String) {
        alertMode = true
        handler.removeCallbacks(hideRunnable)
        overlayView?.let { windowManager?.removeViewImmediate(it) }

        val config = SettingsStore.load(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        val titleView = TextView(this).apply {
            text = title.ifBlank { getString(R.string.reconnect_failure_alert_title) }
            textSize = 16f
        }
        val messageView = TextView(this).apply {
            text = message
            textSize = 13f
        }
        val closeButton = Button(this).apply {
            text = getString(R.string.reconnect_failure_alert_button)
            setOnClickListener { dismissCurrent(showNext = false) }
        }
        root.addView(titleView)
        root.addView(messageView)
        root.addView(closeButton)

        val params = WindowManager.LayoutParams(
            dp(config.floatingWidthDp),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = config.floatingPosX
            y = config.floatingPosY
        }

        attachDragSupport(root, params)
        layoutParams = params
        overlayView = root
        windowManager?.addView(root, params)
        handler.postDelayed(hideRunnable, config.floatingShowSeconds.coerceAtLeast(5) * 1000L)
    }

    private fun attachDragSupport(view: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        view.setOnTouchListener { _, event ->
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
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    SettingsStore.updateFloatingPosition(this, params.x, params.y)
                    false
                }
                else -> false
            }
        }
    }

    private fun dismissCurrent(showNext: Boolean) {
        handler.removeCallbacks(hideRunnable)
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

    private fun Long.sizeLabel(): String = if (this > 0) "${this} B" else getString(R.string.payload_size_unknown)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val ACTION_SHOW_ALERT = "com.transparentlc.cloudclipboardsync.action.SHOW_ALERT"
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
    }
}
