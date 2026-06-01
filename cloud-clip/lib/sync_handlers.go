package lib

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strings"
	"time"
)

func (s *ClipboardServer) writeSyncError(w http.ResponseWriter, status int, message string) {
	s.syncHub.WriteJSON(w, status, map[string]interface{}{
		"error":   http.StatusText(status),
		"message": message,
	})
}

func (s *ClipboardServer) validateSyncRoomAccess(w http.ResponseWriter, r *http.Request, room string) bool {
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
	w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Room-Auth-Tokens")

	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusOK)
		return false
	}

	normalizedRoom := normalizeRoomName(room)
	requirement := s.resolveRoomAuth(normalizedRoom)
	if !requirement.Required {
		return true
	}

	token := extractAuthToken(r)
	if token == "" {
		s.writeSyncError(w, http.StatusUnauthorized, "需要房间访问令牌")
		return false
	}
	if !s.canAccessRoom(normalizedRoom, token) {
		s.writeSyncError(w, http.StatusUnauthorized, "无权访问该同步房间")
		return false
	}
	return true
}

func (s *ClipboardServer) handleSyncServer(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Header().Set("Access-Control-Allow-Methods", "GET, OPTIONS")
	w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Room-Auth-Tokens")
	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusOK)
		return
	}
	if r.Method != http.MethodGet {
		http.Error(w, "仅允许 GET 请求", http.StatusMethodNotAllowed)
		return
	}

	room := normalizeRoomName(r.URL.Query().Get("room"))
	authNeeded := s.resolveRoomAuth(room).Required
	authorized := true
	if authNeeded {
		authorized = s.canAccessRoom(room, extractAuthToken(r))
	}

	wsProtocol := "ws"
	if r.TLS != nil || r.Header.Get("X-Forwarded-Proto") == "https" {
		wsProtocol = "wss"
	}
	s.syncHub.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"server":        fmt.Sprintf("%s://%s%s/sync/ws", wsProtocol, r.Host, s.config.Server.Prefix),
		"auth":          authNeeded,
		"authorized":    authorized,
		"room":          room,
		"roomProtected": s.hasRoomAuthEntry(room),
	})
}

func (s *ClipboardServer) handleSyncDevices(w http.ResponseWriter, r *http.Request) {
	room := normalizeRoomName(r.URL.Query().Get("room"))
	if !s.validateSyncRoomAccess(w, r, room) {
		return
	}
	if r.Method != http.MethodGet {
		http.Error(w, "仅允许 GET 请求", http.StatusMethodNotAllowed)
		return
	}
	s.syncHub.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"devices": s.syncHub.ListDevices(room),
		"summary": s.syncHub.GetRoomSummary(room),
	})
}

func (s *ClipboardServer) handleSyncStatus(w http.ResponseWriter, r *http.Request) {
	room := normalizeRoomName(r.URL.Query().Get("room"))
	if !s.validateSyncRoomAccess(w, r, room) {
		return
	}
	if r.Method != http.MethodGet {
		http.Error(w, "仅允许 GET 请求", http.StatusMethodNotAllowed)
		return
	}
	authRequirement := s.resolveRoomAuth(room)
	deviceID := strings.TrimSpace(r.URL.Query().Get("deviceId"))
	globalPasswordConfigured := normalizeAuthValue(s.config.Server.Auth) != ""
	roomPasswordConfigured := false
	if roomPassword, ok := s.config.Server.RoomAuth[room]; ok && strings.TrimSpace(roomPassword) != "" {
		roomPasswordConfigured = true
	}
	s.syncHub.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"room":          room,
		"roomProtected": s.hasRoomAuthEntry(room),
		"authRequired":  authRequirement.Required,
		"authMode": map[string]interface{}{
			"usesGlobalPassword": globalPasswordConfigured,
			"usesRoomPassword":   roomPasswordConfigured,
		},
		"currentDevice":  s.syncHub.GetDevice(deviceID, room),
		"summary":        s.syncHub.GetRoomSummary(room),
		"recentMessages": s.syncHub.GetRecentMessages(room),
		"recentPayloads": s.syncHub.GetRecentPayloads(room),
		"limits": map[string]interface{}{
			"textLimit":    s.config.Text.Limit,
			"historyLimit": s.config.Server.History,
		},
		"cleanup": map[string]interface{}{
			"stateCleanup":        s.config.Sync.StateCleanup,
			"messageExpire":       s.config.Sync.MessageExpire,
			"payloadExpire":       s.config.Sync.PayloadExpire,
			"pendingDeviceExpire": s.config.Sync.PendingDeviceExpire,
			"trustedDeviceExpire": s.config.Sync.TrustedDeviceExpire,
		},
		"serverTime": time.Now().UnixMilli(),
	})
}

