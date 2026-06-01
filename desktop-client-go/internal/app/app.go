package app

import (
	"context"
	"errors"
	"fmt"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/clipwatch"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/fileclip"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/hotkey"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/panel"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/picker"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/shellmenu"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/syncclient"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/transfer"
	"golang.design/x/clipboard"
)

type App struct {
	logger                           *log.Logger
	cfg                              config.Config
	configPath                       string
	configDir                        string
	state                            *StateStore
	notifier                         Notifier
	panel                            *panel.Server
	hotkeys                          hotkey.Manager
	shellMenu                        shellmenu.Manager
	reloadCh                         chan struct{}
	suppressedClipboardFileSignature string
	lastClipboardNoticeSignature     string
	lastClipboardNoticeUntil         time.Time
	mu                               sync.Mutex
}

func New(logger *log.Logger, cfg config.Config, configPath string) *App {
	if absPath, err := filepath.Abs(configPath); err == nil {
		configPath = absPath
	}
	configDir := filepath.Dir(configPath)
	if configDir == "" {
		configDir = "."
	}
	return &App{
		logger:     logger,
		cfg:        cfg,
		configPath: configPath,
		configDir:  configDir,
		state:      NewStateStore(filepath.Join(configDir, "state.json")),
		notifier:   buildNotifier(cfg, logger, configPath),
		reloadCh:   make(chan struct{}, 1),
	}
}

func (a *App) Run(ctx context.Context) error {
	a.logger.Printf("桌面同步客户端启动，服务端: %s 房间: %s 设备: %s", a.cfg.ServerBase, a.cfg.Room, a.cfg.DeviceName)
	_ = a.state.Save(StateSnapshot{Status: "starting"})
	if removed, err := a.cleanupExpiredDownloadDir(); err != nil {
		a.logger.Printf("启动清理下载缓存失败: %v", err)
	} else if removed > 0 {
		a.logger.Printf("启动时已清理过期下载缓存: %d 项", removed)
	}
	a.hotkeys = hotkey.Start(ctx, a.logger, a.currentConfig(), a)
	clipwatch.Start(ctx, a.logger, a)
	if exePath, err := os.Executable(); err == nil {
		a.shellMenu = shellmenu.Start(ctx, a.logger, a.currentConfig(), exePath, a.configPath)
	}

	panelServer := panel.New(a.cfg.PanelAddress, a)
	a.panel = panelServer
	if err := panelServer.Start(); err != nil {
		return err
	}
	defer func() {
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		_ = panelServer.Shutdown(shutdownCtx)
	}()
	a.logger.Printf("本地控制面板已启动: %s", panelServer.URL())
	if a.cfg.OpenPanelOnLaunch {
		go func() {
			time.Sleep(500 * time.Millisecond)
			if err := a.OpenPanel(); err != nil {
				a.logger.Printf("自动打开控制面板失败: %v", err)
			}
		}()
	}

	for {
		client := syncclient.New(a.currentConfig(), a.logger, a)
		sessionCtx, cancel := context.WithCancel(ctx)
		errCh := make(chan error, 1)
		go func() {
			errCh <- client.Run(sessionCtx)
		}()

		select {
		case <-ctx.Done():
			cancel()
			<-errCh
			return ctx.Err()
		case <-a.reloadCh:
			a.logger.Printf("检测到配置更新，正在重连同步客户端")
			cancel()
			<-errCh
		case err := <-errCh:
			cancel()
			if ctx.Err() != nil {
				return ctx.Err()
			}
			if errors.Is(err, syncclient.ErrReconnectStopped) {
				a.logger.Printf("自动重连已暂停，等待手动重连")
				select {
				case <-ctx.Done():
					return ctx.Err()
				case <-a.reloadCh:
					a.logger.Printf("收到手动重连请求，重新启动同步客户端")
					continue
				}
			}
			return err
		}
	}
}

