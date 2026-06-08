package tray

import (
	"testing"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/panel"
)

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
