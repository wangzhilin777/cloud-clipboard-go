//go:build darwin

package app

import "os/exec"

func startPanelWindow(panelURL string) error {
	cmd := exec.Command("open", panelURL)
	return cmd.Start()
}
