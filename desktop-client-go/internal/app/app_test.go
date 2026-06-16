package app

import (
	"strings"
	"testing"
)

func TestDesktopOSIntegrationsDisabled(t *testing.T) {
	t.Setenv("CLOUD_CLIPBOARD_DESKTOP_DISABLE_OS_INTEGRATIONS", "1")
	if !desktopOSIntegrationsDisabled() {
		t.Fatalf("desktopOSIntegrationsDisabled() = false, want true")
	}

	t.Setenv("CLOUD_CLIPBOARD_DESKTOP_DISABLE_OS_INTEGRATIONS", "off")
	if desktopOSIntegrationsDisabled() {
		t.Fatalf("desktopOSIntegrationsDisabled() = true, want false")
	}
}

func TestBuildWindowsTipScriptIncludesDropUploadHook(t *testing.T) {
	script := buildWindowsTipScript(
		"检测到新的剪贴板文件",
		"codex-test.txt，可在 8 秒内确认发送。",
		"立即发送",
		"http://127.0.0.1:9530/tips/confirm-pending-clipboard-files",
		"打开面板",
		"http://127.0.0.1:9530/",
		"http://127.0.0.1:9530/api/send-file",
		8,
		420,
		170,
		"dark",
		-1,
		-1,
		`C:\temp\config.json`,
	)
	checks := []string{
		"Invoke-DropUpload",
		"System.Net.Http.HttpClient",
		"MultipartFormDataContent",
		"DataFormats]::FileDrop",
		"请拖入文件，暂不支持目录直接发送。",
		"也可以把文件拖到这里直接发送",
		"http://127.0.0.1:9530/api/send-file",
	}
	for _, check := range checks {
		if !strings.Contains(script, check) {
			t.Fatalf("buildWindowsTipScript() missing %q", check)
		}
	}
}

func TestShowHotCornerTipRequiresDropURL(t *testing.T) {
	app := &App{}
	shown, err := app.showHotCornerTip()
	if err != nil {
		t.Fatalf("showHotCornerTip() error = %v", err)
	}
	if shown {
		t.Fatal("showHotCornerTip() = true, want false without drop URL")
	}
}
