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
)

func main() {
	defaultConfig := filepath.Join(".", "config.json")
	configPath := flag.String("config", defaultConfig, "desktop client config path")
	flag.Parse()

	logger := log.New(os.Stdout, "[desktop-go] ", log.LstdFlags|log.Lmsgprefix)
	cfg, err := config.Load(*configPath)
	if err != nil {
		logger.Fatalf("加载配置失败: %v", err)
	}

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	if err := app.New(logger, cfg, *configPath).Run(ctx); err != nil && err != context.Canceled {
		logger.Fatalf("桌面同步客户端退出: %v", err)
	}
}
