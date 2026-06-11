//go:build windows

package app

import (
	"bytes"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

var windowsPanelBrowsers = []string{
	`C:\Program Files\Google\Chrome\Application\chrome.exe`,
	`C:\Program Files (x86)\Google\Chrome\Application\chrome.exe`,
	`C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe`,
	`C:\Program Files\Microsoft\Edge\Application\msedge.exe`,
}

var detectPanelWindowRunning = isPanelWindowRunning
var activatePanelWindowFn = activatePanelWindow
var findPanelBrowserFn = findPanelBrowser

func startPanelWindow(panelURL string) error {
	panelURL = strings.TrimSpace(panelURL)
	if panelURL == "" {
		return errors.New("控制面板地址为空")
	}
	title := "云剪同步桌面端"
	if running, err := detectPanelWindowRunning(panelURL); err == nil && running && activatePanelWindowFn(title) == nil {
		return nil
	}
	browserPath, err := findPanelBrowserFn()
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
		"--no-first-run",
		"--no-default-browser-check",
		"--disable-features=msEdgeSidebarV2",
		"--user-data-dir=" + userDataDir,
	}
	cmd := exec.Command(browserPath, args...)
	if err := cmd.Start(); err != nil {
		return err
	}
	return nil
}

func isPanelWindowRunning(panelURL string) (bool, error) {
	panelURL = strings.TrimSpace(panelURL)
	if panelURL == "" {
		return false, errors.New("控制面板地址为空")
	}
	script := fmt.Sprintf(
		`$target = %s; $procs = Get-CimInstance Win32_Process -Filter "name='chrome.exe'" -ErrorAction SilentlyContinue; foreach ($proc in $procs) { if ($proc.CommandLine -like ("*--app=" + $target + "*")) { "FOUND"; exit 0 } }`,
		toPanelPSString(panelURL),
	)
	cmd := exec.Command("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script)
	var stdout bytes.Buffer
	var stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	if err := cmd.Run(); err != nil {
		message := strings.TrimSpace(stderr.String())
		if message == "" {
			message = err.Error()
		}
		return false, fmt.Errorf("检测面板窗口失败: %s", message)
	}
	return strings.Contains(stdout.String(), "FOUND"), nil
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

func toPanelPSString(value string) string {
	return "'" + strings.ReplaceAll(value, "'", "''") + "'"
}
