package com.transparentlc.cloudclipboardsync

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

data class PermissionStatus(
    val notificationsEnabled: Boolean,
    val overlayEnabled: Boolean,
    val accessibilityEnabled: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val shizukuInstalled: Boolean,
)

object PermissionStatusHelper {
    fun read(context: Context): PermissionStatus = PermissionStatus(
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        overlayEnabled = Settings.canDrawOverlays(context),
        accessibilityEnabled = isAccessibilityEnabled(context),
        batteryOptimizationIgnored = isIgnoringBatteryOptimizations(context),
        shizukuInstalled = isPackageInstalled(context, "moe.shizuku.privileged.api"),
    )

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val target = ComponentName(context, ClipboardAccessAccessibilityService::class.java).flattenToString()
        return enabledServices.split(':').any { it.equals(target, ignoreCase = true) }
    }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val manager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return manager.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: Exception) {
        false
    }
}
