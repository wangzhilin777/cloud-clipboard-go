package config

import (
	"bytes"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/google/uuid"
)

type Config struct {
	ServerBase                  string        `json:"serverBase"`
	Room                        string        `json:"room"`
	RoomPassword                string        `json:"roomPassword"`
	DeviceName                  string        `json:"deviceName"`
	DeviceID                    string        `json:"deviceId"`
	PollInterval                time.Duration `json:"pollInterval"`
	NoticeMode                  string        `json:"noticeMode"`
	PanelAddress                string        `json:"panelAddress"`
	OpenPanelOnLaunch           bool          `json:"openPanelOnLaunch"`
	DownloadDir                 string        `json:"downloadDir"`
	DownloadCacheRetention      time.Duration `json:"downloadCacheRetention"`
	ShellMenuEnabled            bool          `json:"shellMenuEnabled"`
	ClipboardFileConfirmEnabled bool          `json:"clipboardFileConfirmEnabled"`
	ClipboardFileConfirmWindow  time.Duration `json:"clipboardFileConfirmWindow"`
	OpenPanelHotkey             string        `json:"openPanelHotkey"`
	SendClipboardHotkey         string        `json:"sendClipboardHotkey"`
	FetchLatestHotkey           string        `json:"fetchLatestHotkey"`
	FetchLatestFileHotkey       string        `json:"fetchLatestFileHotkey"`
	DownloadLatestHotkey        string        `json:"downloadLatestHotkey"`
	ReconnectDelay              time.Duration `json:"reconnectDelay"`
	MaxReconnectAttempts        int           `json:"maxReconnectAttempts"`
	TipWidth                    int           `json:"tipWidth"`
	TipHeight                   int           `json:"tipHeight"`
	TipTheme                    string        `json:"tipTheme"`
	TipLeft                     int           `json:"tipLeft"`
	TipTop                      int           `json:"tipTop"`
	SuccessNoticeEnabled        bool          `json:"successNoticeEnabled"`
}