func (s *ClipboardServer) handleSyncBootstrap(w http.ResponseWriter, r *http.Request) {
	room := normalizeRoomName(r.URL.Query().Get("room"))
	if !s.validateSyncRoomAccess(w, r, room) {
		return
	}
	if r.Method != http.MethodGet {
		http.Error(w, "仅允许 GET 请求", http.StatusMethodNotAllowed)
		return
	}

	deviceID := strings.TrimSpace(r.URL.Query().Get("deviceId"))
	s.syncHub.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"device":         s.syncHub.GetDevice(deviceID, room),
		"recentMessages": s.syncHub.GetRecentMessages(room),
		"recentPayloads": s.syncHub.GetRecentPayloads(room),
		"summary":        s.syncHub.GetRoomSummary(room),
	})
}

type syncPairRequestBody struct {
	Room       string                 `json:"room"`
	DeviceID   string                 `json:"deviceId"`
	Name       string                 `json:"name"`
	Platform   string                 `json:"platform"`
	ClientType string                 `json:"clientType"`
	Meta       map[string]interface{} `json:"meta"`
}

func (s *ClipboardServer) handleSyncPairRequest(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost && r.Method != http.MethodOptions {
		http.Error(w, "仅允许 POST 请求", http.StatusMethodNotAllowed)
		return
	}
	var body syncPairRequestBody
	if r.Method == http.MethodPost {
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			s.writeSyncError(w, http.StatusBadRequest, "无效的请求体")
			return
		}
	}
	if !s.validateSyncRoomAccess(w, r, body.Room) {
		return
	}
	if r.Method == http.MethodOptions {
		return
	}
	if strings.TrimSpace(body.DeviceID) == "" {
		s.writeSyncError(w, http.StatusBadRequest, "缺少 deviceId")
		return
	}
	device, err := s.syncHub.RequestPair(SyncDevice{
		DeviceID:   body.DeviceID,
		Room:       body.Room,
		Name:       body.Name,
		Platform:   body.Platform,
		ClientType: body.ClientType,
		Meta:       body.Meta,
	})
	if err != nil {
		s.writeSyncError(w, http.StatusInternalServerError, "保存设备失败")
		return
	}
	s.syncHub.WriteJSON(w, http.StatusOK, map[string]interface{}{"device": device})
}

type syncTrustBody struct {
	Room     string `json:"room"`
	DeviceID string `json:"deviceId"`
	Name     string `json:"name"`
	Trusted  *bool  `json:"trusted"`
}

func (s *ClipboardServer) handleSyncPairApprove(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost && r.Method != http.MethodOptions {
		http.Error(w, "仅允许 POST 请求", http.StatusMethodNotAllowed)
		return
	}
	var body syncTrustBody
	if r.Method == http.MethodPost {
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			s.writeSyncError(w, http.StatusBadRequest, "无效的请求体")
			return
		}
	}
	if !s.validateSyncRoomAccess(w, r, body.Room) {
		return
	}
	if r.Method == http.MethodOptions {
		return
	}
	if strings.TrimSpace(body.DeviceID) == "" {
		s.writeSyncError(w, http.StatusBadRequest, "缺少 deviceId")
		return
	}
	trusted := true
	device, ok, err := s.syncHub.UpdateDeviceTrust(body.DeviceID, body.Room, &trusted, body.Name)
	if err != nil {
		s.writeSyncError(w, http.StatusInternalServerError, "更新设备失败")
		return
	}
	if !ok {
		s.writeSyncError(w, http.StatusNotFound, "设备不存在")
		return
	}
	s.syncHub.Broadcast(body.Room, "", false, syncOutgoingEnvelope{
		Event: "deviceState",
		Data: map[string]interface{}{
			"type":     "trusted",
			"deviceId": body.DeviceID,
			"trusted":  true,
		},
	})
	s.syncHub.WriteJSON(w, http.StatusOK, map[string]interface{}{"device": device})
}

