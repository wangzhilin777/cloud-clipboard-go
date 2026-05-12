package lib

import (
	"encoding/json"
	"errors"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/gorilla/websocket"
)

type SyncHub struct {
	logger         LoggerLike
	statePath      string
	messageLimit   int
	textLimit      int
	mu             sync.RWMutex
	state          syncState
	sessions       map[*websocket.Conn]*SyncSession
	onlineByDevice map[string]map[*websocket.Conn]bool
}

type LoggerLike interface {
	Printf(format string, v ...interface{})
}

type SyncSession struct {
	Conn      *websocket.Conn
	Room      string
	DeviceID  string
	Trusted   bool
	Ready     bool
	AuthToken string
}

type syncState struct {
	Devices  []SyncDevice        `json:"devices"`
	Messages []SyncMessageRecord `json:"messages"`
	Payloads []SyncPayloadNotice `json:"payloads"`
}

type syncEnvelope struct {
	Event string          `json:"event"`
	Data  json.RawMessage `json:"data"`
}

type syncOutgoingEnvelope struct {
	Event string      `json:"event"`
	Data  interface{} `json:"data"`
}

type syncHelloPayload struct {
	Room       string                 `json:"room"`
	DeviceID   string                 `json:"deviceId"`
	Name       string                 `json:"name"`
	Platform   string                 `json:"platform"`
	ClientType string                 `json:"clientType"`
	Meta       map[string]interface{} `json:"meta"`
}

type syncClipboardPublishPayload struct {
	MessageID string `json:"messageId"`
	Text      string `json:"text"`
	CreatedAt int64  `json:"createdAt"`
}

type SyncCleanupPolicy struct {
	MessageExpireMillis       int64
	PayloadExpireMillis       int64
	PendingDeviceExpireMillis int64
	TrustedDeviceExpireMillis int64
}

type SyncCleanupResult struct {
	RemovedMessages int
	RemovedPayloads int
	RemovedDevices  int
}

func NewSyncHub(logger LoggerLike, statePath string, messageLimit int, textLimit int) (*SyncHub, error) {
	if statePath == "" {
		return nil, errors.New("sync state path is required")
	}
	if messageLimit < 50 {
		messageLimit = 50
	}

	hub := &SyncHub{
		logger:         logger,
		statePath:      statePath,
		messageLimit:   messageLimit,
		textLimit:      textLimit,
		sessions:       make(map[*websocket.Conn]*SyncSession),
		onlineByDevice: make(map[string]map[*websocket.Conn]bool),
		state: syncState{
			Devices:  []SyncDevice{},
			Messages: []SyncMessageRecord{},
			Payloads: []SyncPayloadNotice{},
		},
	}

	if err := hub.load(); err != nil {
		return nil, err
	}

	return hub, nil
}

func (h *SyncHub) load() error {
	data, err := os.ReadFile(h.statePath)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return nil
		}
		return err
	}

	var loaded syncState
	if err := json.Unmarshal(data, &loaded); err != nil {
		return err
	}
	if loaded.Devices == nil {
		loaded.Devices = []SyncDevice{}
	}
	if loaded.Messages == nil {
		loaded.Messages = []SyncMessageRecord{}
	}
	if loaded.Payloads == nil {
		loaded.Payloads = []SyncPayloadNotice{}
	}
	for i := range loaded.Devices {
		loaded.Devices[i] = normalizeSyncDevice(loaded.Devices[i])
	}
	for i := range loaded.Messages {
		loaded.Messages[i].Room = normalizeSyncRoom(loaded.Messages[i].Room)
		if strings.TrimSpace(loaded.Messages[i].Mime) == "" {
			loaded.Messages[i].Mime = "text/plain"
		}
	}
	for i := range loaded.Payloads {
		loaded.Payloads[i].Room = normalizeSyncRoom(loaded.Payloads[i].Room)
	}
	h.state = loaded
	return nil
}

