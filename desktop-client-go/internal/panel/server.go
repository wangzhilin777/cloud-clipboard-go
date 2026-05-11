package panel

import (
	"context"
	"embed"
	"encoding/json"
	"fmt"
	"io/fs"
	"net"
	"net/http"
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
	SendFiles(paths []string) ([]string, error)
	SendText(text string, fromClipboard bool) (string, error)
	FetchLatestText() (string, error)
	DownloadLatestFile() (string, error)
}

type Server struct {
	address string
	backend Backend
	server  *http.Server
	ln      net.Listener
}

type StateSnapshot struct {
	Connected        bool   `json:"connected"`
	Trusted          bool   `json:"trusted"`
	Status           string `json:"status"`
	LastError        string `json:"lastError,omitempty"`
	LastRemoteTextAt int64  `json:"lastRemoteTextAt,omitempty"`
	LastPayloadTitle string `json:"lastPayloadTitle,omitempty"`
	LastPayloadKind  string `json:"lastPayloadKind,omitempty"`
	LastPayloadAt    int64  `json:"lastPayloadAt,omitempty"`
	LastActionType   string `json:"lastActionType,omitempty"`
	LastActionDetail string `json:"lastActionDetail,omitempty"`
	LastActionAt     int64  `json:"lastActionAt,omitempty"`
	LastUpdatedAt    int64  `json:"lastUpdatedAt"`
}

type StatusView struct {
	Config config.Config `json:"config"`
	State  StateSnapshot `json:"state"`
}

type statusResponse struct {
	State  StateSnapshot `json:"state"`
	Config configView    `json:"config"`
}

type configView struct {
	ServerBase           string `json:"serverBase"`
	Room                 string `json:"room"`
	RoomPassword         string `json:"roomPassword"`
	DeviceName           string `json:"deviceName"`
	PollIntervalMs       int64  `json:"pollIntervalMs"`
	NoticeMode           string `json:"noticeMode"`
	PanelAddress         string `json:"panelAddress"`
	OpenPanelOnLaunch    bool   `json:"openPanelOnLaunch"`
	DownloadDir          string `json:"downloadDir"`
	SendClipboardHotkey  string `json:"sendClipboardHotkey"`
	FetchLatestHotkey    string `json:"fetchLatestHotkey"`
	DownloadLatestHotkey string `json:"downloadLatestHotkey"`
	ReconnectDelayMs     int64  `json:"reconnectDelayMs"`
	MaxReconnectAttempts int    `json:"maxReconnectAttempts"`
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
	mux.HandleFunc("/api/fetch-latest-text", s.handleFetchLatestText)
	mux.HandleFunc("/api/download-latest-file", s.handleDownloadLatestFile)
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

func (s *Server) handleStatus(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	status := s.backend.Status()
	writeJSON(w, http.StatusOK, statusResponse{
		State:  status.State,
		Config: toConfigView(status.Config),
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
		ServerBase:           payload.ServerBase,
		Room:                 payload.Room,
		RoomPassword:         payload.RoomPassword,
		DeviceName:           payload.DeviceName,
		DeviceID:             s.backend.Status().Config.DeviceID,
		PollInterval:         time.Duration(payload.PollIntervalMs) * time.Millisecond,
		NoticeMode:           payload.NoticeMode,
		PanelAddress:         payload.PanelAddress,
		OpenPanelOnLaunch:    payload.OpenPanelOnLaunch,
		DownloadDir:          payload.DownloadDir,
		SendClipboardHotkey:  payload.SendClipboardHotkey,
		FetchLatestHotkey:    payload.FetchLatestHotkey,
		DownloadLatestHotkey: payload.DownloadLatestHotkey,
		ReconnectDelay:       time.Duration(payload.ReconnectDelayMs) * time.Millisecond,
		MaxReconnectAttempts: payload.MaxReconnectAttempts,
	}
	if err := s.backend.UpdateConfig(cfg); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	status := s.backend.Status()
	writeJSON(w, http.StatusOK, statusResponse{
		State:  status.State,
		Config: toConfigView(status.Config),
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
	var body struct {
		Paths []string `json:"paths"`
	}
	if r.Body != nil {
		defer r.Body.Close()
		_ = json.NewDecoder(r.Body).Decode(&body)
	}
	results, err := s.backend.SendFiles(body.Paths)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"files": results})
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

func toConfigView(cfg config.Config) configView {
	return configView{
		ServerBase:           cfg.ServerBase,
		Room:                 cfg.Room,
		RoomPassword:         cfg.RoomPassword,
		DeviceName:           cfg.DeviceName,
		PollIntervalMs:       cfg.PollInterval.Milliseconds(),
		NoticeMode:           cfg.NoticeMode,
		PanelAddress:         cfg.PanelAddress,
		OpenPanelOnLaunch:    cfg.OpenPanelOnLaunch,
		DownloadDir:          cfg.DownloadDir,
		SendClipboardHotkey:  cfg.SendClipboardHotkey,
		FetchLatestHotkey:    cfg.FetchLatestHotkey,
		DownloadLatestHotkey: cfg.DownloadLatestHotkey,
		ReconnectDelayMs:     cfg.ReconnectDelay.Milliseconds(),
		MaxReconnectAttempts: cfg.MaxReconnectAttempts,
	}
}

func writeJSON(w http.ResponseWriter, code int, v interface{}) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(v)
}
