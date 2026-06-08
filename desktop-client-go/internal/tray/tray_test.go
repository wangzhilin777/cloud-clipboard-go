package tray

import (
	"errors"
	"testing"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/panel"
)

type fakeBackend struct {
	status                       panel.StatusView
	openPanelErr                 error
	sendTextResult               string
	sendTextErr                  error
	confirmClipboardFilesResult  []string
	confirmClipboardFilesErr     error
	fetchLatestTextResult        string
	fetchLatestTextErr           error
	fetchLatestFileResult        string
	fetchLatestFileErr           error
	downloadLatestFileResult     string
	downloadLatestFileErr        error
	openDownloadDirErr           error
	clearDownloadDirResult       int
	clearDownloadDirErr          error
	sendFilesResult              []string
	sendFilesErr                 error
	reconnectRequested           bool
}

func (f *fakeBackend) Status() panel.StatusView { return f.status }
func (f *fakeBackend) RequestReconnect()        { f.reconnectRequested = true }
func (f *fakeBackend) OpenPanel() error        { return f.openPanelErr }
func (f *fakeBackend) OpenDownloadDir() error  { return f.openDownloadDirErr }
func (f *fakeBackend) ClearDownloadDir() (int, error) {
	return f.clearDownloadDirResult, f.clearDownloadDirErr
}
func (f *fakeBackend) ConfirmPendingClipboardFiles() ([]string, error) {
	return f.confirmClipboardFilesResult, f.confirmClipboardFilesErr
}
func (f *fakeBackend) SendFiles(paths []string) ([]string, error) {
	return f.sendFilesResult, f.sendFilesErr
}
func (f *fakeBackend) SendText(text string, fromClipboard bool) (string, error) {
	return f.sendTextResult, f.sendTextErr
}
func (f *fakeBackend) FetchLatestText() (string, error) {
	return f.fetchLatestTextResult, f.fetchLatestTextErr
}
func (f *fakeBackend) FetchLatestFileToClipboard() (string, error) {
	return f.fetchLatestFileResult, f.fetchLatestFileErr
}
func (f *fakeBackend) DownloadLatestFile() (string, error) {
	return f.downloadLatestFileResult, f.downloadLatestFileErr
}

func TestNormalizeStatus(t *testing.T) {
	cases := []struct {
		name   string
		input  string
		expect string
	}{
		{name: "trusted", input: "trusted", expect: "已连接"},
		{name: "pending", input: " pending ", expect: "待批准"},
		{name: "connecting", input: "connecting", expect: "连接中"},
		{name: "retrying", input: "retrying", expect: "重试中"},
		{name: "stopped", input: "stopped", expect: "已暂停"},
		{name: "error", input: "error", expect: "异常"},
		{name: "unknown", input: "mystery", expect: "空闲"},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := normalizeStatus(tc.input); got != tc.expect {
				t.Fatalf("normalizeStatus(%q) = %q, want %q", tc.input, got, tc.expect)
			}
		})
	}
}

func TestBuildTrayTooltip(t *testing.T) {
	status := panel.StatusView{
		Config: config.Config{
			DeviceName: "WingLin",
		},
		State: panel.StateSnapshot{
			Status:    "trusted",
			Connected: true,
		},
	}

	if got := buildTrayTooltip(status); got != "Cloud Clipboard / 已连接 / 已连接 / WingLin" {
		t.Fatalf("unexpected tray tooltip: %q", got)
	}
}

func TestBuildTrayTooltipWithoutDeviceName(t *testing.T) {
	status := panel.StatusView{
		State: panel.StateSnapshot{
			Status: "pending",
		},
	}

	if got := buildTrayTooltip(status); got != "Cloud Clipboard / 待批准" {
		t.Fatalf("unexpected tray tooltip without device: %q", got)
	}
}

func TestPreviewText(t *testing.T) {
	cases := []struct {
		name   string
		input  string
		expect string
	}{
		{name: "empty", input: "   ", expect: "(空文本)"},
		{name: "short", input: " hello ", expect: "hello"},
		{name: "truncate", input: "012345678901234567890123456789", expect: "012345678901234567890123..."},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := previewText(tc.input); got != tc.expect {
				t.Fatalf("previewText(%q) = %q, want %q", tc.input, got, tc.expect)
			}
		})
	}
}

func TestHandleSendClipboardText(t *testing.T) {
	backend := &fakeBackend{sendTextResult: "  hello tray  "}
	detail, err := handleSendClipboardText(backend)
	if err != nil {
		t.Fatalf("handleSendClipboardText returned error: %v", err)
	}
	if detail != "hello tray" {
		t.Fatalf("unexpected detail: %q", detail)
	}
}

func TestHandleConfirmClipboardFiles(t *testing.T) {
	backend := &fakeBackend{confirmClipboardFilesResult: []string{"a.txt", "b.png"}}
	detail, err := handleConfirmClipboardFiles(backend)
	if err != nil {
		t.Fatalf("handleConfirmClipboardFiles returned error: %v", err)
	}
	if detail != "a.txt, b.png" {
		t.Fatalf("unexpected detail: %q", detail)
	}
}

func TestHandleClearDownloadDir(t *testing.T) {
	backend := &fakeBackend{clearDownloadDirResult: 3}
	detail, err := handleClearDownloadDir(backend)
	if err != nil {
		t.Fatalf("handleClearDownloadDir returned error: %v", err)
	}
	if detail != "3 项" {
		t.Fatalf("unexpected detail: %q", detail)
	}
}

func TestHandleOpenPanelError(t *testing.T) {
	backend := &fakeBackend{openPanelErr: errors.New("boom")}
	_, err := handleOpenPanel(backend)
	if err == nil {
		t.Fatal("expected error from handleOpenPanel")
	}
}

func TestHandleFetchLatestFileToClipboard(t *testing.T) {
	backend := &fakeBackend{fetchLatestFileResult: `C:\Temp\a.png`}
	detail, err := handleFetchLatestFileToClipboard(backend)
	if err != nil {
		t.Fatalf("handleFetchLatestFileToClipboard returned error: %v", err)
	}
	if detail != `C:\Temp\a.png` {
		t.Fatalf("unexpected detail: %q", detail)
	}
}
