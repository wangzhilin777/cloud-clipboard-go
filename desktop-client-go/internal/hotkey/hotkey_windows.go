//go:build windows

package hotkey

import (
	"context"
	"fmt"
	"log"
	"runtime"
	"strings"
	"time"
	"unsafe"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
	"golang.org/x/sys/windows"
)

const (
	modAlt   = 0x0001
	modCtrl  = 0x0002
	modShift = 0x0004
	modWin   = 0x0008

	wmHotkey = 0x0312
	pmRemove = 0x0001
)

const (
	actionSendClipboard = iota + 1
	actionFetchLatest
	actionFetchLatestFile
	actionDownloadLatest
)

type manager struct {
	logger     *log.Logger
	actions    Actions
	updateCh   chan config.Config
	registered map[int]hotkeyBinding
}

type hotkeyBinding struct {
	id        int
	label     string
	modifiers uint32
	keyCode   uint32
	handler   func()
}

type point struct {
	X int32
	Y int32
}

type msg struct {
	HWnd    uintptr
	Message uint32
	WParam  uintptr
	LParam  uintptr
	Time    uint32
	Pt      point
}

var (
	user32               = windows.NewLazySystemDLL("user32.dll")
	procRegisterHotKey   = user32.NewProc("RegisterHotKey")
	procUnregisterHotKey = user32.NewProc("UnregisterHotKey")
	procPeekMessageW     = user32.NewProc("PeekMessageW")
)

func start(ctx context.Context, logger *log.Logger, cfg config.Config, actions Actions) Manager {
	mgr := &manager{
		logger:     logger,
		actions:    actions,
		updateCh:   make(chan config.Config, 1),
		registered: make(map[int]hotkeyBinding),
	}
	go mgr.loop(ctx, cfg)
	return mgr
}

func (m *manager) Update(cfg config.Config) {
	select {
	case m.updateCh <- cfg:
	default:
		select {
		case <-m.updateCh:
		default:
		}
		m.updateCh <- cfg
	}
}

func (m *manager) loop(ctx context.Context, cfg config.Config) {
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()
	m.applyConfig(cfg)
	ticker := time.NewTicker(80 * time.Millisecond)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			m.unregisterAll()
			return
		case nextCfg := <-m.updateCh:
			m.applyConfig(nextCfg)
		case <-ticker.C:
			m.drainMessages()
		}
	}
}

func (m *manager) drainMessages() {
	for {
		var message msg
		ret, _, _ := procPeekMessageW.Call(uintptr(unsafe.Pointer(&message)), 0, 0, 0, pmRemove)
		if ret == 0 {
			return
		}
		if message.Message != wmHotkey {
			continue
		}
		id := int(message.WParam)
		binding, ok := m.registered[id]
		if !ok || binding.handler == nil {
			continue
		}
		go binding.handler()
	}
}

func (m *manager) applyConfig(cfg config.Config) {
	m.unregisterAll()
	for _, binding := range m.bindingsForConfig(cfg) {
		if binding.id == 0 || binding.keyCode == 0 || binding.modifiers == 0 || binding.handler == nil {
			continue
		}
		if err := registerHotKey(binding.id, binding.modifiers, binding.keyCode); err != nil {
			m.logger.Printf("注册全局热键失败: %s / %v", binding.label, err)
			continue
		}
		m.registered[binding.id] = binding
		m.logger.Printf("已注册全局热键: %s => %s", binding.label, bindingDisplay(binding))
	}
}

func (m *manager) unregisterAll() {
	for id := range m.registered {
		_ = unregisterHotKey(id)
	}
	clear(m.registered)
}

func (m *manager) bindingsForConfig(cfg config.Config) []hotkeyBinding {
	return []hotkeyBinding{
		newBinding(actionSendClipboard, "发送当前剪贴板文本", cfg.SendClipboardHotkey, func() {
			if text, err := m.actions.SendText("", true); err != nil {
				m.logger.Printf("全局热键发送剪贴板文本失败: %v", err)
			} else {
				m.logger.Printf("全局热键发送剪贴板文本成功: %s", previewText(text))
			}
		}),
		newBinding(actionFetchLatest, "拉取最新文本到剪贴板", cfg.FetchLatestHotkey, func() {
			if text, err := m.actions.FetchLatestText(); err != nil {
				m.logger.Printf("全局热键拉取最新文本失败: %v", err)
			} else {
				m.logger.Printf("全局热键拉取最新文本成功: %s", previewText(text))
			}
		}),
		newBinding(actionFetchLatestFile, "拉取最新文件到剪贴板", cfg.FetchLatestFileHotkey, func() {
			if path, err := m.actions.FetchLatestFileToClipboard(); err != nil {
				m.logger.Printf("全局热键拉取最新文件到剪贴板失败: %v", err)
			} else {
				m.logger.Printf("全局热键拉取最新文件到剪贴板成功: %s", path)
			}
		}),
		newBinding(actionDownloadLatest, "下载最新文件到本机", cfg.DownloadLatestHotkey, func() {
			if path, err := m.actions.DownloadLatestFile(); err != nil {
				m.logger.Printf("全局热键下载最新文件失败: %v", err)
			} else {
				m.logger.Printf("全局热键下载最新文件成功: %s", path)
			}
		}),
	}
}

