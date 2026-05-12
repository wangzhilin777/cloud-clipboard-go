package app

import (
	"log"

	"github.com/gen2brain/beeep"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
)

type Notifier interface {
	Notify(title string, body string)
}

type noopNotifier struct{}

func (noopNotifier) Notify(title string, body string) {}

type logNotifier struct {
	logger *log.Logger
}

func (n logNotifier) Notify(title string, body string) {
	n.logger.Printf("%s: %s", title, body)
}

type beeepNotifier struct {
	logger *log.Logger
}

func BuildNotifier(cfg config.Config, logger *log.Logger, configPath string) Notifier {
	return buildNotifier(cfg, logger, configPath)
}

func (n beeepNotifier) Notify(title string, body string) {
	if err := beeep.Notify(title, body, ""); err != nil {
		n.logger.Printf("桌面通知失败: %v", err)
	}
}
