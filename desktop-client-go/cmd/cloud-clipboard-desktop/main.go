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
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/tray"
)

func main() {
	defaultConfig := filepath.Join(".", "config.json")
	configPath := flag.String("config", defaultConfig, "desktop client config path")
	headless := flag.Bool("headless", false, "run without tray")
	flag.Parse()

	logger := log.New(os.Stdout, "[desktop-go] ", log.LstdFlags|log.Lmsgprefix)
	cfg, err := config.Load(*configPath)
	if err != nil {
		logger.Fatalf("加载配置失败: %v", err)
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
