package com.transparentlc.cloudclipboardsync

import android.content.Context
import com.transparentlc.cloudclipboardsync.sync.SettingsStore

data class ClipboardModeSupport(
    val canStart: Boolean,
    val readyMessage: String,
    val blockedMessage: String? = null,
    val implementationSummary: String,
)

object ClipboardModeSupportHelper {
    fun describe(context: Context, mode: String, status: PermissionStatus): ClipboardModeSupport = when (mode) {
        SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY -> {
            if (status.accessibilityEnabled) {
                ClipboardModeSupport(
                    canStart = true,
                    readyMessage = "无障碍辅助能力已就绪（${status.accessibilityDetail}）。",
                    implementationSummary = "无障碍当前只作为兼容旧配置时的辅助能力保留。它会在系统剪贴板回调之外，结合界面交互触发补检查，但不再作为正式推荐的后台同步主模式。当前状态：${status.accessibilityDetail}。",
                )
            } else {
                ClipboardModeSupport(
                    canStart = false,
                    readyMessage = "",
                    blockedMessage = context.getString(R.string.runtime_mode_accessibility_blocked),
                    implementationSummary = "无障碍当前只作为兼容旧配置时的辅助能力保留；若历史配置仍落在这里，需要先开启系统无障碍服务。正式推荐模式请改用前台服务、显式发送或悬浮窗。",
                )
            }
        }

        SettingsStore.CLIPBOARD_MODE_SHIZUKU -> {
            when {
                !status.shizukuInstalled -> ClipboardModeSupport(
                    canStart = false,
                    readyMessage = "",
                    blockedMessage = context.getString(R.string.runtime_mode_shizuku_blocked),
                    implementationSummary = "当前设备还没有可用的 Shizuku 环境，请先安装 Shizuku 并启动服务。",
                )

                !status.shizukuRunning -> ClipboardModeSupport(
                    canStart = false,
                    readyMessage = "",
                    blockedMessage = context.getString(R.string.runtime_mode_shizuku_not_running),
                    implementationSummary = "已检测到 Shizuku App，但服务还没有运行；如果使用 root 启动 Shizuku，请先在 Shizuku 内确认服务状态。",
                )

                !status.shizukuPermissionGranted -> ClipboardModeSupport(
                    canStart = false,
                    readyMessage = "",
                    blockedMessage = context.getString(R.string.runtime_mode_shizuku_permission_required),
                    implementationSummary = "Shizuku 服务已运行，但云剪同步还没获得授权；点快捷处理按钮可以直接弹出授权请求。",
                )

                else -> ClipboardModeSupport(
                    canStart = true,
                    readyMessage = context.getString(R.string.runtime_mode_shizuku_ready),
                    implementationSummary = "Shizuku 服务已运行且云剪同步已授权；当前仅作为系统授权与剪贴板 AppOps 诊断辅助保留，不再额外轮询系统剪贴板，也不再作为正式推荐的后台同步主模式。",
                )
            }
        }

        SettingsStore.CLIPBOARD_MODE_IME -> {
            ClipboardModeSupport(
                canStart = true,
                readyMessage = context.getString(R.string.runtime_mode_ime_ready),
                implementationSummary = "显式发送模式当前不替换原键盘，优先复用系统分享、选中文本后“发送到云剪同步”和主界面手动发送按钮作为文本上行兜底入口。历史专用输入助手仍保留在仓库里，但只作为探索残留，不再属于正式产品路线。",
            )
        }

        SettingsStore.CLIPBOARD_MODE_FLOATING -> {
            if (status.overlayEnabled) {
                ClipboardModeSupport(
                    canStart = true,
                    readyMessage = context.getString(R.string.runtime_mode_floating_ready),
                    implementationSummary = "悬浮窗模式当前先作为复制后快速发送助手的正式模式入口。它依赖悬浮窗权限，后续会继续扩展为更轻量的复制后发送浮标，不承诺绕过系统后台剪贴板限制。",
                )
            } else {
                ClipboardModeSupport(
                    canStart = false,
                    readyMessage = "",
                    blockedMessage = context.getString(R.string.runtime_mode_floating_blocked),
                    implementationSummary = "悬浮窗模式需要先允许本应用显示悬浮窗；开启后可作为复制后快速发送助手的正式入口。",
                )
            }
        }

        else -> {
            ClipboardModeSupport(
                canStart = true,
                readyMessage = "前台服务模式已就绪。",
                implementationSummary = "前台服务模式开箱可用，适合前台使用、自动续连、文本同步和图片/文件确认接收。",
            )
        }
    }
}