func buildNotifier(cfg config.Config, logger *log.Logger, configPath string) Notifier {
	switch strings.ToLower(strings.TrimSpace(cfg.NoticeMode)) {
	case "off":
		return noopNotifier{}
	case "log":
		return logNotifier{logger: logger}
	case "tip":
		return windowsTipNotifier{
			logger:     logger,
			configPath: configPath,
			width:      cfg.TipWidth,
			height:     cfg.TipHeight,
			theme:      cfg.TipTheme,
			left:       cfg.TipLeft,
			top:        cfg.TipTop,
		}
	default:
		return beeepNotifier{logger: logger}
	}
}

func (a *App) OnConnecting() {
	_ = a.state.Update(func(snapshot *StateSnapshot) {
		snapshot.Status = "connecting"
	})
}

func (a *App) OnConnected() {
	_ = a.state.Update(func(snapshot *StateSnapshot) {
		snapshot.Connected = true
		snapshot.Status = "connected"
	})
}

func (a *App) OnTrustedChanged(trusted bool) {
	status := "pending"
	if trusted {
		status = "trusted"
		a.notifier.Notify("云剪同步", "桌面端已获批准，可以开始同步文本。")
	} else {
		a.notifier.Notify("云剪同步", "设备已连接，等待网页端批准。")
	}
	_ = a.state.Update(func(snapshot *StateSnapshot) {
		snapshot.Connected = true
		snapshot.Trusted = trusted
		snapshot.Status = status
	})
}

func (a *App) OnRemoteText(text string) {
	_ = a.state.Update(func(snapshot *StateSnapshot) {
		snapshot.Connected = true
		snapshot.Trusted = true
		snapshot.Status = "trusted"
		snapshot.LastRemoteTextAt = time.Now().UnixMilli()
	})
}

func (a *App) OnPayloadNotice(kind string, title string) {
	if strings.TrimSpace(title) == "" {
		title = "远端内容"
	}
	displayKind := "文件"
	if strings.EqualFold(kind, "image") {
		displayKind = "图片"
	}
	a.notifier.Notify("云剪同步", "收到远端"+displayKind+"："+title)
	_ = a.state.Update(func(snapshot *StateSnapshot) {
		snapshot.Connected = true
		snapshot.Trusted = true
		snapshot.Status = "trusted"
		snapshot.LastPayloadTitle = title
		snapshot.LastPayloadKind = kind
		snapshot.LastPayloadAt = time.Now().UnixMilli()
	})
}

func (a *App) OnClipboardFiles(paths []string) {
	cfg := a.currentConfig()
	if !cfg.ClipboardFileConfirmEnabled || len(paths) == 0 {
		return
	}
	signature := clipboardFilesSignature(paths)
	a.mu.Lock()
	if signature == a.suppressedClipboardFileSignature {
		a.mu.Unlock()
		return
	}
	if signature == a.lastClipboardNoticeSignature && time.Now().Before(a.lastClipboardNoticeUntil) {
		a.mu.Unlock()
		return
	}
	a.suppressedClipboardFileSignature = ""
	a.lastClipboardNoticeSignature = signature
	a.lastClipboardNoticeUntil = time.Now().Add(cfg.ClipboardFileConfirmWindow)
	a.mu.Unlock()
	now := time.Now()
	expiresAt := now.Add(cfg.ClipboardFileConfirmWindow)
	_ = a.state.Update(func(snapshot *StateSnapshot) {
		snapshot.PendingClipboardFiles = append([]string(nil), paths...)
		snapshot.PendingClipboardDetectedAt = now.UnixMilli()
		snapshot.PendingClipboardExpiresAt = expiresAt.UnixMilli()
	})
	if a.showClipboardFilesToast(paths, int(cfg.ClipboardFileConfirmWindow/time.Second)) {
		return
	}
	a.notifier.Notify("云剪同步", fmt.Sprintf("检测到 %d 个剪贴板文件，可在 %d 秒内确认发送。", len(paths), int(cfg.ClipboardFileConfirmWindow/time.Second)))
}

