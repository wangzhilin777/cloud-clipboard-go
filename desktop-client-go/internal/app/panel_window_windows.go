//go:build windows

package app

import (
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

var windowsPanelBrowsers = []string{
	`C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe`,
	`C:\Program Files\Microsoft\Edge\Application\msedge.exe`,
	`C:\Program Files\Google\Chrome\Application\chrome.exe`,
	`C:\Program Files (x86)\Google\Chrome\Application\chrome.exe`,
}

func startPanelWindow(panelURL string) error {
	panelURL = strings.TrimSpace(panelURL)
	if panelURL == "" {
		return errors.New("控制面板地址为空")
	}
	title := "云剪同步桌面端"
	if activatePanelWindow(title) == nil {
		return nil
	}
	browserPath, err := findPanelBrowser()
	if err != nil {
		return err
	}
	userDataDir := filepath.Join(os.TempDir(), "cloud-clipboard-panel-window")
	if err := os.MkdirAll(userDataDir, 0o755); err != nil {
		return err
	}
	args := []string{
		"--app=" + panelURL,
		"--window-size=1120,860",
		"--disable-features=msEdgeSidebarV2",
		"--user-data-dir=" + userDataDir,
	}
	cmd := exec.Command(browserPath, args...)
	if err := cmd.Start(); err != nil {
		return err
	}
	return nil
}

func findPanelBrowser() (string, error) {
	for _, path := range windowsPanelBrowsers {
		if _, err := os.Stat(path); err == nil {
			return path, nil
		}
	}
	return "", fmt.Errorf("未找到可用的客户端窗口宿主，请安装 Microsoft Edge 或 Google Chrome")
}

func activatePanelWindow(title string) error {
	script := fmt.Sprintf(
		`Add-Type -AssemblyName Microsoft.VisualBasic; [Microsoft.VisualBasic.Interaction]::AppActivate(%q) | Out-Null`,
		title,
	)
	cmd := exec.Command("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script)
	if err := cmd.Run(); err != nil {
		return err
	}
	return nil
}
