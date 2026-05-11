package shellmenu

import (
	"context"
	"log"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
)

type Manager interface {
	Update(config.Config)
}

func Start(ctx context.Context, logger *log.Logger, cfg config.Config, exePath string, configPath string) Manager {
	return start(ctx, logger, cfg, exePath, configPath)
}
