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
	ServerBase   string        `json:"serverBase"`
	Room         string        `json:"room"`
	RoomPassword string        `json:"roomPassword"`
	DeviceName   string        `json:"deviceName"`
	DeviceID     string        `json:"deviceId"`
	PollInterval time.Duration `json:"pollInterval"`
}

func Default() Config {
	host, _ := os.Hostname()
	if strings.TrimSpace(host) == "" {
		host = "Desktop Client"
	}
	return Config{
		ServerBase:   "http://127.0.0.1:9501",
		Room:         "",
		RoomPassword: "",
		DeviceName:   host,
		DeviceID:     uuid.NewString(),
		PollInterval: 800 * time.Millisecond,
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
}
