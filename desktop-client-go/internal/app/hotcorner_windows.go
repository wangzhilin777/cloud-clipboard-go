//go:build windows

package app

import (
	"context"
	"log"
	"strings"
	"time"
	"unsafe"

	"golang.org/x/sys/windows"
)

const (
	smXVirtualScreen = 76
	smYVirtualScreen = 77
	smCXVirtualScreen = 78
	smCYVirtualScreen = 79
	vkLButton        = 0x01
)

var (
	hotCornerUser32       = windows.NewLazySystemDLL("user32.dll")
	procGetCursorPos      = hotCornerUser32.NewProc("GetCursorPos")
	procGetAsyncKeyState  = hotCornerUser32.NewProc("GetAsyncKeyState")
	procGetSystemMetrics  = hotCornerUser32.NewProc("GetSystemMetrics")
)

type hotCornerPoint struct {
	X int32
	Y int32
}

func (a *App) runTipHotCornerMonitor(ctx context.Context, logger *log.Logger) {
	ticker := time.NewTicker(45 * time.Millisecond)
	defer ticker.Stop()

	var session dragSession

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		}

		cfg := a.currentConfig()
		if desktopOSIntegrationsDisabled() || !strings.EqualFold(strings.TrimSpace(cfg.NoticeMode), "tip") || !cfg.TipHotCornerEnabled {
			session.reset()
			continue
		}

		if !isDraggingWithLeftButton() {
			session.reset()
			continue
		}

		p, ok := currentCursorPosition()
		if !ok {
			continue
		}
		if !session.update(p) {
			continue
		}
		if !isInTipHotCorner(p, 120) {
			session.markOutside()
			continue
		}

		if !session.readyToTrigger() {
			continue
		}
	shown, err := a.showHotCornerTip()
		if err != nil {
			logger.Printf("右下角热角提示失败: %v", err)
			session.markShown()
			continue
		}
		if !shown {
			continue
		}
		logger.Printf("已触发右下角热角提示")
		session.markShown()
	}
}

func (a *App) showHotCornerTip() (bool, error) {
	cfg := a.currentConfig()
	dropURL := a.pendingClipboardDropURL()
	if strings.TrimSpace(dropURL) == "" {
		return false, nil
	}
	title := "把文件拖到这里直接发送"
	body := "拖动文件到右下角提示窗松开后，就会直接发送到同步房间。"
	timeoutSec := cfg.TipAutoCloseSec
	if timeoutSec <= 0 {
		timeoutSec = 8
	}
	if err := showWindowsTip(title, body, "", "", "", "", dropURL, timeoutSec, cfg.TipWidth, cfg.TipHeight, cfg.TipTheme, cfg.TipLeft, cfg.TipTop, a.configPath); err != nil {
		return false, err
	}
	return true, nil
}

func isDraggingWithLeftButton() bool {
	state, _, _ := procGetAsyncKeyState.Call(vkLButton)
	return state&0x8000 != 0
}

func currentCursorPosition() (hotCornerPoint, bool) {
	var p hotCornerPoint
	ret, _, _ := procGetCursorPos.Call(uintptr(unsafe.Pointer(&p)))
	if ret == 0 {
		return hotCornerPoint{}, false
	}
	return p, true
}

func currentVirtualScreenRect() (left int32, top int32, right int32, bottom int32) {
	left = int32(getSystemMetric(smXVirtualScreen))
	top = int32(getSystemMetric(smYVirtualScreen))
	width := int32(getSystemMetric(smCXVirtualScreen))
	height := int32(getSystemMetric(smCYVirtualScreen))
	right = left + width
	bottom = top + height
	return
}

func getSystemMetric(index int32) int {
	value, _, _ := procGetSystemMetrics.Call(uintptr(index))
	return int(value)
}

func isInTipHotCorner(p hotCornerPoint, edge int32) bool {
	left, top, right, bottom := currentVirtualScreenRect()
	if right <= left || bottom <= top {
		return false
	}
	if edge <= 0 {
		edge = 96
	}
	return p.X >= right-edge && p.X <= right && p.Y >= bottom-edge && p.Y <= bottom
}

type dragSession struct {
	startX    int32
	startY    int32
	enterAt   time.Time
	dragging  bool
	shown     bool
	hasStart  bool
	inCorner  bool
}

func (s *dragSession) reset() {
	s.startX = 0
	s.startY = 0
	s.enterAt = time.Time{}
	s.dragging = false
	s.shown = false
	s.hasStart = false
	s.inCorner = false
}

func (s *dragSession) update(p hotCornerPoint) bool {
	if !s.hasStart {
		s.startX = p.X
		s.startY = p.Y
		s.hasStart = true
		return false
	}
	if s.shown {
		return false
	}
	if !s.dragging && dragDistanceEnough(s.startX, s.startY, p.X, p.Y, 12) {
		s.dragging = true
	}
	if s.dragging && !isInTipHotCorner(p, 120) {
		s.inCorner = false
		s.enterAt = time.Time{}
	}
	return s.dragging && !s.shown
}

func (s *dragSession) markShown() {
	s.shown = true
}

func (s *dragSession) markOutside() {
	s.inCorner = false
	s.enterAt = time.Time{}
}

func (s *dragSession) readyToTrigger() bool {
	if !s.dragging || s.shown {
		return false
	}
	now := time.Now()
	if !s.inCorner {
		s.inCorner = true
		s.enterAt = now
		return false
	}
	if s.enterAt.IsZero() {
		s.enterAt = now
		return false
	}
	return now.Sub(s.enterAt) >= 80*time.Millisecond
}

func dragDistanceEnough(startX, startY, currentX, currentY int32, threshold int32) bool {
	dx := currentX - startX
	dy := currentY - startY
	if dx < 0 {
		dx = -dx
	}
	if dy < 0 {
		dy = -dy
	}
	return dx >= threshold || dy >= threshold
}
