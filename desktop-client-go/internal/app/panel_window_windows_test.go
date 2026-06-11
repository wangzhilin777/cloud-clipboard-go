//go:build windows

package app

import "testing"

func TestWindowsPanelBrowsersPreferChrome(t *testing.T) {
	if len(windowsPanelBrowsers) < 2 {
		t.Fatalf("windowsPanelBrowsers too short: %d", len(windowsPanelBrowsers))
	}
	if got := windowsPanelBrowsers[0]; got != `C:\Program Files\Google\Chrome\Application\chrome.exe` {
		t.Fatalf("windowsPanelBrowsers[0] = %q, want Chrome first", got)
	}
	if got := windowsPanelBrowsers[1]; got != `C:\Program Files (x86)\Google\Chrome\Application\chrome.exe` {
		t.Fatalf("windowsPanelBrowsers[1] = %q, want x86 Chrome second", got)
	}
}

func TestStartPanelWindowActivatesOnlyMatchingPanel(t *testing.T) {
	originalDetect := detectPanelWindowRunning
	originalActivate := activatePanelWindowFn
	originalFindBrowser := findPanelBrowserFn
	t.Cleanup(func() {
		detectPanelWindowRunning = originalDetect
		activatePanelWindowFn = originalActivate
		findPanelBrowserFn = originalFindBrowser
	})

	activated := 0
	detectPanelWindowRunning = func(panelURL string) (bool, error) {
		if panelURL != "http://127.0.0.1:19552/" {
			t.Fatalf("unexpected panelURL: %s", panelURL)
		}
		return true, nil
	}
	activatePanelWindowFn = func(title string) error {
		activated++
		if title != "云剪同步桌面端" {
			t.Fatalf("unexpected title: %s", title)
		}
		return nil
	}
	findPanelBrowserFn = func() (string, error) {
		t.Fatal("findPanelBrowser should not be called when matching panel is already running")
		return "", nil
	}

	if err := startPanelWindow("http://127.0.0.1:19552/"); err != nil {
		t.Fatalf("startPanelWindow() error = %v", err)
	}
	if activated != 1 {
		t.Fatalf("activatePanelWindow called %d times, want 1", activated)
	}
}
