package app

import (
	"context"
	"log"
	"path/filepath"
	"strings"
	"time"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/syncclient"
)

type App struct {
	logger    *log.Logger
	cfg       config.Config
	configDir string
	state     *StateStore
	notifier  Notifier
}

func New(logger *log.Logger, cfg config.Config, configPath string) *App {
	configDir := filepath.Dir(configPath)
	if configDir == "" {
		configDir = "."
	}
	return &App{
		logger:    logger,
		cfg:       cfg,
		configDir: configDir,
		state:     NewStateStore(filepath.Join(configDir, "state.json")),
		notifier:  buildNotifier(cfg, logger),
	}
}

func (a *App) Run(ctx context.Context) error {
	client := syncclient.New(a.cfg, a.logger, a)
	a.logger.Printf("桌面同步客户端启动，服务端: %s 房间: %s 设备: %s", a.cfg.ServerBase, a.cfg.Room, a.cfg.DeviceName)
	_ = a.state.Save(StateSnapshot{Status: "starting"})
	return client.Run(ctx)
}

func buildNotifier(cfg config.Config, logger *log.Logger) Notifier {
	switch strings.ToLower(strings.TrimSpace(cfg.NoticeMode)) {
	case "off":
		return noopNotifier{}
	case "log":
		return logNotifier{logger: logger}
	default:
		return beeepNotifier{logger: logger}
	}
}

func (a *App) OnConnecting() {
	_ = a.state.Save(StateSnapshot{Status: "connecting"})
}

func (a *App) OnConnected() {
	_ = a.state.Save(StateSnapshot{Connected: true, Status: "connected"})
}

func (a *App) OnTrustedChanged(trusted bool) {
	status := "pending"
	if trusted {
		status = "trusted"
		a.notifier.Notify("Cloud Clipboard", "桌面端已获批准，可以开始同步文本。")
	} else {
		a.notifier.Notify("Cloud Clipboard", "设备已连接，等待网页端批准。")
	}
	_ = a.state.Save(StateSnapshot{
		Connected: true,
		Trusted:   trusted,
		Status:    status,
	})
}

func (a *App) OnRemoteText(text string) {
	_ = a.state.Save(StateSnapshot{
		Connected:        true,
		Trusted:          true,
		Status:           "trusted",
		LastRemoteTextAt: time.Now().UnixMilli(),
	})
}

func (a *App) OnPayloadNotice(kind string, title string) {
	if strings.TrimSpace(title) == "" {
		title = "远端内容"
	}
	displayKind := "文件"
	if strings.EqualFold(kind, "image") {
		displayKind = "图片"
	}
	a.notifier.Notify("Cloud Clipboard", "收到远端"+displayKind+"："+title)
	_ = a.state.Save(StateSnapshot{
		Connected:        true,
		Trusted:          true,
		Status:           "trusted",
		LastPayloadTitle: title,
		LastPayloadKind:  kind,
		LastPayloadAt:    time.Now().UnixMilli(),
	})
}

func (a *App) OnError(err error) {
	if err == nil {
		return
	}
	_ = a.state.Save(StateSnapshot{
		Status:    "error",
		LastError: err.Error(),
	})
}
