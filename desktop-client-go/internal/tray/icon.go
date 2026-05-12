package tray

import (
	"bytes"
	"crypto/sha1"
	_ "embed"
	"encoding/hex"
	"os"
	"path/filepath"
)

//go:embed assets/cloud-clipboard-desktop.ico
var trayIconICO []byte

func ensureTrayIconFile() (string, error) {
	sum := sha1.Sum(trayIconICO)
	name := "cloud-clipboard-desktop-" + hex.EncodeToString(sum[:8]) + ".ico"
	path := filepath.Join(os.TempDir(), name)

	if data, err := os.ReadFile(path); err == nil && bytes.Equal(data, trayIconICO) {
		return path, nil
	}
	if err := os.WriteFile(path, trayIconICO, 0o644); err != nil {
		return "", err
	}
	return path, nil
}
