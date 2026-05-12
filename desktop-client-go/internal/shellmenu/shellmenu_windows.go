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
	fileMenuKey              = `Software\Classes\*\shell\CloudClipboard`
	fileSendMenuKey          = `Software\Classes\*\shell\CloudClipboard\shell\Send`
	dirMenuKey               = `Software\Classes\Directory\shell\CloudClipboard`
	dirPasteMenuKey          = `Software\Classes\Directory\shell\CloudClipboard\shell\PasteHere`
	dirFetchClipMenuKey      = `Software\Classes\Directory\shell\CloudClipboard\shell\FetchLatestClipboard`
	backgroundMenuKey        = `Software\Classes\Directory\Background\shell\CloudClipboard`
	backgroundPasteMenuKey   = `Software\Classes\Directory\Background\shell\CloudClipboard\shell\PasteHere`
	backgroundFetchClipMenuKey = `Software\Classes\Directory\Background\shell\CloudClipboard\shell\FetchLatestClipboard`
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
	fetchLatestFileCommand := fmt.Sprintf(`"%s" -config "%s" -shell-fetch-latest-file`, exePath, configPath)

	if err := writeParentMenu(fileMenuKey, "Cloud Clipboard", exePath); err != nil {
		return err
	}
	if err := writeCommandMenu(fileSendMenuKey, "复制到剪贴板服务器", exePath, sendCommand); err != nil {
		return err
	}
	if err := writeParentMenu(dirMenuKey, "Cloud Clipboard", exePath); err != nil {
		return err
	}
	if err := writeCommandMenu(dirPasteMenuKey, "从剪贴板服务器粘贴到此处", exePath, pasteDirCommand); err != nil {
		return err
	}
	if err := writeCommandMenu(dirFetchClipMenuKey, "拉取最新文件到剪贴板", exePath, fetchLatestFileCommand); err != nil {
		return err
	}
	if err := writeParentMenu(backgroundMenuKey, "Cloud Clipboard", exePath); err != nil {
		return err
	}
	if err := writeCommandMenu(backgroundPasteMenuKey, "从剪贴板服务器粘贴到此处", exePath, pasteBackgroundCommand); err != nil {
		return err
	}
	if err := writeCommandMenu(backgroundFetchClipMenuKey, "拉取最新文件到剪贴板", exePath, fetchLatestFileCommand); err != nil {
		return err
	}
	return nil
}

func removeMenus() error {
	removePaths := []string{fileMenuKey, dirMenuKey, backgroundMenuKey}
	for _, path := range removePaths {
		if err := deleteKeyTree(registry.CURRENT_USER, path); err != nil && err != registry.ErrNotExist {
			return err
		}
	}
	return nil
}

func writeParentMenu(path string, title string, iconPath string) error {
	key, _, err := registry.CreateKey(registry.CURRENT_USER, path, registry.SET_VALUE)
	if err != nil {
		return err
	}
	defer key.Close()
	if err := key.SetStringValue("MUIVerb", title); err != nil {
		return err
	}
	if err := key.SetStringValue("Icon", iconPath); err != nil {
		return err
	}
	return nil
}

func writeCommandMenu(path string, title string, iconPath string, command string) error {
	key, _, err := registry.CreateKey(registry.CURRENT_USER, path, registry.SET_VALUE)
	if err != nil {
		return err
	}
	defer key.Close()
	if err := key.SetStringValue("MUIVerb", title); err != nil {
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

func deleteKeyTree(root registry.Key, path string) error {
	key, err := registry.OpenKey(root, path, registry.ENUMERATE_SUB_KEYS|registry.QUERY_VALUE|registry.SET_VALUE)
	if err != nil {
		return err
	}
	defer key.Close()
	names, err := key.ReadSubKeyNames(-1)
	if err != nil {
		return err
	}
	for _, name := range names {
		if err := deleteKeyTree(root, filepath.Join(path, name)); err != nil && err != registry.ErrNotExist {
			return err
		}
	}
	return registry.DeleteKey(root, path)
}
