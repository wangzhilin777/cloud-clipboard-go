package main

import (
	"bytes"
	"context"
	"encoding/json"
	"flag"
	"log"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"strings"
	"syscall"
	"time"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/app"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/fileclip"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/transfer"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/tray"
)

func main() {
	defaultConfig := filepath.Join(".", "config.json")
	configPath := flag.String("config", defaultConfig, "desktop client config path")
	headless := flag.Bool("headless", false, "run without tray")
	shellSend := flag.String("shell-send", "", "one-shot send file path")
	shellDownloadDir := flag.String("shell-download-dir", "", "one-shot download latest file to dir")
	shellFetchLatestFile := flag.Bool("shell-fetch-latest-file", false, "one-shot fetch latest file to clipboard")
	flag.Parse()

	logger := log.New(os.Stdout, "[desktop-go] ", log.LstdFlags|log.Lmsgprefix)
	cfg, err := config.Load(*configPath)
	if err != nil {
		logger.Fatalf("加载配置失败: %v", err)
	}
	if *shellSend != "" || *shellDownloadDir != "" || *shellFetchLatestFile {
		notifier := app.BuildNotifier(cfg, logger, *configPath)
		message, err := runShellAction(logger, cfg, *shellSend, *shellDownloadDir, *shellFetchLatestFile)
		if err != nil {
			notifier.Notify("云剪同步", "右键动作失败："+err.Error())
			logger.Fatalf("执行右键动作失败: %v", err)
		}
		if cfg.SuccessNoticeEnabled && message != "" {
			notifier.Notify("云剪同步", message)
		}
		return
	}

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	desktopApp := app.New(logger, cfg, *configPath)
	if *headless {
		if err := desktopApp.Run(ctx); err != nil && err != context.Canceled {
			logger.Fatalf("桌面同步客户端退出: %v", err)
		}
		return
	}

	errCh := make(chan error, 1)
	go func() {
		errCh <- desktopApp.Run(ctx)
		cancel()
	}()

	go func() {
		<-ctx.Done()
	}()

	tray.Run(ctx, logger, desktopApp, cancel)

	if err := <-errCh; err != nil && err != context.Canceled {
		logger.Fatalf("桌面同步客户端退出: %v", err)
	}
}

func runShellAction(logger *log.Logger, cfg config.Config, shellSend string, shellDownloadDir string, shellFetchLatestFile bool) (string, error) {
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
		notifyPanelSuppressClipboardFiles(logger, cfg.PanelAddress, []string{result.Path})
		if err := fileclip.SetFileList([]string{result.Path}); err != nil {
			logger.Printf("文件剪贴板写入返回异常，但下载文件已落地: %v", err)
		}
		return "已拉取最新文件到本地剪贴板", nil
	}
	return "", nil
}

func notifyPanelSuppressClipboardFiles(logger *log.Logger, panelAddress string, paths []string) {
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
