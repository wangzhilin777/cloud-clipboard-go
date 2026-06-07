package main

import (
	"context"
	"flag"
	"log"
	"os"
	"os/signal"
	"syscall"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/app"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/desktopcmd"
)

func main() {
	defaultConfig := desktopcmd.DefaultConfigPath()
	configPath := flag.String("config", defaultConfig, "desktop client config path")
	shellSend := flag.String("shell-send", "", "one-shot send file path")
	shellDownloadDir := flag.String("shell-download-dir", "", "one-shot download latest file to dir")
	shellFetchLatestFile := flag.Bool("shell-fetch-latest-file", false, "one-shot fetch latest file to clipboard")
	flag.Parse()

	logger := log.New(os.Stdout, "[desktop-panel] ", log.LstdFlags|log.Lmsgprefix)
	cfg, err := config.Load(*configPath)
	if err != nil {
		logger.Fatalf("加载配置失败: %v", err)
	}
	if *shellSend != "" || *shellDownloadDir != "" || *shellFetchLatestFile {
		notifier := app.BuildNotifier(cfg, logger, *configPath)
		message, err := desktopcmd.RunShellAction(logger, cfg, *shellSend, *shellDownloadDir, *shellFetchLatestFile)
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
	if err := desktopcmd.RunPanel(ctx, logger, cfg, *configPath); err != nil && err != context.Canceled {
		logger.Fatalf("桌面同步面板退出: %v", err)
	}
}
