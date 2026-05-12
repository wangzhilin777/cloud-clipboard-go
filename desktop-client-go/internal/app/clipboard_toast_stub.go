//go:build !windows

package app

func (a *App) showClipboardFilesToast(_ []string, _ int) bool {
	return false
}