func (h *SyncHub) persistLocked() error {
	if err := os.MkdirAll(filepath.Dir(h.statePath), 0755); err != nil {
		return err
	}
	data, err := json.MarshalIndent(h.state, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(h.statePath, data, 0644)
}

func normalizeSyncRoom(room string) string {
	return normalizeRoomName(room)
}

func normalizeSyncDevice(payload SyncDevice) SyncDevice {
	now := time.Now().UnixMilli()
	deviceID := strings.TrimSpace(payload.DeviceID)
	if deviceID == "" {
		deviceID = uuid.NewString()
	}

	name := strings.TrimSpace(payload.Name)
	if name == "" {
		name = "未命名设备"
	}

	platform := strings.TrimSpace(payload.Platform)
	if platform == "" {
		platform = "unknown"
	}

	clientType := strings.TrimSpace(payload.ClientType)
	if clientType == "" {
		clientType = "unknown"
	}

	if payload.CreatedAt == 0 {
		payload.CreatedAt = now
	}
	if payload.LastSeenAt == 0 {
		payload.LastSeenAt = now
	}
	if payload.Status == "" {
		if payload.Trusted {
			payload.Status = "trusted"
		} else {
			payload.Status = "pending"
		}
	}
	if payload.Meta == nil {
		payload.Meta = map[string]interface{}{}
	}

	payload.DeviceID = deviceID
	payload.Name = name
	payload.Room = normalizeSyncRoom(payload.Room)
	payload.Platform = platform
	payload.ClientType = clientType
	return payload
}

func (h *SyncHub) deviceKey(room string, deviceID string) string {
	return normalizeSyncRoom(room) + "::" + deviceID
}

func (h *SyncHub) findDeviceIndexLocked(deviceID string, room string) int {
	normalizedRoom := normalizeSyncRoom(room)
	for i, device := range h.state.Devices {
		if device.DeviceID == deviceID && device.Room == normalizedRoom {
			return i
		}
	}
	return -1
}

func (h *SyncHub) isDeviceOnlineLocked(room string, deviceID string) bool {
	clients := h.onlineByDevice[h.deviceKey(room, deviceID)]
	return len(clients) > 0
}

func (h *SyncHub) cloneDeviceLocked(device SyncDevice) SyncDevice {
	cloned := device
	if cloned.Meta == nil {
		cloned.Meta = map[string]interface{}{}
	}
	return cloned
}

func (h *SyncHub) sanitizeDeviceLocked(device SyncDevice) map[string]interface{} {
	return map[string]interface{}{
		"deviceId":   device.DeviceID,
		"name":       device.Name,
		"room":       device.Room,
		"platform":   device.Platform,
		"clientType": device.ClientType,
		"trusted":    device.Trusted,
		"createdAt":  device.CreatedAt,
		"lastSeenAt": device.LastSeenAt,
		"status":     device.Status,
		"meta":       device.Meta,
		"online":     h.isDeviceOnlineLocked(device.Room, device.DeviceID),
	}
}

func (h *SyncHub) RequestPair(payload SyncDevice) (map[string]interface{}, error) {
	h.mu.Lock()
	defer h.mu.Unlock()

	next := normalizeSyncDevice(payload)
	next.Trusted = false
	next.Status = "pending"

	index := h.findDeviceIndexLocked(next.DeviceID, next.Room)
	if index == -1 {
		h.state.Devices = append(h.state.Devices, next)
		index = len(h.state.Devices) - 1
	} else {
		existing := h.state.Devices[index]
		next.CreatedAt = existing.CreatedAt
		next.Trusted = existing.Trusted
		if existing.Trusted {
			next.Status = "trusted"
		}
		h.state.Devices[index] = next
	}

	if err := h.persistLocked(); err != nil {
		return nil, err
	}

	return h.sanitizeDeviceLocked(h.state.Devices[index]), nil
}

func (h *SyncHub) UpsertDevice(payload SyncDevice) (map[string]interface{}, error) {
	h.mu.Lock()
	defer h.mu.Unlock()

	next := normalizeSyncDevice(payload)
	index := h.findDeviceIndexLocked(next.DeviceID, next.Room)
	if index == -1 {
		h.state.Devices = append(h.state.Devices, next)
		index = len(h.state.Devices) - 1
	} else {
		existing := h.state.Devices[index]
		next.CreatedAt = existing.CreatedAt
		next.Trusted = existing.Trusted
		next.Status = existing.Status
		h.state.Devices[index] = next
	}

	if err := h.persistLocked(); err != nil {
		return nil, err
	}
	return h.sanitizeDeviceLocked(h.state.Devices[index]), nil
}

func (h *SyncHub) GetDevice(deviceID string, room string) map[string]interface{} {
	h.mu.RLock()
	defer h.mu.RUnlock()

	index := h.findDeviceIndexLocked(deviceID, room)
	if index == -1 {
		return nil
	}
	return h.sanitizeDeviceLocked(h.state.Devices[index])
}

func (h *SyncHub) getDeviceTrustedLocked(deviceID string, room string) bool {
	index := h.findDeviceIndexLocked(deviceID, room)
	if index == -1 {
		return false
	}
	return h.state.Devices[index].Trusted
}

func (h *SyncHub) ListDevices(room string) []map[string]interface{} {
	h.mu.RLock()
	defer h.mu.RUnlock()

	normalizedRoom := normalizeSyncRoom(room)
	devices := make([]SyncDevice, 0)
	for _, device := range h.state.Devices {
		if device.Room == normalizedRoom {
			devices = append(devices, h.cloneDeviceLocked(device))
		}
	}
	sort.Slice(devices, func(i, j int) bool {
		return devices[i].LastSeenAt > devices[j].LastSeenAt
	})

	result := make([]map[string]interface{}, 0, len(devices))
	for _, device := range devices {
		result = append(result, h.sanitizeDeviceLocked(device))
	}
	return result
}

func (h *SyncHub) UpdateDeviceTrust(deviceID string, room string, trusted *bool, name string) (map[string]interface{}, bool, error) {
	h.mu.Lock()
	defer h.mu.Unlock()

	index := h.findDeviceIndexLocked(deviceID, room)
	if index == -1 {
		return nil, false, nil
	}

	if trusted != nil {
		h.state.Devices[index].Trusted = *trusted
		if *trusted {
			h.state.Devices[index].Status = "trusted"
		} else {
			h.state.Devices[index].Status = "pending"
		}
	}
	if strings.TrimSpace(name) != "" {
		h.state.Devices[index].Name = strings.TrimSpace(name)
	}
	h.state.Devices[index].LastSeenAt = time.Now().UnixMilli()

	if err := h.persistLocked(); err != nil {
		return nil, false, err
	}

	trustedNow := h.state.Devices[index].Trusted
	roomKey := h.deviceKey(h.state.Devices[index].Room, h.state.Devices[index].DeviceID)
	for conn := range h.onlineByDevice[roomKey] {
		if session, ok := h.sessions[conn]; ok {
			session.Trusted = trustedNow
		}
	}

	return h.sanitizeDeviceLocked(h.state.Devices[index]), true, nil
}

func (h *SyncHub) MarkDeviceOnline(conn *websocket.Conn, room string, deviceID string, authToken string) error {
	h.mu.Lock()
	defer h.mu.Unlock()

	index := h.findDeviceIndexLocked(deviceID, room)
	if index != -1 {
		h.state.Devices[index].LastSeenAt = time.Now().UnixMilli()
	}

	if h.sessions[conn] == nil {
		h.sessions[conn] = &SyncSession{Conn: conn}
	}
	h.sessions[conn].Room = normalizeSyncRoom(room)
	h.sessions[conn].DeviceID = deviceID
	h.sessions[conn].Trusted = h.getDeviceTrustedLocked(deviceID, room)
	h.sessions[conn].Ready = true
	h.sessions[conn].AuthToken = authToken

	key := h.deviceKey(room, deviceID)
	if h.onlineByDevice[key] == nil {
		h.onlineByDevice[key] = map[*websocket.Conn]bool{}
	}
	h.onlineByDevice[key][conn] = true

	return h.persistLocked()
}

func (h *SyncHub) MarkDeviceOffline(conn *websocket.Conn) error {
	h.mu.Lock()
	defer h.mu.Unlock()

	session, ok := h.sessions[conn]
	if !ok {
		return nil
	}

	index := h.findDeviceIndexLocked(session.DeviceID, session.Room)
	if index != -1 {
		h.state.Devices[index].LastSeenAt = time.Now().UnixMilli()
	}

	key := h.deviceKey(session.Room, session.DeviceID)
	if clients := h.onlineByDevice[key]; clients != nil {
		delete(clients, conn)
		if len(clients) == 0 {
			delete(h.onlineByDevice, key)
		}
	}
	delete(h.sessions, conn)
	return h.persistLocked()
}

func (h *SyncHub) GetSession(conn *websocket.Conn) *SyncSession {
	h.mu.RLock()
	defer h.mu.RUnlock()

	session := h.sessions[conn]
	if session == nil {
		return nil
	}
	cloned := *session
	return &cloned
}

func (h *SyncHub) HasMessage(messageID string) bool {
	if strings.TrimSpace(messageID) == "" {
		return false
	}
	h.mu.RLock()
	defer h.mu.RUnlock()

	for _, message := range h.state.Messages {
		if message.MessageID == messageID {
			return true
		}
	}
	return false
}

func (h *SyncHub) AddMessage(payload SyncMessageRecord) (SyncMessageRecord, error) {
	h.mu.Lock()
	defer h.mu.Unlock()

	record := payload
	record.Room = normalizeSyncRoom(record.Room)
	record.Mime = "text/plain"
	if record.CreatedAt == 0 {
		record.CreatedAt = time.Now().UnixMilli()
	}
	h.state.Messages = append(h.state.Messages, record)
	if len(h.state.Messages) > h.messageLimit {
		h.state.Messages = h.state.Messages[len(h.state.Messages)-h.messageLimit:]
	}
	return record, h.persistLocked()
}

func (h *SyncHub) GetRecentMessages(room string) []SyncMessageRecord {
	h.mu.RLock()
	defer h.mu.RUnlock()

	normalizedRoom := normalizeSyncRoom(room)
	result := make([]SyncMessageRecord, 0, 20)
	for _, message := range h.state.Messages {
		if message.Room == normalizedRoom {
			result = append(result, message)
		}
	}
	if len(result) > 20 {
		result = result[len(result)-20:]
	}
	return result
}

func (h *SyncHub) AddPayloadNotice(payload SyncPayloadNotice) (SyncPayloadNotice, error) {
	h.mu.Lock()
	defer h.mu.Unlock()

	record := payload
	record.Room = normalizeSyncRoom(record.Room)
	if strings.TrimSpace(record.PayloadID) == "" {
		record.PayloadID = uuid.NewString()
	}
	if record.CreatedAt == 0 {
		record.CreatedAt = time.Now().UnixMilli()
	}
	h.state.Payloads = append(h.state.Payloads, record)
	if len(h.state.Payloads) > h.messageLimit {
		h.state.Payloads = h.state.Payloads[len(h.state.Payloads)-h.messageLimit:]
	}
	return record, h.persistLocked()
}

func (h *SyncHub) GetRecentPayloads(room string) []SyncPayloadNotice {
	h.mu.RLock()
	defer h.mu.RUnlock()

	normalizedRoom := normalizeSyncRoom(room)
	result := make([]SyncPayloadNotice, 0, 20)
	for _, payload := range h.state.Payloads {
		if payload.Room == normalizedRoom {
			result = append(result, payload)
		}
	}
	if len(result) > 20 {
		result = result[len(result)-20:]
	}
	return result
}

func (h *SyncHub) Cleanup(policy SyncCleanupPolicy, now time.Time) (SyncCleanupResult, error) {
	h.mu.Lock()
	defer h.mu.Unlock()

	result := SyncCleanupResult{}
	nowMillis := now.UnixMilli()

	if policy.MessageExpireMillis > 0 {
		filtered := h.state.Messages[:0]
		for _, message := range h.state.Messages {
			if message.CreatedAt > 0 && nowMillis-message.CreatedAt > policy.MessageExpireMillis {
				result.RemovedMessages++
				continue
			}
			filtered = append(filtered, message)
		}
		h.state.Messages = filtered
	}

	if policy.PayloadExpireMillis > 0 {
		filtered := h.state.Payloads[:0]
		for _, payload := range h.state.Payloads {
			if payload.CreatedAt > 0 && nowMillis-payload.CreatedAt > policy.PayloadExpireMillis {
				result.RemovedPayloads++
				continue
			}
			filtered = append(filtered, payload)
		}
		h.state.Payloads = filtered
	}

	if policy.PendingDeviceExpireMillis > 0 || policy.TrustedDeviceExpireMillis > 0 {
		filtered := h.state.Devices[:0]
		for _, device := range h.state.Devices {
			if h.isDeviceOnlineLocked(device.Room, device.DeviceID) {
				filtered = append(filtered, device)
				continue
			}

			lastActiveAt := device.LastSeenAt
			if lastActiveAt == 0 {
				lastActiveAt = device.CreatedAt
			}

			expireMillis := policy.PendingDeviceExpireMillis
			if device.Trusted {
				expireMillis = policy.TrustedDeviceExpireMillis
			}

			if expireMillis > 0 && lastActiveAt > 0 && nowMillis-lastActiveAt > expireMillis {
				result.RemovedDevices++
				continue
			}
			filtered = append(filtered, device)
		}
		h.state.Devices = filtered
	}

	if result.RemovedMessages == 0 && result.RemovedPayloads == 0 && result.RemovedDevices == 0 {
		return result, nil
	}

	return result, h.persistLocked()
}

func (h *SyncHub) WriteJSON(w http.ResponseWriter, status int, payload interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(payload)
}

func (h *SyncHub) Broadcast(room string, sourceDeviceID string, trustedOnly bool, message syncOutgoingEnvelope) {
	h.mu.RLock()
	targets := make([]*websocket.Conn, 0)
	for conn, session := range h.sessions {
		if session == nil || !session.Ready || session.Room != normalizeSyncRoom(room) {
			continue
		}
		if sourceDeviceID != "" && session.DeviceID == sourceDeviceID {
			continue
		}
		if trustedOnly && !session.Trusted {
			continue
		}
		targets = append(targets, conn)
	}
	h.mu.RUnlock()

	for _, conn := range targets {
		if err := conn.WriteJSON(message); err != nil {
			h.logger.Printf("同步广播失败: %v", err)
		}
	}
}
