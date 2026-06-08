package app

import "log"

type windowsTipNotifier struct {
	logger *log.Logger
	configPath string
	width  int
	height int
	theme  string
	left   int
	top    int
}

func (n windowsTipNotifier) Notify(title string, body string) {
	if err := showWindowsTip(title, body, "", "", "", "", "", 5, n.width, n.height, n.theme, n.left, n.top, n.configPath); err != nil && n.logger != nil {
		n.logger.Printf("右下角提示失败: %v", err)
	}
}
