package panel

import (
	"context"
	"embed"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"mime"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"time"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
)

//go:embed static/*
var staticFiles embed.FS

type Backend interface {
	Status() StatusView
	UpdateConfig(cfg config.Config) error
	RequestReconnect()
	OpenPanel() error
	OpenDownloadDir() error
	ClearDownloadDir() (int, error)
	ConfirmPendingClipboardFiles() ([]string, error)
	SendFiles(paths []string) ([]string, error)
	SendText(text string, fromClipboard bool) (string, error)
	FetchLatestText() (string, error)
	DownloadLatestFile() (string, error)
	FetchLatestFileToClipboard() (string, error)
	SuppressClipboardFiles(paths []string)
}

type Server struct {
	address string
	backend Backend
	server  *http.Server
	ln      net.Listener
}

type StateSnapshot struct {
	Connected                  bool     `json:"connected"`
	Trusted                    bool     `json:"trusted"`
	Status                     string   `json:"status"`
	LastError                  string   `json:"lastError,omitempty"`
	LastErrorAt                int64    `json:"lastErrorAt,omitempty"`
	LastRemoteTextAt           int64    `json:"lastRemoteTextAt,omitempty"`
	LastPayloadTitle           string   `json:"lastPayloadTitle,omitempty"`
	LastPayloadKind            string   `json:"lastPayloadKind,omitempty"`
	LastPayloadAt              int64    `json:"lastPayloadAt,omitempty"`
	PendingClipboardFiles      []string `json:"pendingClipboardFiles,omitempty"`
	PendingClipboardDetectedAt int64    `json:"pendingClipboardDetectedAt,omitempty"`
	PendingClipboardExpiresAt  int64    `json:"pendingClipboardExpiresAt,omitempty"`
	LastCacheCleanupRemoved    int      `json:"lastCacheCleanupRemoved,omitempty"`
	LastCacheCleanupAt         int64    `json:"lastCacheCleanupAt,omitempty"`
	LastActionType             string   `json:"lastActionType,omitempty"`
	LastActionDetail           string   `json:"lastActionDetail,omitempty"`
	LastActionAt               int64    `json:"lastActionAt,omitempty"`
	LastUpdatedAt              int64    `json:"lastUpdatedAt"`
}

type CapabilityView struct {
	Platform                  string `json:"platform"`
	FilePicker                string `json:"filePicker"`
	FileClipboardWrite        string `json:"fileClipboardWrite"`
	ShellMenu                 string `json:"shellMenu"`
	ShellMenuStatus           string `json:"shellMenuStatus,omitempty"`
	ShellMenuLastError        string `json:"shellMenuLastError,omitempty"`
	ShellMenuRecommended      bool   `json:"shellMenuRecommended"`
	ClipboardFileConfirmation string `json:"clipboardFileConfirmation"`
}

type StatusView struct {
	Config       config.Config  `json:"config"`
	State        StateSnapshot  `json:"state"`
	Capabilities CapabilityView `json:"capabilities"`
}

type statusResponse struct {
	State        StateSnapshot  `json:"state"`
	Config       configView     `json:"config"`
	Capabilities CapabilityView `json:"capabilities"`
}

