//go:build windows

package tray

import (
	"context"
	"log"
	"strings"
	"time"

	"github.com/getlantern/systray"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/panel"
)

func Run(ctx context.Context, logger *log.Logger, backend Backend, stop func()) error {
	if backend == nil {
		return nil
	}

	onReady := func() {
		if icon := trayIconBytes(); len(icon) > 0 {
			systray.SetIcon(icon)
		}
		systray.SetTooltip("Cloud Clipboard Desktop")
		systray.SetOnClick(func() {
			if err := backend.OpenPanel(); err != nil {
				logger.Printf("托盘左键打开控制面板失败: %v", err)
			}
		})

		statusItem := systray.AddMenuItem("状态：请查看悬停提示", "当前同步状态请查看托盘悬停提示")
		statusItem.Disable()
		systray.AddSeparator()

		openPanelItem := systray.AddMenuItem("打开控制面板", "打开云剪同步桌面控制面板")
		reconnectItem := systray.AddMenuItem("立即重连", "重新建立同步连接")
		systray.AddSeparator()

		sendClipboardItem := systray.AddMenuItem("发送当前剪贴板文本", "把当前文本同步到服务器")
		confirmClipboardFilesItem := systray.AddMenuItem("发送待确认剪贴板文件", "发送最近检测到的剪贴板文件")
		systray.AddSeparator()

		fetchLatestTextItem := systray.AddMenuItem("拉取最新文本到剪贴板", "把服务器最新文本写回本地剪贴板")
		fetchLatestFileItem := systray.AddMenuItem("拉取最新文件到剪贴板", "把服务器最新文件写回系统文件剪贴板")
		downloadLatestFileItem := systray.AddMenuItem("下载最新文件到本机", "把服务器最新文件下载到本地")
		openDownloadDirItem := systray.AddMenuItem("打开下载目录", "打开当前下载缓存目录")
		clearDownloadDirItem := systray.AddMenuItem("清空下载缓存", "清理桌面客户端下载缓存")
		systray.AddSeparator()

		sendFilesItem := systray.AddMenuItem("发送文件到同步房间", "选择并发送文件到当前同步房间")
		systray.AddSeparator()

		quitItem := systray.AddMenuItem("退出", "退出云剪同步桌面端")

		go trayEventLoop(ctx, logger, backend, stop, openPanelItem, reconnectItem, sendClipboardItem, confirmClipboardFilesItem, fetchLatestTextItem, fetchLatestFileItem, downloadLatestFileItem, openDownloadDirItem, clearDownloadDirItem, sendFilesItem, quitItem)
		go refreshTooltipLoop(ctx, backend)
	}

	systray.Run(onReady, func() {})
	return nil
}

func refreshTooltipLoop(ctx context.Context, backend Backend) {
	ticker := time.NewTicker(2 * time.Second)
	defer ticker.Stop()

	updateTrayTooltip(backend.Status())
	for {
		select {
		case <-ctx.Done():
			systray.Quit()
			return
		case <-ticker.C:
			updateTrayTooltip(backend.Status())
		}
	}
}

func updateTrayTooltip(status panel.StatusView) {
	systray.SetTooltip(buildTrayTooltip(status))
}