func newBinding(id int, label string, hotkeyValue string, handler func()) hotkeyBinding {
	mods, key, ok := parseHotkey(hotkeyValue)
	if !ok {
		return hotkeyBinding{}
	}
	return hotkeyBinding{
		id:        id,
		label:     label,
		modifiers: mods,
		keyCode:   key,
		handler:   handler,
	}
}

func bindingDisplay(binding hotkeyBinding) string {
	parts := make([]string, 0, 4)
	if binding.modifiers&modCtrl != 0 {
		parts = append(parts, "Ctrl")
	}
	if binding.modifiers&modAlt != 0 {
		parts = append(parts, "Alt")
	}
	if binding.modifiers&modShift != 0 {
		parts = append(parts, "Shift")
	}
	if binding.modifiers&modWin != 0 {
		parts = append(parts, "Win")
	}
	parts = append(parts, keyCodeLabel(binding.keyCode))
	return strings.Join(parts, "+")
}

func registerHotKey(id int, modifiers uint32, keyCode uint32) error {
	ret, _, err := procRegisterHotKey.Call(0, uintptr(id), uintptr(modifiers), uintptr(keyCode))
	if ret == 0 {
		return err
	}
	return nil
}

func unregisterHotKey(id int) error {
	ret, _, err := procUnregisterHotKey.Call(0, uintptr(id))
	if ret == 0 {
		return err
	}
	return nil
}

func parseHotkey(value string) (uint32, uint32, bool) {
	value = strings.TrimSpace(value)
	if value == "" {
		return 0, 0, false
	}
	parts := strings.Split(value, "+")
	var mods uint32
	var key uint32
	for _, part := range parts {
		token := strings.ToUpper(strings.TrimSpace(part))
		switch token {
		case "":
			continue
		case "CTRL", "CONTROL":
			mods |= modCtrl
		case "ALT", "OPTION":
			mods |= modAlt
		case "SHIFT":
			mods |= modShift
		case "WIN", "CMD", "META":
			mods |= modWin
		default:
			code, ok := parseVirtualKey(token)
			if !ok {
				return 0, 0, false
			}
			key = code
		}
	}
	if key == 0 || mods == 0 {
		return 0, 0, false
	}
	return mods, key, true
}

func parseVirtualKey(token string) (uint32, bool) {
	if len(token) == 1 {
		ch := token[0]
		if ch >= 'A' && ch <= 'Z' {
			return uint32(ch), true
		}
		if ch >= '0' && ch <= '9' {
			return uint32(ch), true
		}
	}
	if strings.HasPrefix(token, "F") {
		var index int
		_, err := fmt.Sscanf(token, "F%d", &index)
		if err == nil && index >= 1 && index <= 24 {
			return uint32(0x70 + index - 1), true
		}
	}
	switch token {
	case "INSERT":
		return 0x2D, true
	case "DELETE":
		return 0x2E, true
	case "HOME":
		return 0x24, true
	case "END":
		return 0x23, true
	case "PGUP", "PAGEUP":
		return 0x21, true
	case "PGDN", "PAGEDOWN":
		return 0x22, true
	case "UP":
		return 0x26, true
	case "DOWN":
		return 0x28, true
	case "LEFT":
		return 0x25, true
	case "RIGHT":
		return 0x27, true
	case "SPACE":
		return 0x20, true
	}
	return 0, false
}

func keyCodeLabel(code uint32) string {
	if code >= 'A' && code <= 'Z' {
		return string(rune(code))
	}
	if code >= '0' && code <= '9' {
		return string(rune(code))
	}
	if code >= 0x70 && code <= 0x87 {
		return fmt.Sprintf("F%d", int(code-0x70)+1)
	}
	switch code {
	case 0x2D:
		return "Insert"
	case 0x2E:
		return "Delete"
	case 0x24:
		return "Home"
	case 0x23:
		return "End"
	case 0x21:
		return "PageUp"
	case 0x22:
		return "PageDown"
	case 0x26:
		return "Up"
	case 0x28:
		return "Down"
	case 0x25:
		return "Left"
	case 0x27:
		return "Right"
	case 0x20:
		return "Space"
	default:
		return fmt.Sprintf("VK_%d", code)
	}
}

func previewText(text string) string {
	text = strings.TrimSpace(text)
	if text == "" {
		return "(空文本)"
	}
	runes := []rune(text)
	if len(runes) > 24 {
		return string(runes[:24]) + "..."
	}
	return text
}
