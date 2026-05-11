//go:build windows

package shellmenu

import (
	"context"
	"fmt"
	"log"
	"path/filepath"
	"strings"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
	"golang.org/x/sys/windows/registry"
)

const (
	sendMenuKey            = `Software\Classes\*\shell\CloudClipboardSend`
	pasteDirMenuKey        = `Software\Classes\Directory\shell\CloudClipboardPasteHere`
	pasteBackgroundMenuKey = `Software\Classes\Directory\Background\shell\CloudClipboardPasteHere`
)

type manager struct {
	logger     *log.Logger
	exePath    string
	configPath string
	updateCh   chan config.Config
}

func start(ctx context.Context, logger *log.Logger, cfg config.Config, exePath string, configPath string) Manager {
	mgr := &manager{
		logger:     logger,
		exePath:    exePath,
		configPath: configPath,
		updateCh:   make(chan config.Config, 1),
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
	m.apply(cfg)
	for {
		select {
		case <-ctx.Done():
			return
		case nextCfg := <-m.updateCh:
			m.apply(nextCfg)
		}
	}
}

func (m *manager) apply(cfg config.Config) {
	if !cfg.ShellMenuEnabled {
		if err := removeMenus(); err != nil {
			m.logger.Printf("移除右键菜单失败: %v", err)
		}
		return
	}
	if err := ensureMenus(m.exePath, m.configPath); err != nil {
		m.logger.Printf("注册右键菜单失败: %v", err)
		return
	}
	m.logger.Printf("已同步 Windows 右键菜单")
}

func ensureMenus(exePath string, configPath string) error {
	exePath = strings.TrimSpace(exePath)
	configPath = strings.TrimSpace(configPath)
	if exePath == "" || configPath == "" {
		return fmt.Errorf("右键菜单缺少可执行文件或配置路径")
	}
	sendCommand := fmt.Sprintf(`"%s" -config "%s" -shell-send "%%1"`, exePath, configPath)
	pasteDirCommand := fmt.Sprintf(`"%s" -config "%s" -shell-download-dir "%%1"`, exePath, configPath)
	pasteBackgroundCommand := fmt.Sprintf(`"%s" -config "%s" -shell-download-dir "%%V"`, exePath, configPath)

	if err := writeMenu(sendMenuKey, "复制到剪贴板服务器", exePath, sendCommand); err != nil {
		return err
	}
	if err := writeMenu(pasteDirMenuKey, "从剪贴板服务器粘贴到此处", exePath, pasteDirCommand); err != nil {
		return err
	}
	if err := writeMenu(pasteBackgroundMenuKey, "从剪贴板服务器粘贴到此处", exePath, pasteBackgroundCommand); err != nil {
		return err
	}
	return nil
}

func removeMenus() error {
	removePaths := []string{sendMenuKey, pasteDirMenuKey, pasteBackgroundMenuKey}
	for _, path := range removePaths {
		if err := registry.DeleteKey(registry.CURRENT_USER, filepath.Join(path, "command")); err != nil && err != registry.ErrNotExist {
			return err
		}
		if err := registry.DeleteKey(registry.CURRENT_USER, path); err != nil && err != registry.ErrNotExist {
			return err
		}
	}
	return nil
}

func writeMenu(path string, title string, iconPath string, command string) error {
	key, _, err := registry.CreateKey(registry.CURRENT_USER, path, registry.SET_VALUE)
	if err != nil {
		return err
	}
	defer key.Close()
	if err := key.SetStringValue("", title); err != nil {
		return err
	}
	if err := key.SetStringValue("Icon", iconPath); err != nil {
		return err
	}
	commandKey, _, err := registry.CreateKey(registry.CURRENT_USER, filepath.Join(path, "command"), registry.SET_VALUE)
	if err != nil {
		return err
	}
	defer commandKey.Close()
	return commandKey.SetStringValue("", command)
}
