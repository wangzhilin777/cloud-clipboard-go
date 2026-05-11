package com.transparentlc.cloudclipboardsync

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.transparentlc.cloudclipboardsync.sync.PayloadCacheStore
import com.transparentlc.cloudclipboardsync.sync.SettingsStore
import com.transparentlc.cloudclipboardsync.sync.SyncService

class MainActivity : AppCompatActivity() {
    private lateinit var serverBaseInput: EditText
    private lateinit var roomInput: EditText
    private lateinit var roomPasswordInput: EditText
    private lateinit var deviceNameInput: EditText
    private lateinit var statusText: TextView
    private lateinit var lastSyncText: TextView

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            statusText.text = intent?.getStringExtra(SyncService.EXTRA_STATUS) ?: getString(R.string.status_idle)
            lastSyncText.text = intent?.getStringExtra(SyncService.EXTRA_LAST_RESULT) ?: getString(R.string.last_result_idle)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        serverBaseInput = findViewById(R.id.serverBaseInput)
        roomInput = findViewById(R.id.roomInput)
        roomPasswordInput = findViewById(R.id.roomPasswordInput)
        deviceNameInput = findViewById(R.id.deviceNameInput)
        statusText = findViewById(R.id.statusText)
        lastSyncText = findViewById(R.id.lastSyncText)

        bindConfig()
        findViewById<Button>(R.id.saveButton).setOnClickListener {
            val config = saveConfig()
            if (isLoopbackServerBase(config.serverBase)) {
                showLoopbackHint()
            }
        }
        findViewById<Button>(R.id.startButton).setOnClickListener {
            val config = saveConfig()
            if (isLoopbackServerBase(config.serverBase)) {
                showLoopbackHint()
                return@setOnClickListener
            }
            SyncService.start(this)
        }
        findViewById<Button>(R.id.stopButton).setOnClickListener {
            SyncService.stop(this)
        }
        findViewById<Button>(R.id.openReceivedButton).setOnClickListener {
            startActivity(Intent(this, ReceivedPayloadActivity::class.java))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        PayloadCacheStore.pruneExpired(this)
        ContextCompat.registerReceiver(
            this,
            statusReceiver,
            IntentFilter(SyncService.ACTION_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(statusReceiver)
    }

    private fun bindConfig() {
        val config = SettingsStore.load(this)
        serverBaseInput.setText(config.serverBase)
        roomInput.setText(config.room)
        roomPasswordInput.setText(config.roomPassword)
        deviceNameInput.setText(config.deviceName)
        statusText.text = getString(R.string.status_idle)
        lastSyncText.text = getString(R.string.last_result_idle)
    }

    private fun saveConfig(): SettingsStore.Config {
        val config = SettingsStore.Config(
            serverBase = serverBaseInput.text.toString().trim(),
            room = roomInput.text.toString().trim(),
            roomPassword = roomPasswordInput.text.toString().trim(),
            deviceName = deviceNameInput.text.toString().trim().ifBlank { "Android 同步端" },
            deviceId = SettingsStore.load(this).deviceId,
        )
        SettingsStore.save(this, config)
        return config
    }

    private fun isLoopbackServerBase(serverBase: String): Boolean {
        if (serverBase.isBlank()) return false
        val host = runCatching { Uri.parse(serverBase).host.orEmpty().lowercase() }.getOrDefault("")
        return host == "127.0.0.1" || host == "localhost" || host == "::1"
    }

    private fun showLoopbackHint() {
        val message = getString(R.string.server_base_loopback_hint)
        lastSyncText.text = message
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
