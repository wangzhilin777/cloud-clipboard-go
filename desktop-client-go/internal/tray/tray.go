package tray

import (
	"context"
	"log"
	"strings"
	"time"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/panel"
	"github.com/tadvi/systray"
)

type Backend interface {
	Status() panel.StatusView
	RequestReconnect()
	OpenPanel() error
	OpenDownloadDir() error
	ClearDownloadDir() (int, error)
	ConfirmPendingClipboardFiles() ([]string, error)
	SendFiles(paths []string) ([]string, error)
	SendText(text string, fromClipboard bool) (string, error)
	FetchLatestText() (string, error)
	FetchLatestFileToClipboard() (string, error)
	DownloadLatestFile() (string, error)
}

func Run(ctx context.Context, logger *log.Logger, backend Backend, stop func()) error {
	if backend == nil {
		return nil
	}

	tray, err := systray.New()
	if err != nil {
		return err
	}
	iconPath, iconErr := ensureTrayIconFile()
	if iconErr == nil {
		if err := tray.ShowCustom(iconPath, "Cloud Clipboard Desktop"); err != nil {
			return err
		}
	} else {
		logger.Printf("加载托盘图标失败，回退默认图标: %v", iconErr)
		if err := tray.Show(0, "Cloud Clipboard Desktop"); err != nil {
			return err
		}
	}
	tray.OnClick(func() {
		if err := backend.OpenPanel(); err != nil {
			logger.Printf("托盘左键打开控制面板失败: %v", err)
		}
	})

	tray.AppendMenu("状态：启动中", func() {})
	if len(tray.Menu) > 0 {
		tray.Menu[0].Disabled = true
	}
	tray.AppendSeparator()
	tray.AppendMenu("打开控制面板", func() {
		if err := backend.OpenPanel(); err != nil {
			logger.Printf("托盘打开控制面板失败: %v", err)
		}
	})
	tray.AppendMenu("立即重连", func() {
		backend.RequestReconnect()
	})
	tray.AppendSeparator()
	tray.AppendMenu("发送当前剪贴板文本", func() {
		text, err := backend.SendText("", true)
		if err != nil {
			logger.Printf("托盘发送剪贴板文本失败: %v", err)
			return
		}
		logger.Printf("托盘发送剪贴板文本成功: %s", previewText(text))
	})
	tray.AppendMenu("发送待确认剪贴板文件", func() {
		files, err := backend.ConfirmPendingClipboardFiles()
		if err != nil {
			logger.Printf("托盘发送待确认剪贴板文件失败: %v", err)
			return
		}
		if len(files) > 0 {
			logger.Printf("托盘发送待确认剪贴板文件成功: %s", strings.Join(files, ", "))
		}
	})
	pendingClipboardMenuIndex := len(tray.Menu) - 1
	if pendingClipboardMenuIndex >= 0 && tray.Menu[pendingClipboardMenuIndex] != nil {
		tray.Menu[pendingClipboardMenuIndex].Disabled = true
	}
	tray.AppendSeparator()
	tray.AppendMenu("拉取最新文本到剪贴板", func() {
		text, err := backend.FetchLatestText()
		if err != nil {
			logger.Printf("托盘拉取最新文本失败: %v", err)
			return
		}
		logger.Printf("托盘拉取最新文本成功: %s", previewText(text))
	})
	tray.AppendMenu("拉取最新文件到剪贴板", func() {
		path, err := backend.FetchLatestFileToClipboard()
		if err != nil {
			logger.Printf("托盘拉取最新文件到剪贴板失败: %v", err)
			return
		}
		logger.Printf("托盘拉取最新文件到剪贴板成功: %s", path)
	})
	tray.AppendMenu("下载最新文件到本机", func() {
		path, err := backend.DownloadLatestFile()
		if err != nil {
			logger.Printf("托盘下载最新文件失败: %v", err)
			return
		}
		logger.Printf("托盘下载最新文件成功: %s", path)
	})
	tray.AppendMenu("打开下载目录", func() {
		if err := backend.OpenDownloadDir(); err != nil {
			logger.Printf("托盘打开下载目录失败: %v", err)
			return
		}
		logger.Printf("托盘已打开下载目录")
	})
	tray.AppendMenu("清空下载缓存", func() {
		count, err := backend.ClearDownloadDir()
		if err != nil {
			logger.Printf("托盘清空下载缓存失败: %v", err)
			return
		}
		logger.Printf("托盘已清空下载缓存: %d 项", count)
	})
	tray.AppendSeparator()
	tray.AppendMenu("发送文件到同步房间", func() {
		files, err := backend.SendFiles(nil)
		if err != nil {
			logger.Printf("托盘发送文件失败: %v", err)
			return
		}
		if len(files) > 0 {
			logger.Printf("托盘发送文件成功: %s", strings.Join(files, ", "))
		}
	})
	tray.AppendSeparator()
	tray.AppendMenu("退出", func() {
		if stop != nil {
			stop()
		}
		if err := tray.Stop(); err != nil {
			logger.Printf("托盘退出失败: %v", err)
		}
	})

	go refreshTooltipLoop(ctx, tray, backend, pendingClipboardMenuIndex)
	go func() {
		<-ctx.Done()
		if err := tray.Stop(); err != nil {
			logger.Printf("托盘停止失败: %v", err)
		}
	}()

	return tray.Run()
}