func trayEventLoop(
	ctx context.Context,
	logger *log.Logger,
	backend Backend,
	stop func(),
	openPanelItem *systray.MenuItem,
	reconnectItem *systray.MenuItem,
	sendClipboardItem *systray.MenuItem,
	confirmClipboardFilesItem *systray.MenuItem,
	fetchLatestTextItem *systray.MenuItem,
	fetchLatestFileItem *systray.MenuItem,
	downloadLatestFileItem *systray.MenuItem,
	openDownloadDirItem *systray.MenuItem,
	clearDownloadDirItem *systray.MenuItem,
	sendFilesItem *systray.MenuItem,
	quitItem *systray.MenuItem,
) {
	for {
		select {
		case <-ctx.Done():
			return
		case <-openPanelItem.ClickedCh:
			if err := backend.OpenPanel(); err != nil {
				logger.Printf("托盘打开控制面板失败: %v", err)
			}
		case <-reconnectItem.ClickedCh:
			backend.RequestReconnect()
		case <-sendClipboardItem.ClickedCh:
			text, err := backend.SendText("", true)
			if err != nil {
				logger.Printf("托盘发送剪贴板文本失败: %v", err)
				continue
			}
			logger.Printf("托盘发送剪贴板文本成功: %s", previewText(text))
		case <-confirmClipboardFilesItem.ClickedCh:
			files, err := backend.ConfirmPendingClipboardFiles()
			if err != nil {
				logger.Printf("托盘发送待确认剪贴板文件失败: %v", err)
				continue
			}
			if len(files) > 0 {
				logger.Printf("托盘发送待确认剪贴板文件成功: %s", strings.Join(files, ", "))
			}
		case <-fetchLatestTextItem.ClickedCh:
			text, err := backend.FetchLatestText()
			if err != nil {
				logger.Printf("托盘拉取最新文本失败: %v", err)
				continue
			}
			logger.Printf("托盘拉取最新文本成功: %s", previewText(text))
		case <-fetchLatestFileItem.ClickedCh:
			path, err := backend.FetchLatestFileToClipboard()
			if err != nil {
				logger.Printf("托盘拉取最新文件到剪贴板失败: %v", err)
				continue
			}
			logger.Printf("托盘拉取最新文件到剪贴板成功: %s", path)
		case <-downloadLatestFileItem.ClickedCh:
			path, err := backend.DownloadLatestFile()
			if err != nil {
				logger.Printf("托盘下载最新文件失败: %v", err)
				continue
			}
			logger.Printf("托盘下载最新文件成功: %s", path)
		case <-openDownloadDirItem.ClickedCh:
			if err := backend.OpenDownloadDir(); err != nil {
				logger.Printf("托盘打开下载目录失败: %v", err)
				continue
			}
			logger.Printf("托盘已打开下载目录")
		case <-clearDownloadDirItem.ClickedCh:
			count, err := backend.ClearDownloadDir()
			if err != nil {
				logger.Printf("托盘清空下载缓存失败: %v", err)
				continue
			}
			logger.Printf("托盘已清空下载缓存: %d 项", count)
		case <-sendFilesItem.ClickedCh:
			files, err := backend.SendFiles(nil)
			if err != nil {
				logger.Printf("托盘发送文件失败: %v", err)
				continue
			}
			if len(files) > 0 {
				logger.Printf("托盘发送文件成功: %s", strings.Join(files, ", "))
			}
		case <-quitItem.ClickedCh:
			if stop != nil {
				stop()
			}
			systray.Quit()
			return
		}
	}
}

func normalizeStatus(status string) string {
	switch strings.ToLower(strings.TrimSpace(status)) {
	case "trusted":
		return "已连接"
	case "pending":
		return "待批准"
	case "connected":
		return "已连接"
	case "connecting":
		return "连接中"
	case "retrying":
		return "重试中"
	case "stopped":
		return "已暂停"
	case "error":
		return "异常"
	case "starting":
		return "启动中"
	default:
		return "空闲"
	}
}

func buildTrayTooltip(status panel.StatusView) string {
	text := "Cloud Clipboard / " + normalizeStatus(status.State.Status)
	if status.State.Connected {
		text = text + " / 已连接"
	}
	if strings.TrimSpace(status.Config.DeviceName) != "" {
		text = text + " / " + strings.TrimSpace(status.Config.DeviceName)
	}
	return text
}

func previewText(text string) string {
	text = strings.TrimSpace(text)
	if text == "" {
		return "(空文本)"
	}
	runes := []rune(text)
	if len(runes) > 24 {
		return string(runes[:24]) + "..."
	}
	return text
}
