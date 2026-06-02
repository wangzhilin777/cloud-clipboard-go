package tray

import "github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/panel"

type Backend interface {
	Status() panel.StatusView
	RequestReconnect()
	OpenPanel() error
	OpenDownloadDir() error
	ClearDownloadDir() (int, error)
	ConfirmPendingClipboardFiles() ([]string, error)
	SendFiles(paths []string) ([]string, error)
	SendText(text string, fromClipboard bool) (string, error)
	FetchLatestText() (string, error)
	FetchLatestFileToClipboard() (string, error)
	DownloadLatestFile() (string, error)
}
