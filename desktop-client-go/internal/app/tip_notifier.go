package app

import "log"

type windowsTipNotifier struct {
	logger *log.Logger
	width  int
	height int
	theme  string
}

func (n windowsTipNotifier) Notify(title string, body string) {
	if err := showWindowsTip(title, body, "", "", "", "", 5, n.width, n.height, n.theme); err != nil && n.logger != nil {
		n.logger.Printf("右下角提示失败: %v", err)
	}
}
