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

func TestLoadAcceptsUTF8BOMConfig(t *testing.T) {
	path := filepath.Join(t.TempDir(), "config.json")
	raw := append([]byte{0xEF, 0xBB, 0xBF}, []byte(`{
  "serverBase": "http://127.0.0.1:9501/",
  "deviceName": "BOM Device",
  "deviceId": "bom-device-id"
}`)...)
	if err := os.WriteFile(path, raw, 0o644); err != nil {
		t.Fatalf("write raw config: %v", err)
	}

	cfg, err := Load(path)
	if err != nil {
		t.Fatalf("load config with bom: %v", err)
	}
	if cfg.DeviceName != "BOM Device" || cfg.DeviceID != "bom-device-id" {
		t.Fatalf("unexpected config after bom load: name=%q id=%q", cfg.DeviceName, cfg.DeviceID)
	}

	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read normalized config: %v", err)
	}
	if len(data) >= 3 && data[0] == 0xEF && data[1] == 0xBB && data[2] == 0xBF {
		t.Fatal("normalized config still contains utf-8 bom")
	}
}

func TestNormalizeNoticeModeDefaultsToTipButKeepsExplicitPopup(t *testing.T) {
	tests := []struct {
		name  string
		input string
		want  string
	}{
		{name: "empty", input: "", want: "tip"},
		{name: "unknown", input: "system", want: "tip"},
		{name: "tip", input: "TIP", want: "tip"},
		{name: "popup", input: "popup", want: "popup"},
		{name: "log", input: "log", want: "log"},
		{name: "off", input: "off", want: "off"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := normalizeNoticeMode(tt.input); got != tt.want {
				t.Fatalf("normalizeNoticeMode(%q) = %q, want %q", tt.input, got, tt.want)
			}
		})
	}
}

func TestNormalizeHotkeyRequiresModifierAndMainKey(t *testing.T) {
	tests := []struct {
		name  string
		input string
		want  string
	}{
		{name: "common", input: " ctrl + alt + c ", want: "Ctrl+Alt+C"},
		{name: "deduplicate modifiers", input: "ctrl+control+shift+v", want: "Ctrl+Shift+V"},
		{name: "function key", input: "win+f12", want: "Win+F12"},
		{name: "modifier only", input: "Ctrl+Alt", want: ""},
		{name: "key only", input: "V", want: ""},
		{name: "blank", input: " ", want: ""},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := normalizeHotkey(tt.input); got != tt.want {
				t.Fatalf("normalizeHotkey(%q) = %q, want %q", tt.input, got, tt.want)
			}
		})
	}
}
