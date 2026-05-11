package app

import (
	"context"
	"errors"
	"log"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/panel"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/picker"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/syncclient"
	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/transfer"
	"golang.design/x/clipboard"
)

type App struct {
	logger     *log.Logger
	cfg        config.Config
	configPath string
	configDir  string
	state      *StateStore
	notifier   Notifier
	panel      *panel.Server
	reloadCh   chan struct{}
	mu         sync.Mutex
}

func New(logger *log.Logger, cfg config.Config, configPath string) *App {
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
		notifier:   buildNotifier(cfg, logger),
		reloadCh:   make(chan struct{}, 1),
	}
}

func (a *App) Run(ctx context.Context) error {
	a.logger.Printf("桌面同步客户端启动，服务端: %s 房间: %s 设备: %s", a.cfg.ServerBase, a.cfg.Room, a.cfg.DeviceName)
	_ = a.state.Save(StateSnapshot{Status: "starting"})

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

func buildNotifier(cfg config.Config, logger *log.Logger) Notifier {
	switch strings.ToLower(strings.TrimSpace(cfg.NoticeMode)) {
	case "off":
		return noopNotifier{}
	case "log":
		return logNotifier{logger: logger}
	default:
		return beeepNotifier{logger: logger}
	}
}

func (a *App) OnConnecting() {
	_ = a.state.Save(StateSnapshot{Status: "connecting"})
}

func (a *App) OnConnected() {
	_ = a.state.Save(StateSnapshot{Connected: true, Status: "connected"})
}

func (a *App) OnTrustedChanged(trusted bool) {
	status := "pending"
	if trusted {
		status = "trusted"
		a.notifier.Notify("Cloud Clipboard", "桌面端已获批准，可以开始同步文本。")
	} else {
		a.notifier.Notify("Cloud Clipboard", "设备已连接，等待网页端批准。")
	}
	_ = a.state.Save(StateSnapshot{
		Connected: true,
		Trusted:   trusted,
		Status:    status,
	})
}

func (a *App) OnRemoteText(text string) {
	_ = a.state.Save(StateSnapshot{
		Connected:        true,
		Trusted:          true,
		Status:           "trusted",
		LastRemoteTextAt: time.Now().UnixMilli(),
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
	a.notifier.Notify("Cloud Clipboard", "收到远端"+displayKind+"："+title)
	_ = a.state.Save(StateSnapshot{
		Connected:        true,
		Trusted:          true,
		Status:           "trusted",
		LastPayloadTitle: title,
		LastPayloadKind:  kind,
		LastPayloadAt:    time.Now().UnixMilli(),
	})
}

func (a *App) OnError(err error) {
	if err == nil {
		return
	}
	_ = a.state.Save(StateSnapshot{
		Status:    "error",
		LastError: err.Error(),
	})
}

func (a *App) OnRetrying(attempt int, maxAttempts int, delay time.Duration, err error) {
	message := ""
	if err != nil {
		message = err.Error()
	}
	_ = a.state.Save(StateSnapshot{
		Status:    "retrying",
		LastError: message,
	})
	a.logger.Printf("同步失败，%d/%d 次后将在 %s 后重试", attempt, maxAttempts, delay.String())
}

func (a *App) OnReconnectStopped(lastErr error) {
	message := "自动重连次数已达上限，请检查服务后在面板手动重连。"
	if lastErr != nil && strings.TrimSpace(lastErr.Error()) != "" {
		message = message + " 最近错误：" + lastErr.Error()
	}
	a.notifier.Notify("Cloud Clipboard", "自动重连已暂停，请打开面板检查后手动重连。")
	_ = a.state.Save(StateSnapshot{
		Status:    "stopped",
		LastError: message,
	})
}

func (a *App) Status() panel.StatusView {
	a.mu.Lock()
	defer a.mu.Unlock()
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
	a.notifier = buildNotifier(cfg, a.logger)
	select {
	case a.reloadCh <- struct{}{}:
	default:
	}
	return nil
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
	return cmd.Start()
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
	_ = a.state.Save(StateSnapshot{
		Status:           a.state.Current().Status,
		Connected:        a.state.Current().Connected,
		Trusted:          a.state.Current().Trusted,
		LastError:        a.state.Current().LastError,
		LastRemoteTextAt: a.state.Current().LastRemoteTextAt,
		LastPayloadTitle: a.state.Current().LastPayloadTitle,
		LastPayloadKind:  a.state.Current().LastPayloadKind,
		LastPayloadAt:    a.state.Current().LastPayloadAt,
		LastActionType:   "file-send",
		LastActionDetail: strings.Join(names, "，"),
		LastActionAt:     time.Now().UnixMilli(),
	})
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
	current := a.state.Current()
	_ = a.state.Save(StateSnapshot{
		Status:           current.Status,
		Connected:        current.Connected,
		Trusted:          current.Trusted,
		LastError:        current.LastError,
		LastRemoteTextAt: current.LastRemoteTextAt,
		LastPayloadTitle: current.LastPayloadTitle,
		LastPayloadKind:  current.LastPayloadKind,
		LastPayloadAt:    current.LastPayloadAt,
		LastActionType:   label,
		LastActionDetail: result.Text,
		LastActionAt:     time.Now().UnixMilli(),
	})
	return result.Text, nil
}
