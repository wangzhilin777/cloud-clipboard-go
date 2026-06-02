package config

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestLoadNormalizesAndPersistsMissingGeneratedFields(t *testing.T) {
	path := filepath.Join(t.TempDir(), "config.json")
	raw := []byte(`{
  "serverBase": "http://127.0.0.1:9501/",
  "deviceName": "",
  "deviceId": "",
  "downloadDir": "",
  "tipLeft": -5,
  "tipTop": -3
}`)
	if err := os.WriteFile(path, raw, 0o644); err != nil {
		t.Fatalf("write raw config: %v", err)
	}

	cfg, err := Load(path)
	if err != nil {
		t.Fatalf("load config: %v", err)
	}
	if cfg.ServerBase != "http://127.0.0.1:9501" {
		t.Fatalf("server base was not normalized: %q", cfg.ServerBase)
	}
	if cfg.DeviceName == "" {
		t.Fatal("device name was not generated")
	}
	if cfg.DeviceID == "" {
		t.Fatal("device id was not generated")
	}
	if cfg.DownloadDir == "" {
		t.Fatal("download dir was not generated")
	}
	if cfg.TipLeft != -1 || cfg.TipTop != -1 {
		t.Fatalf("tip position was not clamped: left=%d top=%d", cfg.TipLeft, cfg.TipTop)
	}

	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read normalized config: %v", err)
	}
	var persisted Config
	if err := json.Unmarshal(data, &persisted); err != nil {
		t.Fatalf("unmarshal normalized config: %v", err)
	}
	if persisted.DeviceName != cfg.DeviceName || persisted.DeviceID != cfg.DeviceID || persisted.DownloadDir != cfg.DownloadDir {
		t.Fatalf("generated fields were not persisted: got name=%q id=%q dir=%q", persisted.DeviceName, persisted.DeviceID, persisted.DownloadDir)
	}
}

func TestLoadPrefersFriendlyDurationFields(t *testing.T) {
	path := filepath.Join(t.TempDir(), "config.json")
	raw := []byte(`{
  "pollInterval": 3000000000,
  "pollIntervalMs": 700,
  "downloadCacheRetention": 3600000000000,
  "downloadCacheRetentionHours": 48,
  "clipboardFileConfirmWindow": 30000000000,
  "clipboardFileConfirmWindowSec": 6,
  "reconnectDelay": 30000000000,
  "reconnectDelayMs": 1500
}`)
	if err := os.WriteFile(path, raw, 0o644); err != nil {
		t.Fatalf("write raw config: %v", err)
	}

	cfg, err := Load(path)
	if err != nil {
		t.Fatalf("load config: %v", err)
	}
	if cfg.PollInterval != 700*time.Millisecond {
		t.Fatalf("poll interval = %s, want 700ms", cfg.PollInterval)
	}
	if cfg.DownloadCacheRetention != 48*time.Hour {
		t.Fatalf("download cache retention = %s, want 48h", cfg.DownloadCacheRetention)
	}
	if cfg.ClipboardFileConfirmWindow != 6*time.Second {
		t.Fatalf("clipboard file confirm window = %s, want 6s", cfg.ClipboardFileConfirmWindow)
	}
	if cfg.ReconnectDelay != 1500*time.Millisecond {
		t.Fatalf("reconnect delay = %s, want 1500ms", cfg.ReconnectDelay)
	}

	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read normalized config: %v", err)
	}
	var persisted struct {
		PollIntervalMs                int64 `json:"pollIntervalMs"`
		DownloadCacheRetentionHours   int64 `json:"downloadCacheRetentionHours"`
		ClipboardFileConfirmWindowSec int64 `json:"clipboardFileConfirmWindowSec"`
		ReconnectDelayMs              int64 `json:"reconnectDelayMs"`
	}
	if err := json.Unmarshal(data, &persisted); err != nil {
		t.Fatalf("unmarshal normalized config: %v", err)
	}
	if persisted.PollIntervalMs != 700 || persisted.DownloadCacheRetentionHours != 48 || persisted.ClipboardFileConfirmWindowSec != 6 || persisted.ReconnectDelayMs != 1500 {
		t.Fatalf("friendly duration fields were not persisted: %#v", persisted)
	}
}