func (s *ClipboardServer) handleSyncDeviceTrust(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost && r.Method != http.MethodOptions {
		http.Error(w, "仅允许 POST 请求", http.StatusMethodNotAllowed)
		return
	}

	prefix := s.config.Server.Prefix + "/api/sync/device/"
	path := strings.TrimPrefix(r.URL.Path, prefix)
	parts := strings.Split(strings.Trim(path, "/"), "/")
	if len(parts) < 2 || parts[1] != "trust" {
		http.NotFound(w, r)
		return
	}
	deviceID := strings.TrimSpace(parts[0])

	var body syncTrustBody
	if r.Method == http.MethodPost {
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			s.writeSyncError(w, http.StatusBadRequest, "无效的请求体")
			return
		}
	}
	if !s.validateSyncRoomAccess(w, r, body.Room) {
		return
	}
	if r.Method == http.MethodOptions {
		return
	}
	if body.Trusted == nil {
		s.writeSyncError(w, http.StatusBadRequest, "缺少 trusted")
		return
	}
	device, ok, err := s.syncHub.UpdateDeviceTrust(deviceID, body.Room, body.Trusted, body.Name)
	if err != nil {
		s.writeSyncError(w, http.StatusInternalServerError, "更新设备失败")
		return
	}
	if !ok {
		s.writeSyncError(w, http.StatusNotFound, "设备不存在")
		return
	}
	trustedValue := false
	if body.Trusted != nil {
		trustedValue = *body.Trusted
	}
	s.syncHub.Broadcast(body.Room, "", false, syncOutgoingEnvelope{
		Event: "deviceState",
		Data: map[string]interface{}{
			"type":     "trusted",
			"deviceId": deviceID,
			"trusted":  trustedValue,
		},
	})
	s.syncHub.WriteJSON(w, http.StatusOK, map[string]interface{}{"device": device})
}

func normalizeSyncPayloadKind(kind string) string {
	switch strings.ToLower(strings.TrimSpace(kind)) {
	case "image":
		return "image"
	case "file":
		return "file"
	default:
		return ""
	}
}

func sanitizeSyncPayloadURL(raw string) (string, bool) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return "", true
	}
	parsed, err := url.Parse(raw)
	if err != nil {
		return "", false
	}
	if parsed.IsAbs() {
		scheme := strings.ToLower(parsed.Scheme)
		if scheme != "http" && scheme != "https" {
			return "", false
		}
		return parsed.String(), true
	}
	if strings.HasPrefix(raw, "//") {
		return "", false
	}
	return raw, true
}

type syncPayloadNoticeBody struct {
	PayloadID      string `json:"payloadId"`
	SourceDeviceID string `json:"sourceDeviceId"`
	Room           string `json:"room"`
	Kind           string `json:"kind"`
	Title          string `json:"title"`
	Mime           string `json:"mime"`
	Size           int64  `json:"size"`
	ActionURL      string `json:"actionUrl"`
	DownloadURL    string `json:"downloadUrl"`
	CreatedAt      int64  `json:"createdAt"`
}

