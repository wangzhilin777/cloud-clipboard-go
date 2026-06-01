package config

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
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
