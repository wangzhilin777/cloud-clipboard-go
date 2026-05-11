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
	ServerBase     string `json:"serverBase"`
	Room           string `json:"room"`
	RoomPassword   string `json:"roomPassword"`
	DeviceName     string `json:"deviceName"`
	PollIntervalMs int64  `json:"pollIntervalMs"`
	NoticeMode     string `json:"noticeMode"`
	PanelAddress   string `json:"panelAddress"`
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
		ServerBase:   payload.ServerBase,
		Room:         payload.Room,
		RoomPassword: payload.RoomPassword,
		DeviceName:   payload.DeviceName,
		DeviceID:     s.backend.Status().Config.DeviceID,
		PollInterval: time.Duration(payload.PollIntervalMs) * time.Millisecond,
		NoticeMode:   payload.NoticeMode,
		PanelAddress: payload.PanelAddress,
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

func toConfigView(cfg config.Config) configView {
	return configView{
		ServerBase:     cfg.ServerBase,
		Room:           cfg.Room,
		RoomPassword:   cfg.RoomPassword,
		DeviceName:     cfg.DeviceName,
		PollIntervalMs: cfg.PollInterval.Milliseconds(),
		NoticeMode:     cfg.NoticeMode,
		PanelAddress:   cfg.PanelAddress,
	}
}

func writeJSON(w http.ResponseWriter, code int, v interface{}) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(v)
}