func (s *ClipboardServer) handleSyncPayloadNotice(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost && r.Method != http.MethodOptions {
		http.Error(w, "仅允许 POST 请求", http.StatusMethodNotAllowed)
		return
	}

	var body syncPayloadNoticeBody
	if r.Method == http.MethodPost {
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			s.writeSyncError(w, http.StatusBadRequest, "无效的请求体")
			return
		}
	}
	if !s.validateSyncRoomAccess(w, r, body.Room) {
		return
	}
	if r.Method == http.MethodOptions {
		return
	}
	body.Kind = normalizeSyncPayloadKind(body.Kind)
	if body.Kind == "" {
		s.writeSyncError(w, http.StatusBadRequest, "kind 仅支持 image 或 file")
		return
	}
	body.Title = strings.TrimSpace(body.Title)
	if body.Title == "" {
		s.writeSyncError(w, http.StatusBadRequest, "缺少 title")
		return
	}
	if body.Size < 0 {
		s.writeSyncError(w, http.StatusBadRequest, "size 不能小于 0")
		return
	}
	body.SourceDeviceID = strings.TrimSpace(body.SourceDeviceID)
	if body.SourceDeviceID == "" {
		s.writeSyncError(w, http.StatusBadRequest, "缺少 sourceDeviceId")
		return
	}
	var ok bool
	body.ActionURL, ok = sanitizeSyncPayloadURL(body.ActionURL)
	if !ok {
		s.writeSyncError(w, http.StatusBadRequest, "actionUrl 非法")
		return
	}
	body.DownloadURL, ok = sanitizeSyncPayloadURL(body.DownloadURL)
	if !ok {
		s.writeSyncError(w, http.StatusBadRequest, "downloadUrl 非法")
		return
	}
	if body.ActionURL == "" && body.DownloadURL == "" {
		s.writeSyncError(w, http.StatusBadRequest, "actionUrl 与 downloadUrl 至少提供一个")
		return
	}

	payload, err := s.syncHub.AddPayloadNotice(SyncPayloadNotice{
		PayloadID:      body.PayloadID,
		SourceDeviceID: body.SourceDeviceID,
		Room:           body.Room,
		Kind:           body.Kind,
		Title:          body.Title,
		Mime:           body.Mime,
		Size:           body.Size,
		ActionURL:      body.ActionURL,
		DownloadURL:    body.DownloadURL,
		CreatedAt:      body.CreatedAt,
	})
	if err != nil {
		s.writeSyncError(w, http.StatusInternalServerError, "保存 payload 通知失败")
		return
	}

	s.syncHub.Broadcast(body.Room, body.SourceDeviceID, true, syncOutgoingEnvelope{
		Event: "payloadNotice",
		Data:  payload,
	})
	s.syncHub.WriteJSON(w, http.StatusOK, map[string]interface{}{"payload": payload})
}

