package app

import "log"

type windowsTipNotifier struct {
	logger *log.Logger
}

func (n windowsTipNotifier) Notify(title string, body string) {
	if err := showWindowsTip(title, body, "", "", "", "", 5); err != nil && n.logger != nil {
		n.logger.Printf("右下角提示失败: %v", err)
	}
}