func (a *App) OnError(err error) {
	if err == nil {
		return
	}
	_ = a.state.Update(func(snapshot *StateSnapshot) {
		snapshot.Status = "error"
		snapshot.LastError = err.Error()
	})
}

func (a *App) OnRetrying(attempt int, maxAttempts int, delay time.Duration, err error) {
	message := ""
	if err != nil {
		message = err.Error()
	}
	_ = a.state.Update(func(snapshot *StateSnapshot) {
		snapshot.Status = "retrying"
		snapshot.LastError = message
	})
	a.logger.Printf("同步失败，%d/%d 次后将在 %s 后重试", attempt, maxAttempts, delay.String())
}

func (a *App) OnReconnectStopped(lastErr error) {
	message := "自动重连次数已达上限，请检查服务后在面板手动重连。"
	if lastErr != nil && strings.TrimSpace(lastErr.Error()) != "" {
		message = message + " 最近错误：" + lastErr.Error()
	}
	a.notifier.Notify("云剪同步", "自动重连已暂停，请打开面板检查后手动重连。")
	_ = a.state.Update(func(snapshot *StateSnapshot) {
		snapshot.Status = "stopped"
		snapshot.LastError = message
	})
}

func (a *App) Status() panel.StatusView {
	a.mu.Lock()
	defer a.mu.Unlock()
	a.clearExpiredClipboardPendingLocked()
	return panel.StatusView{
		Config: a.cfg,
		State:  panel.StateSnapshot(a.state.Current()),
	}
}

func (a *App) UpdateConfig(cfg config.Config) error {
	a.mu.Lock()
	defer a.mu.Unlock()
	cfg.DeviceID = a.cfg.DeviceID
	cfg.Normalize()
	if err := config.Save(a.configPath, cfg); err != nil {
		return err
	}
	a.cfg = cfg
	a.notifier = buildNotifier(cfg, a.logger, a.configPath)
	if a.hotkeys != nil {
		a.hotkeys.Update(cfg)
	}
	if a.shellMenu != nil {
		a.shellMenu.Update(cfg)
	}
	select {
	case a.reloadCh <- struct{}{}:
	default:
	}
	return nil
}

func (a *App) ConfirmPendingClipboardFiles() ([]string, error) {
	current := a.state.Current()
	if len(current.PendingClipboardFiles) == 0 {
		return nil, errors.New("当前没有待确认的剪贴板文件")
	}
	if current.PendingClipboardExpiresAt > 0 && time.Now().UnixMilli() > current.PendingClipboardExpiresAt {
		a.clearPendingClipboard()
		return nil, errors.New("待确认的剪贴板文件已过期")
	}
	a.mu.Lock()
	a.suppressedClipboardFileSignature = clipboardFilesSignature(current.PendingClipboardFiles)
	a.mu.Unlock()
	names, err := a.SendFiles(append([]string(nil), current.PendingClipboardFiles...))
	if err != nil {
		return nil, err
	}
	a.clearPendingClipboard()
	a.saveLastAction("clipboard-file-send", strings.Join(names, "，"))
	a.notifyActionSuccess("已发送待确认文件到服务器")
	return names, nil
}

func (a *App) RequestReconnect() {
	select {
	case a.reloadCh <- struct{}{}:
	default:
	}
}

func (a *App) OpenPanel() error {
	panelURL := ""
	a.mu.Lock()
	if a.panel != nil {
		panelURL = a.panel.URL()
	}
	a.mu.Unlock()
	if strings.TrimSpace(panelURL) == "" {
		return errors.New("控制面板尚未启动")
	}

	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "windows":
		cmd = exec.Command("rundll32", "url.dll,FileProtocolHandler", panelURL)
	case "darwin":
		cmd = exec.Command("open", panelURL)
	default:
		cmd = exec.Command("xdg-open", panelURL)
	}
	if err := cmd.Start(); err != nil {
		return err
	}
	a.saveLastAction("open-panel", panelURL)
	return nil
}

