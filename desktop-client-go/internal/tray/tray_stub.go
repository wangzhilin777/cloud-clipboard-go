//go:build !windows

package tray

import (
	"context"
	"log"
)

func Run(ctx context.Context, logger *log.Logger, backend Backend, stop func()) error {
	if logger != nil {
		logger.Printf("当前平台暂未启用托盘图标，桌面同步客户端将以后台模式运行。")
		logger.Printf("本地控制面板和快捷命令仍可使用；如需退出，请结束进程或发送中断信号。")
	}
	<-ctx.Done()
	if stop != nil {
		stop()
	}
	return ctx.Err()
}
