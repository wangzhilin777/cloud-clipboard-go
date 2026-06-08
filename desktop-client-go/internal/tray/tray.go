//go:build windows

package tray

import (
	"context"
	"fmt"
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
			detail, err := handleOpenPanel(backend)
			runTrayAction(logger, detail, err, "托盘打开控制面板")
		case <-reconnectItem.ClickedCh:
			backend.RequestReconnect()
		case <-sendClipboardItem.ClickedCh:
			detail, err := handleSendClipboardText(backend)
			runTrayAction(logger, detail, err, "托盘发送剪贴板文本")
		case <-confirmClipboardFilesItem.ClickedCh:
			detail, err := handleConfirmClipboardFiles(backend)
			runTrayAction(logger, detail, err, "托盘发送待确认剪贴板文件")
		case <-fetchLatestTextItem.ClickedCh:
			detail, err := handleFetchLatestText(backend)
			runTrayAction(logger, detail, err, "托盘拉取最新文本")
		case <-fetchLatestFileItem.ClickedCh:
			detail, err := handleFetchLatestFileToClipboard(backend)
			runTrayAction(logger, detail, err, "托盘拉取最新文件到剪贴板")
		case <-downloadLatestFileItem.ClickedCh:
			detail, err := handleDownloadLatestFile(backend)
			runTrayAction(logger, detail, err, "托盘下载最新文件")
		case <-openDownloadDirItem.ClickedCh:
			detail, err := handleOpenDownloadDir(backend)
			runTrayAction(logger, detail, err, "托盘打开下载目录")
		case <-clearDownloadDirItem.ClickedCh:
			detail, err := handleClearDownloadDir(backend)
			runTrayAction(logger, detail, err, "托盘清空下载缓存")
		case <-sendFilesItem.ClickedCh:
			detail, err := handleSendFiles(backend)
			runTrayAction(logger, detail, err, "托盘发送文件")
		case <-quitItem.ClickedCh:
			if stop != nil {
				stop()
			}
			systray.Quit()
			return
		}
	}
}

func runTrayAction(logger *log.Logger, detail string, err error, prefix string) {
	if err != nil {
		logger.Printf("%s失败: %v", prefix, err)
		return
	}
	if strings.TrimSpace(detail) == "" {
		logger.Printf("%s成功", prefix)
		return
	}
	logger.Printf("%s成功: %s", prefix, detail)
}

func handleOpenPanel(backend Backend) (string, error) {
	if err := backend.OpenPanel(); err != nil {
		return "", err
	}
	return "", nil
}

func handleSendClipboardText(backend Backend) (string, error) {
	text, err := backend.SendText("", true)
	if err != nil {
		return "", err
	}
	return previewText(text), nil
}

func handleConfirmClipboardFiles(backend Backend) (string, error) {
	files, err := backend.ConfirmPendingClipboardFiles()
	if err != nil {
		return "", err
	}
	return joinPreview(files), nil
}

func handleFetchLatestText(backend Backend) (string, error) {
	text, err := backend.FetchLatestText()
	if err != nil {
		return "", err
	}
	return previewText(text), nil
}

func handleFetchLatestFileToClipboard(backend Backend) (string, error) {
	return backend.FetchLatestFileToClipboard()
}

func handleDownloadLatestFile(backend Backend) (string, error) {
	return backend.DownloadLatestFile()
}

func handleOpenDownloadDir(backend Backend) (string, error) {
	if err := backend.OpenDownloadDir(); err != nil {
		return "", err
	}
	return "", nil
}

func handleClearDownloadDir(backend Backend) (string, error) {
	count, err := backend.ClearDownloadDir()
	if err != nil {
		return "", err
	}
	return fmt.Sprintf("%d 项", count), nil
}

func handleSendFiles(backend Backend) (string, error) {
	files, err := backend.SendFiles(nil)
	if err != nil {
		return "", err
	}
	return joinPreview(files), nil
}

func joinPreview(values []string) string {
	if len(values) == 0 {
		return ""
	}
	return strings.Join(values, ", ")
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

