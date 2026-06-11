//go:build windows

package systray

import "testing"

func TestTrayMouseAction(t *testing.T) {
	tests := []struct {
		name string
		msg  uint32
		want trayAction
	}{
		{name: "left button up opens click callback", msg: 0x0202, want: trayActionClick},
		{name: "right button up opens menu", msg: 0x0205, want: trayActionShowMenu},
		{name: "context menu also opens menu", msg: 0x007B, want: trayActionShowMenu},
		{name: "unknown message ignored", msg: 0x0000, want: trayActionNone},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := trayMouseAction(tc.msg); got != tc.want {
				t.Fatalf("trayMouseAction(%#x) = %v, want %v", tc.msg, got, tc.want)
			}
		})
	}
}