func (a *App) OpenDownloadDir() error {
	dir := strings.TrimSpace(a.currentConfig().DownloadDir)
	if dir == "" {
		return errors.New("下载目录未配置")
	}
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return err
	}
	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "windows":
		cmd = exec.Command("explorer", dir)
	case "darwin":
		cmd = exec.Command("open", dir)
	default:
		cmd = exec.Command("xdg-open", dir)
	}
	if err := cmd.Start(); err != nil {
		return err
	}
	a.saveLastAction("open-download-dir", dir)
	return nil
}

func (a *App) ClearDownloadDir() (int, error) {
	dir := strings.TrimSpace(a.currentConfig().DownloadDir)
	if dir == "" {
		return 0, errors.New("下载目录未配置")
	}
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return 0, err
	}
	entries, err := os.ReadDir(dir)
	if err != nil {
		return 0, err
	}
	removed := 0
	for _, entry := range entries {
		target := filepath.Join(dir, entry.Name())
		if err := os.RemoveAll(target); err != nil {
			return removed, err
		}
		removed++
	}
	a.saveLastAction("clear-download-dir", dir)
	return removed, nil
}

func (a *App) currentConfig() config.Config {
	a.mu.Lock()
	defer a.mu.Unlock()
	return a.cfg
}

func (a *App) SendFiles(paths []string) ([]string, error) {
	var err error
	if len(paths) == 0 {
		paths, err = picker.PickFiles(context.Background())
		if err != nil {
			return nil, err
		}
	}
	if len(paths) == 0 {
		return nil, nil
	}
	sender := transfer.NewSender(a.currentConfig(), a.logger)
	results, err := sender.SendFiles(context.Background(), paths)
	if err != nil {
		return nil, err
	}
	names := make([]string, 0, len(results))
	for _, result := range results {
		names = append(names, result.Name)
	}
	a.saveLastAction("file-send", strings.Join(names, "，"))
	if len(names) > 0 {
		a.notifyActionSuccess("已发送文件到服务器")
	}
	return names, nil
}

func (a *App) SendText(text string, fromClipboard bool) (string, error) {
	if fromClipboard {
		if err := clipboard.Init(); err != nil {
			return "", err
		}
		text = strings.TrimSpace(string(clipboard.Read(clipboard.FmtText)))
	}
	sender := transfer.NewSender(a.currentConfig(), a.logger)
	result, err := sender.SendText(context.Background(), text)
	if err != nil {
		return "", err
	}
	label := "manual-text"
	if fromClipboard {
		label = "clipboard-text"
	}
	a.saveLastAction(label, result.Text)
	if fromClipboard {
		a.notifyActionSuccess("已发送剪贴板文本到服务器")
	}
	return result.Text, nil
}

func (a *App) FetchLatestText() (string, error) {
	sender := transfer.NewSender(a.currentConfig(), a.logger)
	result, err := sender.FetchLatestTextToClipboard(context.Background())
	if err != nil {
		return "", err
	}
	a.saveLastAction("fetch-latest-text", result.Text)
	a.notifyActionSuccess("已拉取最新文本到本地剪贴板")
	return result.Text, nil
}

func (a *App) DownloadLatestFile() (string, error) {
	if _, err := a.cleanupExpiredDownloadDir(); err != nil {
		return "", err
	}
	sender := transfer.NewSender(a.currentConfig(), a.logger)
	result, err := sender.DownloadLatestFile(context.Background())
	if err != nil {
		return "", err
	}
	a.saveLastAction("download-latest-file", result.Path)
	a.notifyActionSuccess("已下载最新文件到本机")
	return result.Path, nil
}