type configView struct {
	ServerBase                    string `json:"serverBase"`
	Room                          string `json:"room"`
	RoomPassword                  string `json:"roomPassword"`
	DeviceName                    string `json:"deviceName"`
	PollIntervalMs                int64  `json:"pollIntervalMs"`
	NoticeMode                    string `json:"noticeMode"`
	PanelAddress                  string `json:"panelAddress"`
	OpenPanelOnLaunch             bool   `json:"openPanelOnLaunch"`
	DownloadDir                   string `json:"downloadDir"`
	DownloadCacheRetentionHours   int64  `json:"downloadCacheRetentionHours"`
	ShellMenuEnabled              bool   `json:"shellMenuEnabled"`
	ClipboardFileConfirmEnabled   bool   `json:"clipboardFileConfirmEnabled"`
	ClipboardFileConfirmWindowSec int64  `json:"clipboardFileConfirmWindowSec"`
	OpenPanelHotkey               string `json:"openPanelHotkey"`
	SendClipboardHotkey           string `json:"sendClipboardHotkey"`
	FetchLatestHotkey             string `json:"fetchLatestHotkey"`
	FetchLatestFileHotkey         string `json:"fetchLatestFileHotkey"`
	DownloadLatestHotkey          string `json:"downloadLatestHotkey"`
	ReconnectDelayMs              int64  `json:"reconnectDelayMs"`
	MaxReconnectAttempts          int    `json:"maxReconnectAttempts"`
	TipWidth                      int    `json:"tipWidth"`
	TipHeight                     int    `json:"tipHeight"`
	TipTheme                      string `json:"tipTheme"`
	TipLeft                       int    `json:"tipLeft"`
	TipTop                        int    `json:"tipTop"`
	SuccessNoticeEnabled          bool   `json:"successNoticeEnabled"`
}

func New(address string, backend Backend) *Server {
	mux := http.NewServeMux()
	staticRoot, _ := fs.Sub(staticFiles, "static")
	s := &Server{
		address: address,
		backend: backend,
		server: &http.Server{
			Addr:              address,
			Handler:           mux,
			ReadHeaderTimeout: 5 * time.Second,
		},
	}
	mux.HandleFunc("/api/status", s.handleStatus)
	mux.HandleFunc("/api/config", s.handleConfig)
	mux.HandleFunc("/api/reconnect", s.handleReconnect)
	mux.HandleFunc("/api/open-panel", s.handleOpenPanel)
	mux.HandleFunc("/api/open-download-dir", s.handleOpenDownloadDir)
	mux.HandleFunc("/api/clear-download-dir", s.handleClearDownloadDir)
	mux.HandleFunc("/api/send-file", s.handleSendFile)
	mux.HandleFunc("/api/send-text", s.handleSendText)
	mux.HandleFunc("/api/confirm-pending-clipboard-files", s.handleConfirmPendingClipboardFiles)
	mux.HandleFunc("/api/fetch-latest-text", s.handleFetchLatestText)
	mux.HandleFunc("/api/download-latest-file", s.handleDownloadLatestFile)
	mux.HandleFunc("/api/fetch-latest-file-to-clipboard", s.handleFetchLatestFileToClipboard)
	mux.HandleFunc("/api/suppress-clipboard-files", s.handleSuppressClipboardFiles)
	mux.HandleFunc("/tips/confirm-pending-clipboard-files", s.handleConfirmPendingClipboardFilesTip)
	mux.Handle("/", http.FileServer(http.FS(staticRoot)))
	return s
}

func (s *Server) Start() error {
	ln, err := net.Listen("tcp", s.address)
	if err != nil {
		return err
	}
	s.ln = ln
	go func() {
		_ = s.server.Serve(ln)
	}()
	return nil
}

func (s *Server) Shutdown(ctx context.Context) error {
	return s.server.Shutdown(ctx)
}

func (s *Server) URL() string {
	return fmt.Sprintf("http://%s/", s.address)
}

func (s *Server) PendingClipboardConfirmURL() string {
	return fmt.Sprintf("http://%s/tips/confirm-pending-clipboard-files", s.address)
}

func (s *Server) SendFilesURL() string {
	return fmt.Sprintf("http://%s/api/send-file", s.address)
}

func (s *Server) handleStatus(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	status := s.backend.Status()
	writeJSON(w, http.StatusOK, statusResponse{
		State:        status.State,
		Config:       toConfigView(status.Config),
		Capabilities: status.Capabilities,
	})
}

