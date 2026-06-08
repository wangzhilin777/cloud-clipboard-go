//go:build windows

package shellmenu

import (
	"context"
	"fmt"
	"log"
	"path/filepath"
	"strings"
	"sync"
	"syscall"

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
	ensureFn   func(string, string) error
	removeFn   func() error
	mu         sync.Mutex
	status     Status
}

func start(ctx context.Context, logger *log.Logger, cfg config.Config, exePath string, configPath string) Manager {
	mgr := &manager{
		logger:     logger,
		exePath:    exePath,
		configPath: configPath,
		updateCh:   make(chan config.Config, 1),
		ensureFn:   ensureMenus,
		removeFn:   removeMenus,
		status: Status{
			Supported: true,
			Message:   "等待同步 Windows 右键菜单状态",
		}.withTimestamp(),
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

func (m *manager) Status() Status {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.status
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
		if err := m.removeFn(); err != nil {
			m.setStatus(Status{
				Supported: true,
				Enabled:   false,
				Message:   "关闭右键菜单失败",
				LastError: err.Error(),
			})
			m.logger.Printf("移除右键菜单失败: %v", err)
			return
		}
		refreshExplorerShell()
		m.setStatus(Status{
			Supported: true,
			Enabled:   false,
			Ready:     false,
			Message:   "Windows 右键菜单已关闭",
		})
		return
	}
	if err := m.ensureFn(m.exePath, m.configPath); err != nil {
		m.setStatus(Status{
			Supported: true,
			Enabled:   true,
			Ready:     false,
			Message:   "注册 Windows 右键菜单失败",
			LastError: err.Error(),
		})
		m.logger.Printf("注册右键菜单失败: %v", err)
		return
	}
	refreshExplorerShell()
	m.setStatus(Status{
		Supported: true,
		Enabled:   true,
		Ready:     true,
		Message:   "Windows 右键菜单已同步到资源管理器",
	})
	m.logger.Printf("已同步 Windows 右键菜单")
}

func (m *manager) setStatus(status Status) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.status = status.withTimestamp()
}

func ensureMenus(exePath string, configPath string) error {
	commands, err := buildMenuCommands(exePath, configPath)
	if err != nil {
		return err
	}
	if err := writeParentMenu(fileMenuKey, "Cloud Clipboard", commands.IconPath); err != nil {
		return err
	}
	if err := writeCommandMenu(fileSendMenuKey, "复制到剪贴板服务器", commands.IconPath, commands.SendCommand); err != nil {
		return err
	}
	if err := writeParentMenu(dirMenuKey, "Cloud Clipboard", commands.IconPath); err != nil {
		return err
	}
	if err := writeCommandMenu(dirPasteMenuKey, "从剪贴板服务器粘贴到此处", commands.IconPath, commands.PasteDirCommand); err != nil {
		return err
	}
	if err := writeCommandMenu(dirFetchClipMenuKey, "拉取最新文件到剪贴板", commands.IconPath, commands.FetchLatestFileCommand); err != nil {
		return err
	}
	if err := writeParentMenu(backgroundMenuKey, "Cloud Clipboard", commands.IconPath); err != nil {
		return err
	}
	if err := writeCommandMenu(backgroundPasteMenuKey, "从剪贴板服务器粘贴到此处", commands.IconPath, commands.PasteBackgroundCommand); err != nil {
		return err
	}
	if err := writeCommandMenu(backgroundFetchClipMenuKey, "拉取最新文件到剪贴板", commands.IconPath, commands.FetchLatestFileCommand); err != nil {
		return err
	}
	return nil
}

type menuCommands struct {
	IconPath               string
	SendCommand            string
	PasteDirCommand        string
	PasteBackgroundCommand string
	FetchLatestFileCommand string
}

func buildMenuCommands(exePath string, configPath string) (menuCommands, error) {
	exePath = strings.TrimSpace(exePath)
	configPath = strings.TrimSpace(configPath)
	if exePath == "" || configPath == "" {
		return menuCommands{}, fmt.Errorf("右键菜单缺少可执行文件或配置路径")
	}
	return menuCommands{
		IconPath:               exePath,
		SendCommand:            fmt.Sprintf(`"%s" -config "%s" -shell-send "%%1"`, exePath, configPath),
		PasteDirCommand:        fmt.Sprintf(`"%s" -config "%s" -shell-download-dir "%%1"`, exePath, configPath),
		PasteBackgroundCommand: fmt.Sprintf(`"%s" -config "%s" -shell-download-dir "%%V"`, exePath, configPath),
		FetchLatestFileCommand: fmt.Sprintf(`"%s" -config "%s" -shell-fetch-latest-file`, exePath, configPath),
	}, nil
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

var (
	shell32DLL         = syscall.NewLazyDLL("shell32.dll")
	procSHChangeNotify = shell32DLL.NewProc("SHChangeNotify")
)

func refreshExplorerShell() {
	if procSHChangeNotify.Find() != nil {
		return
	}
	const shcneAssocChanged = 0x08000000
	const shcnfIDList = 0x0000
	procSHChangeNotify.Call(shcneAssocChanged, shcnfIDList, 0, 0)
}
