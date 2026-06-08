//go:build !windows

package shellmenu

import (
	"context"
	"log"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
)

type noopManager struct{}

func (noopManager) Update(config.Config) {}

func (noopManager) Status() Status {
	return Status{
		Supported: false,
		Message:   "当前平台未接入右键子菜单",
	}
}

func start(_ context.Context, _ *log.Logger, _ config.Config, _ string, _ string) Manager {
	return noopManager{}
}