func (s *ClipboardServer) handleSyncWebSocket(w http.ResponseWriter, r *http.Request) {
	room := normalizeRoomName(r.URL.Query().Get("room"))
	if !s.validateSyncRoomAccess(w, r, room) {
		return
	}
	if r.Method != http.MethodGet {
		http.Error(w, "仅允许 GET 请求", http.StatusMethodNotAllowed)
		return
	}

	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		s.logger.Printf("同步 WebSocket 升级失败: %v", err)
		return
	}
	authToken := extractAuthToken(r)

	go func() {
		defer func() {
			session := s.syncHub.GetSession(conn)
			if session != nil && session.Ready {
				_ = s.syncHub.MarkDeviceOffline(conn)
				s.syncHub.Broadcast(session.Room, "", false, syncOutgoingEnvelope{
					Event: "deviceState",
					Data: map[string]interface{}{
						"type":     "offline",
						"deviceId": session.DeviceID,
					},
				})
			}
			_ = conn.Close()
		}()

		for {
			var envelope syncEnvelope
			if err := conn.ReadJSON(&envelope); err != nil {
				return
			}

			switch envelope.Event {
			case "hello":
				var hello syncHelloPayload
				if err := json.Unmarshal(envelope.Data, &hello); err != nil {
					_ = conn.WriteJSON(syncOutgoingEnvelope{Event: "error", Data: map[string]string{"message": "hello 数据无效"}})
					continue
				}
				hello.Room = normalizeRoomName(hello.Room)
				if hello.Room != room {
					_ = conn.WriteJSON(syncOutgoingEnvelope{Event: "forbidden", Data: map[string]string{"message": "房间不匹配"}})
					return
				}
				existing := s.syncHub.GetDevice(hello.DeviceID, hello.Room)
				var device map[string]interface{}
				if existing != nil {
					if upserted, err := s.syncHub.UpsertDevice(SyncDevice{
						DeviceID:   hello.DeviceID,
						Room:       hello.Room,
						Name:       hello.Name,
						Platform:   hello.Platform,
						ClientType: hello.ClientType,
						Meta:       hello.Meta,
					}); err == nil {
						device = upserted
					} else {
						_ = conn.WriteJSON(syncOutgoingEnvelope{Event: "error", Data: map[string]string{"message": "更新设备失败"}})
						return
					}
				} else {
					var err error
					device, err = s.syncHub.RequestPair(SyncDevice{
						DeviceID:   hello.DeviceID,
						Room:       hello.Room,
						Name:       hello.Name,
						Platform:   hello.Platform,
						ClientType: hello.ClientType,
						Meta:       hello.Meta,
					})
					if err != nil {
						_ = conn.WriteJSON(syncOutgoingEnvelope{Event: "error", Data: map[string]string{"message": "注册设备失败"}})
						return
					}
				}
				if err := s.syncHub.MarkDeviceOnline(conn, hello.Room, hello.DeviceID, authToken); err != nil {
					_ = conn.WriteJSON(syncOutgoingEnvelope{Event: "error", Data: map[string]string{"message": "标记设备在线失败"}})
					return
				}
				_ = conn.WriteJSON(syncOutgoingEnvelope{
					Event: "helloAck",
					Data: map[string]interface{}{
						"device":         device,
						"recentMessages": s.syncHub.GetRecentMessages(hello.Room),
						"recentPayloads": s.syncHub.GetRecentPayloads(hello.Room),
					},
				})
				s.syncHub.Broadcast(hello.Room, "", false, syncOutgoingEnvelope{
					Event: "deviceState",
					Data: map[string]interface{}{
						"type":     "online",
						"deviceId": hello.DeviceID,
					},
				})
			case "clipboardPublish":
				session := s.syncHub.GetSession(conn)
				if session == nil || !session.Ready || !session.Trusted {
					var payload syncClipboardPublishPayload
					_ = json.Unmarshal(envelope.Data, &payload)
					_ = conn.WriteJSON(syncOutgoingEnvelope{
						Event: "clipboardAck",
						Data: map[string]interface{}{
							"messageId": payload.MessageID,
							"status":    "rejected",
							"reason":    "device_not_trusted",
						},
					})
					continue
				}

				var payload syncClipboardPublishPayload
				if err := json.Unmarshal(envelope.Data, &payload); err != nil {
					_ = conn.WriteJSON(syncOutgoingEnvelope{Event: "error", Data: map[string]string{"message": "剪贴板数据无效"}})
					continue
				}
				payload.Text = strings.TrimSpace(payload.Text)
				if payload.Text == "" {
					continue
				}
				if s.config.Text.Limit > 0 && len(payload.Text) > s.config.Text.Limit {
					_ = conn.WriteJSON(syncOutgoingEnvelope{
						Event: "clipboardAck",
						Data: map[string]interface{}{
							"messageId": payload.MessageID,
							"status":    "rejected",
							"reason":    "text_too_long",
						},
					})
					continue
				}
				if s.syncHub.HasMessage(payload.MessageID) {
					_ = conn.WriteJSON(syncOutgoingEnvelope{
						Event: "clipboardAck",
						Data: map[string]interface{}{
							"messageId": payload.MessageID,
							"status":    "duplicate",
						},
					})
					continue
				}
				record, err := s.syncHub.AddMessage(SyncMessageRecord{
					MessageID:      payload.MessageID,
					SourceDeviceID: session.DeviceID,
					Room:           session.Room,
					Mime:           "text/plain",
					Text:           payload.Text,
					CreatedAt:      payload.CreatedAt,
				})
				if err != nil {
					_ = conn.WriteJSON(syncOutgoingEnvelope{
						Event: "clipboardAck",
						Data: map[string]interface{}{
							"messageId": payload.MessageID,
							"status":    "rejected",
							"reason":    "persist_failed",
						},
					})
					continue
				}
				s.syncHub.Broadcast(session.Room, session.DeviceID, true, syncOutgoingEnvelope{
					Event: "clipboardSync",
					Data:  record,
				})
				_ = conn.WriteJSON(syncOutgoingEnvelope{
					Event: "clipboardAck",
					Data: map[string]interface{}{
						"messageId": record.MessageID,
						"status":    "ok",
						"serverAt":  time.Now().UnixMilli(),
					},
				})
			}
		}
	}()
}
