//go:build windows

package app

import (
	"fmt"
	"strings"

	toast "git.sr.ht/~jackmordaunt/go-toast"
)

func (a *App) showClipboardFilesToast(paths []string, windowSeconds int) bool {
	cfg := a.currentConfig()
	panelURL, confirmURL := a.pendingClipboardToastURLs()
	if strings.TrimSpace(confirmURL) == "" {
		return false
	}
	body := clipboardToastBody(paths, windowSeconds)
	switch strings.ToLower(strings.TrimSpace(cfg.NoticeMode)) {
	case "tip":
		if err := showWindowsTip("检测到新的剪贴板文件", body, "立即发送", confirmURL, "打开面板", panelURL, windowSeconds, cfg.TipWidth, cfg.TipHeight, cfg.TipTheme, cfg.TipLeft, cfg.TipTop, a.configPath); err != nil {
			a.logger.Printf("右下角剪贴板提示失败: %v", err)
			return false
		}
		return true
	case "popup":
	default:
		return false
	}
	notification := toast.Notification{
		AppID:               "Cloud Clipboard Desktop",
		Title:               "检测到新的剪贴板文件",
		Body:                body,
		ActivationType:      toast.Protocol,
		ActivationArguments: defaultURL(panelURL, confirmURL),
		Actions: []toast.Action{
			{Type: toast.Protocol, Content: "立即发送", Arguments: confirmURL},
		},
		Duration: toast.Short,
	}
	if strings.TrimSpace(panelURL) != "" {
		notification.Actions = append(notification.Actions, toast.Action{
			Type:      toast.Protocol,
			Content:   "打开面板",
			Arguments: panelURL,
		})
	}
	if err := notification.Push(); err != nil {
		a.logger.Printf("剪贴板文件提示弹窗失败: %v", err)
		return false
	}
	return true
}

func (a *App) pendingClipboardToastURLs() (string, string) {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.panel == nil {
		return "", ""
	}
	return a.panel.URL(), a.panel.PendingClipboardConfirmURL()
}

func clipboardToastBody(paths []string, windowSeconds int) string {
	if len(paths) == 0 {
		return fmt.Sprintf("可在 %d 秒内确认发送。", windowSeconds)
	}
	name := filepathBase(paths[0])
	if len(paths) == 1 {
		return fmt.Sprintf("%s，可在 %d 秒内确认发送。", name, windowSeconds)
	}
	return fmt.Sprintf("%s 等 %d 项，可在 %d 秒内确认发送。", name, len(paths), windowSeconds)
}

func filepathBase(path string) string {
	path = strings.ReplaceAll(path, "/", "\\")
	parts := strings.Split(path, "\\")
	if len(parts) == 0 {
		return strings.TrimSpace(path)
	}
	last := strings.TrimSpace(parts[len(parts)-1])
	if last == "" {
		return strings.TrimSpace(path)
	}
	return last
}

func defaultURL(primary string, fallback string) string {
	if strings.TrimSpace(primary) != "" {
		return primary
	}
	return fallback
}
