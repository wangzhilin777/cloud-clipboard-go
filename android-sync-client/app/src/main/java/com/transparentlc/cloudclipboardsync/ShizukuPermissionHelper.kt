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
        android.util.Log.w("ShizukuPermissionHelper", "=== 开始 Shizuku 状态诊断 ===")

        val installed = isPackageInstalled(context, PACKAGE_NAME)
        android.util.Log.w("ShizukuPermissionHelper", "isPackageInstalled($PACKAGE_NAME) = $installed")
        if (!installed) {
            return ShizukuPermissionState(
                installed = false,
                running = false,
                granted = false,
                uid = null,
                detail = "未安装 Shizuku",
            )
        }

        val pingResult = runCatching { Shizuku.pingBinder() }
        val running = pingResult.getOrDefault(false)
        android.util.Log.w("ShizukuPermissionHelper", "Shizuku.pingBinder() = $running, exception = ${pingResult.exceptionOrNull()?.message}")
        if (!running) {
            return ShizukuPermissionState(
                installed = true,
                running = false,
                granted = false,
                uid = null,
                detail = "Shizuku 已安装，但服务未运行",
            )
        }

        val permResult = runCatching {
            Shizuku.checkSelfPermission()
        }
        val permValue = permResult.getOrNull()
        val granted = permValue == PackageManager.PERMISSION_GRANTED
        android.util.Log.w("ShizukuPermissionHelper", "Shizuku.checkSelfPermission() = $permValue (GRANTED=${PackageManager.PERMISSION_GRANTED}, DENIED=${PackageManager.PERMISSION_DENIED}), result = $granted, exception = ${permResult.exceptionOrNull()?.message}")

        val uidResult = if (granted) runCatching { Shizuku.getUid() } else null
        val uid = uidResult?.getOrNull()
        android.util.Log.w("ShizukuPermissionHelper", "Shizuku.getUid() = $uid, exception = ${uidResult?.exceptionOrNull()?.message}")

        val finalState = ShizukuPermissionState(
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
        android.util.Log.w("ShizukuPermissionHelper", "最终状态: $finalState")
        android.util.Log.w("ShizukuPermissionHelper", "=== Shizuku 状态诊断完成 ===")
        return finalState
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
        android.util.Log.w("ShizukuPermissionHelper", "开始检查包名: $packageName")
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
        }
        android.util.Log.w("ShizukuPermissionHelper", "包名检查通过: $packageName, versionCode=${packageInfo.versionCode}")
        true
    } catch (e: Exception) {
        android.util.Log.w("ShizukuPermissionHelper", "包名检查失败: $packageName, 异常: ${e.message}", e)
        false
    }
}