func Default() Config {
	host, _ := os.Hostname()
	if strings.TrimSpace(host) == "" {
		host = "Desktop Client"
	}
	return Config{
		ServerBase:                  "http://127.0.0.1:9501",
		Room:                        "",
		RoomPassword:                "",
		DeviceName:                  host,
		DeviceID:                    uuid.NewString(),
		PollInterval:                800 * time.Millisecond,
		NoticeMode:                  "tip",
		PanelAddress:                "127.0.0.1:9530",
		OpenPanelOnLaunch:           true,
		DownloadDir:                 defaultDownloadDir(),
		DownloadCacheRetention:      24 * time.Hour,
		ClipboardFileConfirmEnabled: true,
		ClipboardFileConfirmWindow:  8 * time.Second,
		OpenPanelHotkey:             "",
		SendClipboardHotkey:         "",
		FetchLatestHotkey:           "",
		FetchLatestFileHotkey:       "",
		DownloadLatestHotkey:        "",
		ReconnectDelay:              2 * time.Second,
		MaxReconnectAttempts:        3,
		TipWidth:                    348,
		TipHeight:                   140,
		TipTheme:                    "dark",
		TipLeft:                     -1,
		TipTop:                      -1,
		SuccessNoticeEnabled:        true,
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
	if err := applyFriendlyDurationFields(data, &cfg); err != nil {
		return Config{}, err
	}
	cfg.normalize()
	if normalized, err := marshalDiskConfig(cfg); err == nil && !bytes.Equal(bytes.TrimSpace(data), normalized) {
		if err := os.WriteFile(path, append(normalized, '\n'), 0o644); err != nil {
			return Config{}, err
		}
	}
	return cfg, nil
}

func Save(path string, cfg Config) error {
	cfg.normalize()
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	data, err := marshalDiskConfig(cfg)
	if err != nil {
		return err
	}
	return os.WriteFile(path, append(data, '\n'), 0o644)
}

type friendlyDurationFields struct {
	PollIntervalMs                int64 `json:"pollIntervalMs"`
	DownloadCacheRetentionHours   int64 `json:"downloadCacheRetentionHours"`
	ClipboardFileConfirmWindowSec int64 `json:"clipboardFileConfirmWindowSec"`
	ReconnectDelayMs              int64 `json:"reconnectDelayMs"`
}

type diskConfig struct {
	ServerBase                    string `json:"serverBase"`
	Room                          string `json:"room"`
	RoomPassword                  string `json:"roomPassword"`
	DeviceName                    string `json:"deviceName"`
	DeviceID                      string `json:"deviceId"`
	PollIntervalMs                int64  `json:"pollIntervalMs"`
	NoticeMode                    string `json:"noticeMode"`
	PanelAddress                  string `json:"panelAddress"`
	OpenPanelOnLaunch             bool   `json:"openPanelOnLaunch"`
	DownloadDir                   string `json:"downloadDir"`
	DownloadCacheRetentionHours   int64  `json:"downloadCacheRetentionHours"`
	ShellMenuEnabled              bool   `json:"shellMenuEnabled"`
	ClipboardFileConfirmEnabled   bool   `json:"clipboardFileConfirmEnabled"`
	ClipboardFileConfirmWindowSec int64  `json:"clipboardFileConfirmWindowSec"`
	OpenPanelHotkey               string `json:"openPanelHotkey"`
	SendClipboardHotkey           string `json:"sendClipboardHotkey"`
	FetchLatestHotkey             string `json:"fetchLatestHotkey"`
	FetchLatestFileHotkey         string `json:"fetchLatestFileHotkey"`
	DownloadLatestHotkey          string `json:"downloadLatestHotkey"`
	ReconnectDelayMs              int64  `json:"reconnectDelayMs"`
	MaxReconnectAttempts          int    `json:"maxReconnectAttempts"`
	TipWidth                      int    `json:"tipWidth"`
	TipHeight                     int    `json:"tipHeight"`
	TipTheme                      string `json:"tipTheme"`
	TipLeft                       int    `json:"tipLeft"`
	TipTop                        int    `json:"tipTop"`
	SuccessNoticeEnabled          bool   `json:"successNoticeEnabled"`
}

func applyFriendlyDurationFields(data []byte, cfg *Config) error {
	var fields friendlyDurationFields
	if err := json.Unmarshal(data, &fields); err != nil {
		return err
	}
	if fields.PollIntervalMs > 0 {
		cfg.PollInterval = time.Duration(fields.PollIntervalMs) * time.Millisecond
	}
	if fields.DownloadCacheRetentionHours > 0 {
		cfg.DownloadCacheRetention = time.Duration(fields.DownloadCacheRetentionHours) * time.Hour
	}
	if fields.ClipboardFileConfirmWindowSec > 0 {
		cfg.ClipboardFileConfirmWindow = time.Duration(fields.ClipboardFileConfirmWindowSec) * time.Second
	}
	if fields.ReconnectDelayMs > 0 {
		cfg.ReconnectDelay = time.Duration(fields.ReconnectDelayMs) * time.Millisecond
	}
	return nil
}

func marshalDiskConfig(cfg Config) ([]byte, error) {
	return json.MarshalIndent(diskConfig{
		ServerBase:                    cfg.ServerBase,
		Room:                          cfg.Room,
		RoomPassword:                  cfg.RoomPassword,
		DeviceName:                    cfg.DeviceName,
		DeviceID:                      cfg.DeviceID,
		PollIntervalMs:                int64(cfg.PollInterval / time.Millisecond),
		NoticeMode:                    cfg.NoticeMode,
		PanelAddress:                  cfg.PanelAddress,
		OpenPanelOnLaunch:             cfg.OpenPanelOnLaunch,
		DownloadDir:                   cfg.DownloadDir,
		DownloadCacheRetentionHours:   int64(cfg.DownloadCacheRetention / time.Hour),
		ShellMenuEnabled:              cfg.ShellMenuEnabled,
		ClipboardFileConfirmEnabled:   cfg.ClipboardFileConfirmEnabled,
		ClipboardFileConfirmWindowSec: int64(cfg.ClipboardFileConfirmWindow / time.Second),
		OpenPanelHotkey:               cfg.OpenPanelHotkey,
		SendClipboardHotkey:           cfg.SendClipboardHotkey,
		FetchLatestHotkey:             cfg.FetchLatestHotkey,
		FetchLatestFileHotkey:         cfg.FetchLatestFileHotkey,
		DownloadLatestHotkey:          cfg.DownloadLatestHotkey,
		ReconnectDelayMs:              int64(cfg.ReconnectDelay / time.Millisecond),
		MaxReconnectAttempts:          cfg.MaxReconnectAttempts,
		TipWidth:                      cfg.TipWidth,
		TipHeight:                     cfg.TipHeight,
		TipTheme:                      cfg.TipTheme,
		TipLeft:                       cfg.TipLeft,
		TipTop:                        cfg.TipTop,
		SuccessNoticeEnabled:          cfg.SuccessNoticeEnabled,
	}, "", "  ")
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
	if c.DownloadCacheRetention <= 0 {
		c.DownloadCacheRetention = def.DownloadCacheRetention
	}
	if c.DownloadCacheRetention < time.Hour {
		c.DownloadCacheRetention = time.Hour
	}
	if c.DownloadCacheRetention > 30*24*time.Hour {
		c.DownloadCacheRetention = 30 * 24 * time.Hour
	}
	if c.ClipboardFileConfirmWindow <= 0 {
		c.ClipboardFileConfirmWindow = def.ClipboardFileConfirmWindow
	}
	if c.ClipboardFileConfirmWindow < 3*time.Second {
		c.ClipboardFileConfirmWindow = 3 * time.Second
	}
	if c.ClipboardFileConfirmWindow > 30*time.Second {
		c.ClipboardFileConfirmWindow = 30 * time.Second
	}
	c.OpenPanelHotkey = normalizeHotkey(c.OpenPanelHotkey)
	c.SendClipboardHotkey = normalizeHotkey(c.SendClipboardHotkey)
	c.FetchLatestHotkey = normalizeHotkey(c.FetchLatestHotkey)
	c.FetchLatestFileHotkey = normalizeHotkey(c.FetchLatestFileHotkey)
	c.DownloadLatestHotkey = normalizeHotkey(c.DownloadLatestHotkey)
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
	if c.TipWidth <= 0 {
		c.TipWidth = def.TipWidth
	}
	if c.TipWidth < 300 {
		c.TipWidth = 300
	}
	if c.TipWidth > 560 {
		c.TipWidth = 560
	}
	if c.TipHeight <= 0 {
		c.TipHeight = def.TipHeight
	}
	if c.TipHeight < 120 {
		c.TipHeight = 120
	}
	if c.TipHeight > 260 {
		c.TipHeight = 260
	}
	c.TipTheme = normalizeTipTheme(c.TipTheme)
	if c.TipLeft < -1 {
		c.TipLeft = -1
	}
	if c.TipTop < -1 {
		c.TipTop = -1
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
	case "tip":
		return "tip"
	case "popup":
		return "popup"
	default:
		return "tip"
	}
}

func defaultDownloadDir() string {
	home, err := os.UserHomeDir()
	if err != nil || strings.TrimSpace(home) == "" {
		return ".\\downloads"
	}
	return filepath.Join(home, "Downloads", "CloudClipboard")
}

func normalizeTipTheme(value string) string {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "light":
		return "light"
	default:
		return "dark"
	}
}

func normalizeHotkey(value string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return ""
	}
	value = strings.ReplaceAll(value, " ", "")
	parts := strings.Split(value, "+")
	canonical := make([]string, 0, len(parts))
	seen := map[string]bool{}
	for _, part := range parts {
		token := strings.TrimSpace(part)
		if token == "" {
			continue
		}
		upper := strings.ToUpper(token)
		switch upper {
		case "CTRL", "CONTROL":
			if !seen["Ctrl"] {
				canonical = append(canonical, "Ctrl")
				seen["Ctrl"] = true
			}
		case "ALT", "OPTION":
			if !seen["Alt"] {
				canonical = append(canonical, "Alt")
				seen["Alt"] = true
			}
		case "SHIFT":
			if !seen["Shift"] {
				canonical = append(canonical, "Shift")
				seen["Shift"] = true
			}
		case "WIN", "CMD", "META":
			if !seen["Win"] {
				canonical = append(canonical, "Win")
				seen["Win"] = true
			}
		default:
			canonical = append(canonical, upper)
		}
	}
	return strings.Join(canonical, "+")
}
