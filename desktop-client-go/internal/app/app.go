package app

import (
	"context"
	"log"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/syncclient"
)

type App struct {
	logger *log.Logger
	cfg    config.Config
}

func New(logger *log.Logger, cfg config.Config) *App {
	return &App{
		logger: logger,
		cfg:    cfg,
	}
}

func (a *App) Run(ctx context.Context) error {
	client := syncclient.New(a.cfg, a.logger)
	a.logger.Printf("桌面同步客户端启动，服务端: %s 房间: %s 设备: %s", a.cfg.ServerBase, a.cfg.Room, a.cfg.DeviceName)
	return client.Run(ctx)
}
