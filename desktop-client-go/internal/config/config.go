package config

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/google/uuid"
)

type Config struct {
	ServerBase           string        `json:"serverBase"`
	Room                 string        `json:"room"`
	RoomPassword         string        `json:"roomPassword"`
	DeviceName           string        `json:"deviceName"`
	DeviceID             string        `json:"deviceId"`
	PollInterval         time.Duration `json:"pollInterval"`
	NoticeMode           string        `json:"noticeMode"`
	PanelAddress         string        `json:"panelAddress"`
	OpenPanelOnLaunch    bool          `json:"openPanelOnLaunch"`
	DownloadDir          string        `json:"downloadDir"`
	ReconnectDelay       time.Duration `json:"reconnectDelay"`
	MaxReconnectAttempts int           `json:"maxReconnectAttempts"`
}

func Default() Config {
	host, _ := os.Hostname()
	if strings.TrimSpace(host) == "" {
		host = "Desktop Client"
	}
	return Config{
		ServerBase:           "http://127.0.0.1:9501",
		Room:                 "",
		RoomPassword:         "",
		DeviceName:           host,
		DeviceID:             uuid.NewString(),
		PollInterval:         800 * time.Millisecond,
		NoticeMode:           "popup",
		PanelAddress:         "127.0.0.1:9530",
		OpenPanelOnLaunch:    true,
		DownloadDir:          defaultDownloadDir(),
		ReconnectDelay:       2 * time.Second,
		MaxReconnectAttempts: 3,
	}
}

func Load(path string) (Config, error) {
	cfg := Default()
	data, err := os.ReadFile(path)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			if err := Save(path, cfg); err != nil {
				return Config{}, err
			}
			return cfg, nil
		}
		return Config{}, err
	}
	if err := json.Unmarshal(data, &cfg); err != nil {
		return Config{}, err
	}
	cfg.normalize()
	return cfg, nil
}

func Save(path string, cfg Config) error {
	cfg.normalize()
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	data, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(path, data, 0o644)
}

func (c *Config) normalize() {
	def := Default()
	c.ServerBase = strings.TrimRight(strings.TrimSpace(c.ServerBase), "/")
	if c.ServerBase == "" {
		c.ServerBase = def.ServerBase
	}
	c.Room = strings.TrimSpace(c.Room)
	c.RoomPassword = strings.TrimSpace(c.RoomPassword)
	c.DeviceName = strings.TrimSpace(c.DeviceName)
	if c.DeviceName == "" {
		c.DeviceName = def.DeviceName
	}
	c.DeviceID = strings.TrimSpace(c.DeviceID)
	if c.DeviceID == "" {
		c.DeviceID = def.DeviceID
	}
	if c.PollInterval <= 0 {
		c.PollInterval = def.PollInterval
	}
	if c.PollInterval < 200*time.Millisecond {
		c.PollInterval = 200 * time.Millisecond
	}
	if c.PollInterval > 3*time.Second {
		c.PollInterval = 3 * time.Second
	}
	c.NoticeMode = normalizeNoticeMode(c.NoticeMode)
	c.PanelAddress = strings.TrimSpace(c.PanelAddress)
	if c.PanelAddress == "" {
		c.PanelAddress = def.PanelAddress
	}
	c.DownloadDir = strings.TrimSpace(c.DownloadDir)
	if c.DownloadDir == "" {
		c.DownloadDir = def.DownloadDir
	}
	if c.ReconnectDelay <= 0 {
		c.ReconnectDelay = def.ReconnectDelay
	}
	if c.ReconnectDelay < 500*time.Millisecond {
		c.ReconnectDelay = 500 * time.Millisecond
	}
	if c.ReconnectDelay > 30*time.Second {
		c.ReconnectDelay = 30 * time.Second
	}
	if c.MaxReconnectAttempts <= 0 {
		c.MaxReconnectAttempts = def.MaxReconnectAttempts
	}
	if c.MaxReconnectAttempts > 20 {
		c.MaxReconnectAttempts = 20
	}
}

func (c *Config) Normalize() {
	c.normalize()
}

func normalizeNoticeMode(value string) string {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "off":
		return "off"
	case "log":
		return "log"
	default:
		return "popup"
	}
}

func defaultDownloadDir() string {
	home, err := os.UserHomeDir()
	if err != nil || strings.TrimSpace(home) == "" {
		return ".\\downloads"
	}
	return filepath.Join(home, "Downloads", "CloudClipboard")
}
