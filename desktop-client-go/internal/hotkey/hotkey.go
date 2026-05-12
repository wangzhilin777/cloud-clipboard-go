package hotkey

import (
	"context"
	"log"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
)

type Actions interface {
	SendText(text string, fromClipboard bool) (string, error)
	FetchLatestText() (string, error)
	FetchLatestFileToClipboard() (string, error)
	DownloadLatestFile() (string, error)
}

type Manager interface {
	Update(config.Config)
}

func Start(ctx context.Context, logger *log.Logger, cfg config.Config, actions Actions) Manager {
	return start(ctx, logger, cfg, actions)
}
