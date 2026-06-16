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
    fun describe(context: Context, config: SettingsStore.Config, status: PermissionStatus): ClipboardModeSupport = when (config.clipboardMode) {
        SettingsStore.CLIPBOARD_MODE_ACCESSIBILITY -> {
            if (status.accessibilityEnabled) {
                ClipboardModeSupport(
                    canStart = true,
                    readyMessage = "无障碍辅助链路已就绪（${status.accessibilityDetail}）。",
                    implementationSummary = "无障碍当前只作为兼容旧配置时的辅助链路保留。它会在系统剪贴板回调之外，结合界面交互触发补检查，但不再作为正式推荐的后台同步主模式。当前状态：${status.accessibilityDetail}。",
                )
            } else {
                ClipboardModeSupport(
                    canStart = false,
                    readyMessage = "",
                    blockedMessage = context.getString(R.string.runtime_mode_accessibility_blocked),
                    implementationSummary = "无障碍当前只作为兼容旧配置时的辅助链路保留；若历史配置仍落在这里，需要先开启系统无障碍服务。正式推荐模式请改用前台服务、原键盘发送或悬浮窗。",
                )
            }
        }

        SettingsStore.CLIPBOARD_MODE_SHIZUKU -> {
            when {
                !status.shizukuInstalled -> ClipboardModeSupport(
                    canStart = false,
                    readyMessage = "",
                    blockedMessage = context.getString(R.string.runtime_mode_shizuku_blocked),
                    implementationSummary = "当前设备还没有可用的 Shizuku 环境，请先安装并启动 Shizuku，再继续使用辅助复制。",
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
                    implementationSummary = buildString {
                        append("Shizuku 辅助模式会优先尝试辅助读取剪贴板并自动上传；")
                        if (config.shizukuAssistEnabled) {
                            append("自动复制后上传已开启")
                            if (!config.shizukuAutoUploadEnabled) {
                                append("，但当前关闭了自动上传")
                            }
                            append("。")
                        } else {
                            append("当前已关闭辅助复制，仍可保留手动发送和其它模式兜底。")
                        }
                        if (config.shizukuLightPromptEnabled) {
                            append("失败时会显示轻量提示。")
                        }
                        if (config.shizukuFallbackFloatingEnabled) {
                            append("失败时会自动打开悬浮发送助手。")
                        }
                    },
                )
            }
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

        SettingsStore.CLIPBOARD_MODE_IME_BACKGROUND -> {
            if (status.imeEnabled) {
                ClipboardModeSupport(
                    canStart = true,
                    readyMessage = context.getString(R.string.runtime_mode_ime_background_ready),
                    implementationSummary = "原键盘后台发送模式利用输入法权限获取后台剪贴板访问能力，但无需用户切换默认键盘。启用输入法后即可在后台自动监听剪贴板变化并同步。",
                )
            } else {
                ClipboardModeSupport(
                    canStart = false,
                    readyMessage = "",
                    blockedMessage = context.getString(R.string.runtime_mode_ime_background_blocked),
                    implementationSummary = "原键盘后台发送模式需要先在系统输入法设置中启用\"云剪同步\"，但不需要切换为默认键盘。启用后即可获得后台剪贴板访问权限。",
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
