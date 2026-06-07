package com.transparentlc.cloudclipboardsync

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import rikka.shizuku.Shizuku

data class ShizukuPermissionState(
    val installed: Boolean,
    val running: Boolean,
    val granted: Boolean,
    val uid: Int?,
    val detail: String,
)

object ShizukuPermissionHelper {
    const val REQUEST_CODE = 6201
    private const val PACKAGE_NAME = "moe.shizuku.privileged.api"

    fun read(context: Context): ShizukuPermissionState {
        val installed = isPackageInstalled(context, PACKAGE_NAME)
        if (!installed) {
            return ShizukuPermissionState(
                installed = false,
                running = false,
                granted = false,
                uid = null,
                detail = "未安装 Shizuku",
            )
        }

        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!running) {
            return ShizukuPermissionState(
                installed = true,
                running = false,
                granted = false,
                uid = null,
                detail = "Shizuku 已安装，但服务未运行",
            )
        }

        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val uid = if (granted) runCatching { Shizuku.getUid() }.getOrNull() else null
        return ShizukuPermissionState(
            installed = true,
            running = true,
            granted = granted,
            uid = uid,
            detail = if (granted) {
                "Shizuku 服务已运行，云剪同步已授权"
            } else {
                "Shizuku 服务已运行，云剪同步尚未授权"
            },
        )
    }

    fun requestPermission(): Boolean {
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!running) return false
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (granted) return true
        Shizuku.requestPermission(REQUEST_CODE)
        return true
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
