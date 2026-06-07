package app

import (
	"encoding/json"
	"os"
	"path/filepath"
	"sync"
	"time"
)

type StateStore struct {
	path    string
	mu      sync.Mutex
	current StateSnapshot
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

func NewStateStore(path string) *StateStore {
	return &StateStore{
		path:    path,
		current: StateSnapshot{Status: "idle"},
	}
}

func (s *StateStore) Save(snapshot StateSnapshot) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	snapshot.LastUpdatedAt = time.Now().UnixMilli()
	s.current = snapshot
	if err := os.MkdirAll(filepath.Dir(s.path), 0o755); err != nil {
		return err
	}
	data, err := json.MarshalIndent(snapshot, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(s.path, data, 0o644)
}

func (s *StateStore) Update(mutator func(snapshot *StateSnapshot)) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if mutator != nil {
		mutator(&s.current)
	}
	s.current.LastUpdatedAt = time.Now().UnixMilli()
	if err := os.MkdirAll(filepath.Dir(s.path), 0o755); err != nil {
		return err
	}
	data, err := json.MarshalIndent(s.current, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(s.path, data, 0o644)
}

func (s *StateStore) Current() StateSnapshot {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.current
}