func (s *Server) handleConfig(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	defer r.Body.Close()

	var payload configView
	if err := json.NewDecoder(r.Body).Decode(&payload); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	cfg := config.Config{
		ServerBase:                  payload.ServerBase,
		Room:                        payload.Room,
		RoomPassword:                payload.RoomPassword,
		DeviceName:                  payload.DeviceName,
		DeviceID:                    s.backend.Status().Config.DeviceID,
		PollInterval:                time.Duration(payload.PollIntervalMs) * time.Millisecond,
		NoticeMode:                  payload.NoticeMode,
		PanelAddress:                payload.PanelAddress,
		OpenPanelOnLaunch:           payload.OpenPanelOnLaunch,
		DownloadDir:                 payload.DownloadDir,
		DownloadCacheRetention:      time.Duration(payload.DownloadCacheRetentionHours) * time.Hour,
		ShellMenuEnabled:            payload.ShellMenuEnabled,
		ClipboardFileConfirmEnabled: payload.ClipboardFileConfirmEnabled,
		ClipboardFileConfirmWindow:  time.Duration(payload.ClipboardFileConfirmWindowSec) * time.Second,
		OpenPanelHotkey:             payload.OpenPanelHotkey,
		SendClipboardHotkey:         payload.SendClipboardHotkey,
		FetchLatestHotkey:           payload.FetchLatestHotkey,
		FetchLatestFileHotkey:       payload.FetchLatestFileHotkey,
		DownloadLatestHotkey:        payload.DownloadLatestHotkey,
		ReconnectDelay:              time.Duration(payload.ReconnectDelayMs) * time.Millisecond,
		MaxReconnectAttempts:        payload.MaxReconnectAttempts,
		TipWidth:                    payload.TipWidth,
		TipHeight:                   payload.TipHeight,
		TipTheme:                    payload.TipTheme,
		TipLeft:                     payload.TipLeft,
		TipTop:                      payload.TipTop,
		SuccessNoticeEnabled:        payload.SuccessNoticeEnabled,
	}
	if err := s.backend.UpdateConfig(cfg); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	status := s.backend.Status()
	writeJSON(w, http.StatusOK, statusResponse{
		State:        status.State,
		Config:       toConfigView(status.Config),
		Capabilities: status.Capabilities,
	})
}

func (s *Server) handleReconnect(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	s.backend.RequestReconnect()
	writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func (s *Server) handleOpenPanel(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	if err := s.backend.OpenPanel(); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func (s *Server) handleSendFile(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	paths, cleanup, err := collectSendFilePaths(r)
	if cleanup != nil {
		defer cleanup()
	}
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	results, err := s.backend.SendFiles(paths)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"files": results})
}

func collectSendFilePaths(r *http.Request) ([]string, func(), error) {
	if r == nil {
		return nil, nil, errors.New("请求无效")
	}
	mediaType, _, _ := mime.ParseMediaType(r.Header.Get("Content-Type"))
	if strings.EqualFold(mediaType, "multipart/form-data") {
		return collectMultipartUploadPaths(r)
	}
	var body struct {
		Paths []string `json:"paths"`
	}
	if r.Body != nil {
		defer r.Body.Close()
		_ = json.NewDecoder(r.Body).Decode(&body)
	}
	return body.Paths, nil, nil
}

func collectMultipartUploadPaths(r *http.Request) ([]string, func(), error) {
	reader, err := r.MultipartReader()
	if err != nil {
		return nil, nil, fmt.Errorf("解析上传文件失败: %w", err)
	}
	tempDir, err := os.MkdirTemp("", "cloud-clipboard-panel-upload-*")
	if err != nil {
		return nil, nil, fmt.Errorf("创建临时上传目录失败: %w", err)
	}
	cleanup := func() {
		_ = os.RemoveAll(tempDir)
	}
	paths := make([]string, 0, 4)
	for {
		part, err := reader.NextPart()
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			cleanup()
			return nil, nil, fmt.Errorf("读取上传内容失败: %w", err)
		}
		if part.FileName() == "" {
			_ = part.Close()
			continue
		}
		targetPath, err := uniqueUploadPath(tempDir, part.FileName())
		if err != nil {
			_ = part.Close()
			cleanup()
			return nil, nil, err
		}
		dst, err := os.Create(targetPath)
		if err != nil {
			_ = part.Close()
			cleanup()
			return nil, nil, fmt.Errorf("创建临时上传文件失败: %w", err)
		}
		if _, err := io.Copy(dst, part); err != nil {
			_ = dst.Close()
			_ = part.Close()
			cleanup()
			return nil, nil, fmt.Errorf("写入临时上传文件失败: %w", err)
		}
		_ = dst.Close()
		_ = part.Close()
		paths = append(paths, targetPath)
	}
	if len(paths) == 0 {
		cleanup()
		return nil, nil, errors.New("当前没有可发送的文件")
	}
	return paths, cleanup, nil
}

