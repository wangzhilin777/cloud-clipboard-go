package main

import (
	"context"
	"flag"
	"log"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/app"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/transfer"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/tray"
)

func main() {
	defaultConfig := filepath.Join(".", "config.json")
	configPath := flag.String("config", defaultConfig, "desktop client config path")
	headless := flag.Bool("headless", false, "run without tray")
	shellSend := flag.String("shell-send", "", "one-shot send file path")
	shellDownloadDir := flag.String("shell-download-dir", "", "one-shot download latest file to dir")
	flag.Parse()

	logger := log.New(os.Stdout, "[desktop-go] ", log.LstdFlags|log.Lmsgprefix)
	cfg, err := config.Load(*configPath)
	if err != nil {
		logger.Fatalf("加载配置失败: %v", err)
	}
	if *shellSend != "" || *shellDownloadDir != "" {
		if err := runShellAction(logger, cfg, *shellSend, *shellDownloadDir); err != nil {
			logger.Fatalf("执行右键动作失败: %v", err)
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

func runShellAction(logger *log.Logger, cfg config.Config, shellSend string, shellDownloadDir string) error {
	sender := transfer.NewSender(cfg, logger)
	if shellSend != "" {
		_, err := sender.SendFiles(context.Background(), []string{shellSend})
		return err
	}
	if shellDownloadDir != "" {
		cfg.DownloadDir = shellDownloadDir
		cfg.Normalize()
		sender = transfer.NewSender(cfg, logger)
		_, err := sender.DownloadLatestFile(context.Background())
		return err
	}
	return nil
}
