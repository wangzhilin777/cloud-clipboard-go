//go:build !windows

package app

import "os/exec"

func startPanelWindow(panelURL string) error {
	cmd := exec.Command("xdg-open", panelURL)
	return cmd.Start()
}
