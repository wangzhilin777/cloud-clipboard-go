//go:build !windows

package hotkey

import (
	"context"
	"log"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
)

type noopManager struct{}

func (noopManager) Update(config.Config) {}

func start(_ context.Context, _ *log.Logger, _ config.Config, _ Actions) Manager {
	return noopManager{}
}