func uniqueUploadPath(dir string, fileName string) (string, error) {
	baseName := filepath.Base(strings.TrimSpace(fileName))
	if baseName == "" || baseName == "." || baseName == string(filepath.Separator) {
		return "", errors.New("上传文件名无效")
	}
	target := filepath.Join(dir, baseName)
	if _, err := os.Stat(target); errors.Is(err, os.ErrNotExist) {
		return target, nil
	}
	ext := filepath.Ext(baseName)
	nameOnly := strings.TrimSuffix(baseName, ext)
	for index := 1; ; index++ {
		candidate := filepath.Join(dir, fmt.Sprintf("%s (%d)%s", nameOnly, index, ext))
		if _, err := os.Stat(candidate); errors.Is(err, os.ErrNotExist) {
			return candidate, nil
		}
	}
}

func (s *Server) handleSendText(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var body struct {
		Text          string `json:"text"`
		FromClipboard bool   `json:"fromClipboard"`
	}
	if r.Body != nil {
		defer r.Body.Close()
		_ = json.NewDecoder(r.Body).Decode(&body)
	}
	result, err := s.backend.SendText(body.Text, body.FromClipboard)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"text": result})
}

func (s *Server) handleConfirmPendingClipboardFiles(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	results, err := s.backend.ConfirmPendingClipboardFiles()
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"files": results})
}

func (s *Server) handleConfirmPendingClipboardFilesTip(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	results, err := s.backend.ConfirmPendingClipboardFiles()
	if err != nil {
		writeTipHTML(w, http.StatusBadRequest, "发送失败", err.Error())
		return
	}
	detail := "待确认剪贴板文件已发送。"
	if len(results) > 0 {
		detail = "已发送：" + results[0]
		if len(results) > 1 {
			detail = fmt.Sprintf("已发送：%s 等 %d 项", results[0], len(results))
		}
	}
	writeTipHTML(w, http.StatusOK, "发送完成", detail)
}

func (s *Server) handleOpenDownloadDir(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	if err := s.backend.OpenDownloadDir(); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func (s *Server) handleClearDownloadDir(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	count, err := s.backend.ClearDownloadDir()
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"removed": count})
}

func (s *Server) handleFetchLatestText(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	text, err := s.backend.FetchLatestText()
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"text": text})
}

func (s *Server) handleDownloadLatestFile(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	path, err := s.backend.DownloadLatestFile()
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"path": path})
}

func (s *Server) handleFetchLatestFileToClipboard(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	path, err := s.backend.FetchLatestFileToClipboard()
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"path": path})
}

