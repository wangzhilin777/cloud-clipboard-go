package shellmenu

import (
	"context"
	"log"
	"time"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
)

type Status struct {
	Supported bool   `json:"supported"`
	Enabled   bool   `json:"enabled"`
	Ready     bool   `json:"ready"`
	Message   string `json:"message"`
	LastError string `json:"lastError,omitempty"`
	UpdatedAt int64  `json:"updatedAt,omitempty"`
}

func (s Status) withTimestamp() Status {
	s.UpdatedAt = time.Now().UnixMilli()
	return s
}

type Manager interface {
	Update(config.Config)
	Status() Status
}

func Start(ctx context.Context, logger *log.Logger, cfg config.Config, exePath string, configPath string) Manager {
	return start(ctx, logger, cfg, exePath, configPath)
}
