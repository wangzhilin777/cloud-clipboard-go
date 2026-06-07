package desktopcmd

import (
	"bytes"
	"context"
	"encoding/json"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/app"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/fileclip"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/transfer"
)

func DefaultConfigPath() string {
	if exePath, err := os.Executable(); err == nil {
		exeDir := filepath.Dir(exePath)
		// `go run` starts a temporary binary from Go's build cache; keep the
		// development config beside the current working directory in that case.
		if !strings.Contains(strings.ToLower(exeDir), "go-build") {
			return filepath.Join(exeDir, "config.json")
		}
	}
	if cwd, err := os.Getwd(); err == nil && strings.TrimSpace(cwd) != "" {
		return filepath.Join(cwd, "config.json")
	}
	return filepath.Join(".", "config.json")
}

func RunShellAction(logger *log.Logger, cfg config.Config, shellSend string, shellDownloadDir string, shellFetchLatestFile bool) (string, error) {
	sender := transfer.NewSender(cfg, logger)
	if shellSend != "" {
		results, err := sender.SendFiles(context.Background(), []string{shellSend})
		if err != nil {
			return "", err
		}
		if len(results) == 0 {
			return "已发送文件到服务器", nil
		}
		return "已发送文件到服务器：" + results[0].Name, nil
	}
	if shellDownloadDir != "" {
		cfg.DownloadDir = shellDownloadDir
		cfg.Normalize()
		sender = transfer.NewSender(cfg, logger)
		result, err := sender.DownloadLatestFile(context.Background())
		if err != nil {
			return "", err
		}
		return "已下载最新文件到：" + result.Path, nil
	}
	if shellFetchLatestFile {
		result, err := sender.DownloadLatestFile(context.Background())
		if err != nil {
			return "", err
		}
		NotifyPanelSuppressClipboardFiles(logger, cfg.PanelAddress, []string{result.Path})
		if err := fileclip.SetFileList([]string{result.Path}); err != nil {
			logger.Printf("文件剪贴板写入返回异常，但下载文件已落地: %v", err)
		}
		return "已拉取最新文件到本地剪贴板", nil
	}
	return "", nil
}

func NotifyPanelSuppressClipboardFiles(logger *log.Logger, panelAddress string, paths []string) {
	panelAddress = strings.TrimSpace(panelAddress)
	if panelAddress == "" || len(paths) == 0 {
		return
	}
	baseURL := panelAddress
	if !strings.HasPrefix(baseURL, "http://") && !strings.HasPrefix(baseURL, "https://") {
		baseURL = "http://" + baseURL
	}
	body, err := json.Marshal(map[string]any{"paths": paths})
	if err != nil {
		logger.Printf("构造剪贴板抑制请求失败: %v", err)
		return
	}
	client := &http.Client{Timeout: 800 * time.Millisecond}
	resp, err := client.Post(strings.TrimRight(baseURL, "/")+"/api/suppress-clipboard-files", "application/json", bytes.NewReader(body))
	if err != nil {
		logger.Printf("通知主进程忽略本次文件剪贴板变化失败: %v", err)
		return
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		logger.Printf("通知主进程忽略本次文件剪贴板变化返回异常状态: %s", resp.Status)
	}
}

func RunPanel(ctx context.Context, logger *log.Logger, cfg config.Config, configPath string) error {
	return app.New(logger, cfg, configPath).Run(ctx)
}