func (s *Server) handleSuppressClipboardFiles(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	defer r.Body.Close()
	var body struct {
		Paths []string `json:"paths"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	s.backend.SuppressClipboardFiles(body.Paths)
	writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}
func DetectCapabilities() CapabilityView {
	view := CapabilityView{
		Platform: runtime.GOOS,
	}
	switch runtime.GOOS {
	case "windows":
		view.FilePicker = "支持本地文件选择"
		view.FileClipboardWrite = "支持把最新文件写入系统文件剪贴板"
		view.ShellMenu = "支持 Windows 右键子菜单"
		view.ShellMenuRecommended = true
		view.ClipboardFileConfirmation = "支持剪贴板文件待确认发送"
	case "linux":
		view.FilePicker = "支持本地文件选择，依赖 zenity、kdialog 或 yad"
		view.FileClipboardWrite = "支持文件剪贴板，依赖 wl-copy 或 xclip"
		view.ShellMenu = "当前未接入桌面右键子菜单"
		view.ClipboardFileConfirmation = "支持剪贴板文件待确认发送"
	case "darwin":
		view.FilePicker = "支持本地文件选择"
		view.FileClipboardWrite = "支持把最新文件写入系统文件剪贴板"
		view.ShellMenu = "当前未接入 Finder 右键子菜单"
		view.ClipboardFileConfirmation = "支持剪贴板文件待确认发送"
	default:
		view.FilePicker = "当前平台暂未实现本地文件选择"
		view.FileClipboardWrite = "当前平台暂不支持文件剪贴板写入"
		view.ShellMenu = "当前平台未接入右键子菜单"
		view.ClipboardFileConfirmation = "当前平台仅保留基础文本同步链路"
	}
	return view
}
func toConfigView(cfg config.Config) configView {
	return configView{
		ServerBase:                    cfg.ServerBase,
		Room:                          cfg.Room,
		RoomPassword:                  cfg.RoomPassword,
		DeviceName:                    cfg.DeviceName,
		PollIntervalMs:                cfg.PollInterval.Milliseconds(),
		NoticeMode:                    cfg.NoticeMode,
		PanelAddress:                  cfg.PanelAddress,
		OpenPanelOnLaunch:             cfg.OpenPanelOnLaunch,
		DownloadDir:                   cfg.DownloadDir,
		DownloadCacheRetentionHours:   int64(cfg.DownloadCacheRetention / time.Hour),
		ShellMenuEnabled:              cfg.ShellMenuEnabled,
		ClipboardFileConfirmEnabled:   cfg.ClipboardFileConfirmEnabled,
		ClipboardFileConfirmWindowSec: int64(cfg.ClipboardFileConfirmWindow / time.Second),
		OpenPanelHotkey:               cfg.OpenPanelHotkey,
		SendClipboardHotkey:           cfg.SendClipboardHotkey,
		FetchLatestHotkey:             cfg.FetchLatestHotkey,
		FetchLatestFileHotkey:         cfg.FetchLatestFileHotkey,
		DownloadLatestHotkey:          cfg.DownloadLatestHotkey,
		ReconnectDelayMs:              cfg.ReconnectDelay.Milliseconds(),
		MaxReconnectAttempts:          cfg.MaxReconnectAttempts,
		TipWidth:                      cfg.TipWidth,
		TipHeight:                     cfg.TipHeight,
		TipTheme:                      cfg.TipTheme,
		TipLeft:                       cfg.TipLeft,
		TipTop:                        cfg.TipTop,
		SuccessNoticeEnabled:          cfg.SuccessNoticeEnabled,
	}
}

func writeJSON(w http.ResponseWriter, code int, v interface{}) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(v)
}

func writeTipHTML(w http.ResponseWriter, code int, title string, detail string) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(code)
	_, _ = fmt.Fprintf(w, `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>%s</title>
  <style>
    body {
      margin: 0;
      font-family: "Microsoft YaHei UI", "Segoe UI", sans-serif;
      background: linear-gradient(180deg, #eef4ff 0%%, #f7faff 100%%);
      color: #18324f;
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
    }
    .card {
      width: min(92vw, 420px);
      border-radius: 18px;
      background: #fff;
      box-shadow: 0 18px 40px rgba(24, 50, 79, 0.14);
      padding: 24px;
    }
    h1 {
      margin: 0 0 10px;
      font-size: 22px;
    }
    p {
      margin: 0;
      color: #66788f;
      line-height: 1.7;
      word-break: break-all;
    }
  </style>
</head>
<body>
  <div class="card">
    <h1>%s</h1>
    <p>%s</p>
  </div>
</body>
</html>`, title, title, detail)
}
