package com.transparentlc.cloudclipboardsync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.transparentlc.cloudclipboardsync.sync.SettingsStore
import com.transparentlc.cloudclipboardsync.sync.SyncService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val config = SettingsStore.load(context)
        if (!config.startOnBootEnabled) return
        if (!SettingsStore.shouldResumeSync(context)) return
        if (config.serverBase.isBlank()) return
        if (!RuntimeModeValidator.validate(context, config).ready) return
        SyncService.start(context)
    }
}
