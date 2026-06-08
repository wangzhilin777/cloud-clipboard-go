//go:build windows

package tray

import _ "embed"

//go:embed assets/cloud-clipboard-desktop.ico
var trayIconICO []byte

func trayIconBytes() []byte {
	return append([]byte(nil), trayIconICO...)
}