func refreshTooltipLoop(ctx context.Context, tray *systray.Systray, backend Backend, pendingClipboardMenuIndex int) {
	ticker := time.NewTicker(2 * time.Second)
	defer ticker.Stop()

	updateTrayState(tray, backend.Status(), pendingClipboardMenuIndex)
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			updateTrayState(tray, backend.Status(), pendingClipboardMenuIndex)
		}
	}
}

func updateTrayState(tray *systray.Systray, status panel.StatusView, pendingClipboardMenuIndex int) {
	if tray == nil {
		return
	}
	text := "Cloud Clipboard / " + normalizeStatus(status.State.Status)
	if status.State.Connected {
		text = text + " / 已连接"
	}
	if strings.TrimSpace(status.Config.DeviceName) != "" {
		text = text + " / " + strings.TrimSpace(status.Config.DeviceName)
	}
	_ = tray.SetTooltip(text)
	if len(tray.Menu) > 0 && tray.Menu[0] != nil {
		tray.Menu[0].Label = "状态：" + normalizeStatusLine(status)
	}
	if pendingClipboardMenuIndex >= 0 && pendingClipboardMenuIndex < len(tray.Menu) && tray.Menu[pendingClipboardMenuIndex] != nil {
		files := status.State.PendingClipboardFiles
		if len(files) == 0 {
			tray.Menu[pendingClipboardMenuIndex].Label = "发送待确认剪贴板文件"
			tray.Menu[pendingClipboardMenuIndex].Disabled = true
			return
		}
		tray.Menu[pendingClipboardMenuIndex].Label = "发送待确认剪贴板文件：" + formatPendingClipboardName(files)
		tray.Menu[pendingClipboardMenuIndex].Disabled = false
	}
}

func normalizeStatusLine(status panel.StatusView) string {
	parts := []string{normalizeStatus(status.State.Status)}
	if status.State.Connected {
		parts = append(parts, "已连接")
	}
	if strings.TrimSpace(status.Config.DeviceName) != "" {
		parts = append(parts, strings.TrimSpace(status.Config.DeviceName))
	}
	return strings.Join(parts, " / ")
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

func formatPendingClipboardName(files []string) string {
	if len(files) == 0 {
		return "-"
	}
	name := strings.TrimSpace(files[0])
	name = strings.ReplaceAll(name, "/", "\\")
	parts := strings.Split(name, "\\")
	if len(parts) > 0 && strings.TrimSpace(parts[len(parts)-1]) != "" {
		name = strings.TrimSpace(parts[len(parts)-1])
	}
	if len(files) == 1 {
		return name
	}
	return name + " 等"
}
