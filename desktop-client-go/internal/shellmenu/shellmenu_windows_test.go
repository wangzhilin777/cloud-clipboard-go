//go:build windows

package shellmenu

import (
	"context"
	"errors"
	"io"
	"log"
	"strings"
	"testing"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
)

func TestBuildMenuCommands(t *testing.T) {
	commands, err := buildMenuCommands(`C:\Program Files\Cloud Clipboard\cloud-clipboard-panel.exe`, `C:\Users\Test User\AppData\Roaming\cloud-clipboard\config.json`)
	if err != nil {
		t.Fatalf("buildMenuCommands returned error: %v", err)
	}
	if !strings.Contains(commands.SendCommand, `-shell-send "%1"`) {
		t.Fatalf("expected send command to include %%1 placeholder, got %q", commands.SendCommand)
	}
	if !strings.Contains(commands.PasteBackgroundCommand, `-shell-download-dir "%V"`) {
		t.Fatalf("expected background paste command to include %%V placeholder, got %q", commands.PasteBackgroundCommand)
	}
	if !strings.Contains(commands.FetchLatestFileCommand, `-shell-fetch-latest-file`) {
		t.Fatalf("expected fetch-latest-file command, got %q", commands.FetchLatestFileCommand)
	}
}

func TestBuildMenuCommandsRequiresPaths(t *testing.T) {
	if _, err := buildMenuCommands("", ""); err == nil {
		t.Fatal("expected error when exePath and configPath are empty")
	}
}

func TestManagerApplyUpdatesStatus(t *testing.T) {
	mgr := &manager{
		logger:   log.New(io.Discard, "", 0),
		ensureFn: func(string, string) error { return nil },
		removeFn: func() error { return nil },
		status: Status{
			Supported: true,
			Message:   "init",
		},
	}
	mgr.apply(config.Config{ShellMenuEnabled: true})
	status := mgr.Status()
	if !status.Enabled || !status.Ready {
		t.Fatalf("expected enabled and ready status, got %+v", status)
	}
	if !strings.Contains(status.Message, "已同步") {
		t.Fatalf("unexpected success message: %+v", status)
	}

	mgr.apply(config.Config{ShellMenuEnabled: false})
	status = mgr.Status()
	if status.Enabled || status.Ready {
		t.Fatalf("expected disabled status after turning off shell menu, got %+v", status)
	}
}

func TestManagerApplyRecordsError(t *testing.T) {
	mgr := &manager{
		logger: log.New(io.Discard, "", 0),
		ensureFn: func(string, string) error {
			return errors.New("boom")
		},
		removeFn: func() error { return nil },
		status: Status{
			Supported: true,
			Message:   "init",
		},
	}
	mgr.apply(config.Config{ShellMenuEnabled: true})
	status := mgr.Status()
	if status.LastError != "boom" {
		t.Fatalf("expected last error to be recorded, got %+v", status)
	}
	if status.Ready {
		t.Fatalf("expected ready=false on apply error, got %+v", status)
	}
}

func TestStartInitializesSupportedStatus(t *testing.T) {
	mgr, ok := start(context.Background(), log.New(io.Discard, "", 0), config.Config{}, "demo.exe", "demo.json").(*manager)
	if !ok {
		t.Fatal("expected windows manager implementation")
	}
	status := mgr.Status()
	if !status.Supported {
		t.Fatalf("expected supported shell menu status, got %+v", status)
	}
}
