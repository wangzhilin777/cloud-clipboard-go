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
	SendFiles(paths []string) ([]string, error)
	SendText(text string, fromClipboard bool) (string, error)
}

func Run(ctx context.Context, logger *log.Logger, backend Backend, stop func()) error {
	if backend == nil {
		return nil
	}

	tray, err := systray.New()
	if err != nil {
		return err
	}
	if err := tray.Show(0, "Cloud Clipboard Desktop"); err != nil {
		return err
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

	go refreshTooltipLoop(ctx, tray, backend)
	go func() {
		<-ctx.Done()
		if err := tray.Stop(); err != nil {
			logger.Printf("托盘停止失败: %v", err)
		}
	}()

	return tray.Run()
}

func refreshTooltipLoop(ctx context.Context, tray *systray.Systray, backend Backend) {
	ticker := time.NewTicker(2 * time.Second)
	defer ticker.Stop()

	updateTrayState(tray, backend.Status())
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			updateTrayState(tray, backend.Status())
		}
	}
}

func updateTrayState(tray *systray.Systray, status panel.StatusView) {
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
		return "已信任"
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