func (a *App) FetchLatestFileToClipboard() (string, error) {
	if _, err := a.cleanupExpiredDownloadDir(); err != nil {
		return "", err
	}
	sender := transfer.NewSender(a.currentConfig(), a.logger)
	result, err := sender.DownloadLatestFile(context.Background())
	if err != nil {
		return "", err
	}
	a.SuppressClipboardFiles([]string{result.Path})
	if err := fileclip.SetFileList([]string{result.Path}); err != nil {
		return "", err
	}
	a.saveLastAction("fetch-latest-file", result.Path)
	a.notifyActionSuccess("已拉取最新文件到本地剪贴板")
	return result.Path, nil
}

func (a *App) SuppressClipboardFiles(paths []string) {
	signature := clipboardFilesSignature(paths)
	if strings.TrimSpace(signature) == "" {
		return
	}
	a.mu.Lock()
	a.suppressedClipboardFileSignature = signature
	a.mu.Unlock()
	a.clearPendingClipboardIfSignature(signature)
}
func (a *App) notifyActionSuccess(message string) {
	message = strings.TrimSpace(message)
	if message == "" {
		return
	}
	cfg := a.currentConfig()
	if strings.EqualFold(cfg.NoticeMode, "tip") {
		_ = closeWindowsTip(a.configPath)
	}
	if !cfg.SuccessNoticeEnabled {
		return
	}
	a.notifier.Notify("云剪同步", message)
}

func (a *App) saveLastAction(actionType string, detail string) {
	_ = a.state.Update(func(snapshot *StateSnapshot) {
		snapshot.LastActionType = actionType
		snapshot.LastActionDetail = detail
		snapshot.LastActionAt = time.Now().UnixMilli()
	})
}

func (a *App) clearPendingClipboard() {
	_ = a.state.Update(func(snapshot *StateSnapshot) {
		snapshot.PendingClipboardFiles = nil
		snapshot.PendingClipboardDetectedAt = 0
		snapshot.PendingClipboardExpiresAt = 0
	})
}

func (a *App) clearPendingClipboardIfSignature(signature string) {
	_ = a.state.Update(func(snapshot *StateSnapshot) {
		if clipboardFilesSignature(snapshot.PendingClipboardFiles) != signature {
			return
		}
		snapshot.PendingClipboardFiles = nil
		snapshot.PendingClipboardDetectedAt = 0
		snapshot.PendingClipboardExpiresAt = 0
	})
}
func (a *App) clearExpiredClipboardPendingLocked() {
	current := a.state.Current()
	if current.PendingClipboardExpiresAt == 0 || time.Now().UnixMilli() <= current.PendingClipboardExpiresAt {
		return
	}
	_ = a.state.Update(func(snapshot *StateSnapshot) {
		snapshot.PendingClipboardFiles = nil
		snapshot.PendingClipboardDetectedAt = 0
		snapshot.PendingClipboardExpiresAt = 0
	})
}

func (a *App) cleanupExpiredDownloadDir() (int, error) {
	cfg := a.currentConfig()
	dir := strings.TrimSpace(cfg.DownloadDir)
	if dir == "" {
		return 0, errors.New("下载目录未配置")
	}
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return 0, err
	}
	entries, err := os.ReadDir(dir)
	if err != nil {
		return 0, err
	}
	expireBefore := time.Now().Add(-cfg.DownloadCacheRetention)
	removed := 0
	for _, entry := range entries {
		info, err := entry.Info()
		if err != nil {
			return removed, err
		}
		if info.ModTime().After(expireBefore) {
			continue
		}
		target := filepath.Join(dir, entry.Name())
		if err := os.RemoveAll(target); err != nil {
			return removed, err
		}
		removed++
	}
	_ = a.state.Update(func(snapshot *StateSnapshot) {
		snapshot.LastCacheCleanupRemoved = removed
		snapshot.LastCacheCleanupAt = time.Now().UnixMilli()
	})
	return removed, nil
}

func clipboardFilesSignature(paths []string) string {
	return strings.Join(paths, "\n")
}
