package app

import (
	"log"

	"github.com/gen2brain/beeep"
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

func (n beeepNotifier) Notify(title string, body string) {
	if err := beeep.Notify(title, body, ""); err != nil {
		n.logger.Printf("桌面通知失败: %v", err)
	}
}
