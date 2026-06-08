package com.transparentlc.cloudclipboardsync

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat

data class PermissionStatus(
    val notificationsEnabled: Boolean,
    val overlayEnabled: Boolean,
    val accessibilityEnabled: Boolean,
    val accessibilityDetail: String,
    val batteryOptimizationIgnored: Boolean,
    val shizukuInstalled: Boolean,
    val shizukuRunning: Boolean,
    val shizukuPermissionGranted: Boolean,
    val shizukuUid: Int?,
    val shizukuDetail: String,
    val clipboardReadAppOp: String,
    val clipboardWriteAppOp: String,
)

object PermissionStatusHelper {
    private const val OPSTR_READ_CLIPBOARD = "android:read_clipboard"
    private const val OPSTR_WRITE_CLIPBOARD = "android:write_clipboard"

    private data class AccessibilityStatus(
        val enabled: Boolean,
        val detail: String,
    )

    fun read(context: Context): PermissionStatus {
        val shizukuState = ShizukuPermissionHelper.read(context)
        val accessibilityStatus = readAccessibilityStatus(context)
        return PermissionStatus(
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            overlayEnabled = Settings.canDrawOverlays(context),
            accessibilityEnabled = accessibilityStatus.enabled,
            accessibilityDetail = accessibilityStatus.detail,
            batteryOptimizationIgnored = isIgnoringBatteryOptimizations(context),
            shizukuInstalled = shizukuState.installed,
            shizukuRunning = shizukuState.running,
            shizukuPermissionGranted = shizukuState.granted,
            shizukuUid = shizukuState.uid,
            shizukuDetail = shizukuState.detail,
            clipboardReadAppOp = clipboardAppOpMode(context, OPSTR_READ_CLIPBOARD),
            clipboardWriteAppOp = clipboardAppOpMode(context, OPSTR_WRITE_CLIPBOARD),
        )
    }

    private fun readAccessibilityStatus(context: Context): AccessibilityStatus {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val target = ComponentName(context, ClipboardAccessAccessibilityService::class.java).flattenToString()
        val enabledInSetting = isAccessibilityServiceEnabledInSetting(enabledServices, target)
        val enabledInManager = if (enabledInSetting) false else isAccessibilityServiceEnabledInManager(context, target)
        return AccessibilityStatus(
            enabled = enabledInSetting || enabledInManager,
            detail = accessibilityStateLabel(enabledInSetting, enabledInManager),
        )
    }

    internal fun isAccessibilityServiceEnabledInSetting(enabledServices: String?, target: String): Boolean {
        val normalizedTarget = target.trim()
        if (normalizedTarget.isBlank()) return false
        return enabledServices
            .orEmpty()
            .split(':')
            .any { it.trim().equals(normalizedTarget, ignoreCase = true) }
    }

    private fun isAccessibilityServiceEnabledInManager(context: Context, target: String): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val services = runCatching {
            manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        }.getOrDefault(emptyList())
        val flattenedServices = services.mapNotNull { info ->
            val serviceInfo = info.resolveInfo?.serviceInfo ?: return@mapNotNull null
            ComponentName(serviceInfo.packageName, serviceInfo.name).flattenToString()
        }
        return isAccessibilityServiceEnabledInServiceList(flattenedServices, target)
    }

    internal fun isAccessibilityServiceEnabledInServiceList(services: List<String>, target: String): Boolean {
        val normalizedTarget = target.trim()
        if (normalizedTarget.isBlank()) return false
        return services.any { it.trim().equals(normalizedTarget, ignoreCase = true) }
    }

    internal fun accessibilityStateLabel(enabledInSetting: Boolean, enabledInManager: Boolean): String = when {
        enabledInSetting -> "已开启（系统设置）"
        enabledInManager -> "已开启（系统服务枚举）"
        else -> "未开启"
    }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val manager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return manager.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun clipboardAppOpMode(context: Context, op: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "allow"
        val manager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = manager.unsafeCheckOpNoThrow(op, Process.myUid(), context.packageName)
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> "allow"
            AppOpsManager.MODE_FOREGROUND -> "foreground"
            AppOpsManager.MODE_IGNORED -> "ignore"
            AppOpsManager.MODE_ERRORED -> "errored"
            AppOpsManager.MODE_DEFAULT -> "default"
            else -> mode.toString()
        }
    }

}
