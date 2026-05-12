package syncclient

import (
	"bytes"
	"compress/gzip"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/gorilla/websocket"
	"golang.design/x/clipboard"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
)

type Client struct {
	cfg        config.Config
	logger     *log.Logger
	httpClient *http.Client
	events     EventHandler

	mu                sync.Mutex
	conn              *websocket.Conn
	trusted           bool
	connected         bool
	lastLocalText     string
	lastRemoteText    string
	lastPublishedText string
}

type envelope struct {
	Event string          `json:"event"`
	Data  json.RawMessage `json:"data"`
}

type outgoingEnvelope struct {
	Event string      `json:"event"`
	Data  interface{} `json:"data"`
}

type helloPayload struct {
	Room       string                 `json:"room"`
	DeviceID   string                 `json:"deviceId"`
	Name       string                 `json:"name"`
	Platform   string                 `json:"platform"`
	ClientType string                 `json:"clientType"`
	Meta       map[string]interface{} `json:"meta"`
}

type helloAckPayload struct {
	Device struct {
		Trusted bool   `json:"trusted"`
		Status  string `json:"status"`
	} `json:"device"`
}

type clipboardSyncPayload struct {
	MessageID string `json:"messageId"`
	Text      string `json:"text"`
}

type deviceStatePayload struct {
	Type     string `json:"type"`
	DeviceID string `json:"deviceId"`
	Trusted  bool   `json:"trusted"`
}

type payloadNoticePayload struct {
	Kind  string `json:"kind"`
	Title string `json:"title"`
}

func New(cfg config.Config, logger *log.Logger, events EventHandler) *Client {
	if events == nil {
		events = noopEventHandler{}
	}
	return &Client{
		cfg:    cfg,
		logger: logger,
		events: events,
		httpClient: &http.Client{
			Timeout: 15 * time.Second,
		},
	}
}

func (c *Client) Run(ctx context.Context) error {
	if err := clipboard.Init(); err != nil {
		return fmt.Errorf("初始化系统剪贴板失败: %w", err)
	}

	attempt := 0
	for {
		err := c.runSession(ctx)
		if ctx.Err() != nil {
			return ctx.Err()
		}
		if err != nil && !errors.Is(err, context.Canceled) {
			c.logger.Printf("同步连接断开: %v", err)
		}
		attempt++
		if c.cfg.MaxReconnectAttempts > 0 && attempt >= c.cfg.MaxReconnectAttempts {
			c.events.OnReconnectStopped(err)
			return ErrReconnectStopped
		}
		c.events.OnRetrying(attempt, c.cfg.MaxReconnectAttempts, c.cfg.ReconnectDelay, err)
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(c.cfg.ReconnectDelay):
		}
	}
}

func (c *Client) runSession(ctx context.Context) error {
	c.events.OnConnecting()
	wsURL, err := c.fetchServerURL(ctx)
	if err != nil {
		c.events.OnError(err)
		return err
	}

	headers := http.Header{}
	if c.cfg.RoomPassword != "" {
		headers.Set("Authorization", "Bearer "+c.cfg.RoomPassword)
	}

	conn, _, err := websocket.DefaultDialer.DialContext(ctx, wsURL, headers)
	if err != nil {
		c.events.OnError(err)
		return err
	}
	defer conn.Close()

	c.setConnState(conn, true, false)
	c.events.OnConnected()
	defer c.setConnState(nil, false, false)

	if err := c.sendHello(); err != nil {
		return err
	}

	sessionCtx, cancel := context.WithCancel(ctx)
	defer cancel()

	errCh := make(chan error, 2)
	go c.readLoop(sessionCtx, errCh)
	go c.clipboardLoop(sessionCtx, errCh)

	select {
	case <-ctx.Done():
		return ctx.Err()
	case err := <-errCh:
		return err
	}
}

func (c *Client) fetchServerURL(ctx context.Context) (string, error) {
	u := c.cfg.ServerBase + "/sync/server?room=" + url.QueryEscape(c.cfg.Room)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, u, nil)
	if err != nil {
		return "", err
	}
	if c.cfg.RoomPassword != "" {
		req.Header.Set("Authorization", "Bearer "+c.cfg.RoomPassword)
	}
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		body, _ := readResponsePreview(resp)
		return "", fmt.Errorf("获取同步入口失败: HTTP %d %s", resp.StatusCode, strings.TrimSpace(string(body)))
	}
	var payload struct {
		Server string `json:"server"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		return "", err
	}
	if payload.Server == "" {
		return "", errors.New("服务端未返回 WebSocket 地址")
	}
	serverURL, err := url.Parse(payload.Server)
	if err != nil {
		return "", err
	}
	query := serverURL.Query()
	query.Set("room", c.cfg.Room)
	if c.cfg.RoomPassword != "" {
		query.Set("auth", c.cfg.RoomPassword)
	}
	serverURL.RawQuery = query.Encode()
	return serverURL.String(), nil
}

func (c *Client) sendHello() error {
	meta := map[string]interface{}{
		"os": "desktop-go",
	}
	return c.writeJSON(outgoingEnvelope{
		Event: "hello",
		Data: helloPayload{
			Room:       c.cfg.Room,
			DeviceID:   c.cfg.DeviceID,
			Name:       c.cfg.DeviceName,
			Platform:   "desktop",
			ClientType: "go",
			Meta:       meta,
		},
	})
}

func (c *Client) readLoop(ctx context.Context, errCh chan<- error) {
	for {
		select {
		case <-ctx.Done():
			return
		default:
		}
		_, data, err := c.connOrNil().ReadMessage()
		if err != nil {
			errCh <- err
			return
		}
		if err := c.handleMessage(data); err != nil {
			c.logger.Printf("处理服务端消息失败: %v", err)
			c.events.OnError(err)
		}
	}
}

func (c *Client) clipboardLoop(ctx context.Context, errCh chan<- error) {
	ticker := time.NewTicker(c.cfg.PollInterval)
	defer ticker.Stop()

	c.lastLocalText = c.readClipboardText()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			if !c.isTrusted() {
				continue
			}
			current := c.readClipboardText()
			if current == "" {
				continue
			}
			if current == c.lastRemoteText || current == c.lastPublishedText {
				c.lastLocalText = current
				continue
			}
			if current == c.lastLocalText {
				continue
			}
			c.lastLocalText = current
			if err := c.publishClipboard(current); err != nil {
				errCh <- err
				return
			}
		}
	}
}

func (c *Client) handleMessage(data []byte) error {
	var env envelope
	if err := json.Unmarshal(data, &env); err != nil {
		return err
	}
	switch env.Event {
	case "helloAck":
		var payload helloAckPayload
		if err := json.Unmarshal(env.Data, &payload); err != nil {
			return err
		}
		c.setTrusted(payload.Device.Trusted)
		c.events.OnTrustedChanged(payload.Device.Trusted)
		if payload.Device.Trusted {
			c.logger.Printf("设备已连接并获批")
		} else {
			c.logger.Printf("设备已连接，等待网页端批准")
		}
	case "clipboardSync":
		var payload clipboardSyncPayload
		if err := json.Unmarshal(env.Data, &payload); err != nil {
			return err
		}
		payload.Text = strings.TrimSpace(payload.Text)
		if payload.Text == "" {
			return nil
		}
		c.lastRemoteText = payload.Text
		c.lastLocalText = payload.Text
		clipboard.Write(clipboard.FmtText, []byte(payload.Text))
		c.events.OnRemoteText(payload.Text)
		c.logger.Printf("已接收远端文本")
	case "deviceState":
		var payload deviceStatePayload
		if err := json.Unmarshal(env.Data, &payload); err != nil {
			return err
		}
		if payload.DeviceID == c.cfg.DeviceID && payload.Type == "trusted" {
			c.setTrusted(payload.Trusted)
			c.events.OnTrustedChanged(payload.Trusted)
			if payload.Trusted {
				c.logger.Printf("当前设备已获批准")
			} else {
				c.logger.Printf("当前设备已取消信任")
			}
		}
	case "clipboardAck":
		c.logger.Printf("文本已提交到同步服务")
	case "payloadNotice":
		var payload payloadNoticePayload
		if err := json.Unmarshal(env.Data, &payload); err != nil {
			return err
		}
		c.events.OnPayloadNotice(payload.Kind, payload.Title)
		c.logger.Printf("收到远端 payload 通知")
	case "forbidden":
		c.logger.Printf("同步认证失败，请检查房间密码")
		c.events.OnError(errors.New("同步认证失败，请检查房间密码"))
	default:
	}
	return nil
}

func (c *Client) publishClipboard(text string) error {
	text = strings.TrimSpace(text)
	if text == "" {
		return nil
	}
	c.lastPublishedText = text
	payload := outgoingEnvelope{
		Event: "clipboardPublish",
		Data: map[string]interface{}{
			"messageId": uuid.NewString(),
			"text":      text,
			"createdAt": time.Now().UnixMilli(),
		},
	}
	return c.writeJSON(payload)
}

func (c *Client) writeJSON(v interface{}) error {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.conn == nil {
		return errors.New("WebSocket 未连接")
	}
	return c.conn.WriteJSON(v)
}

func (c *Client) setConnState(conn *websocket.Conn, connected bool, trusted bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.conn = conn
	c.connected = connected
	c.trusted = trusted
}

func (c *Client) setTrusted(trusted bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.trusted = trusted
}

func (c *Client) isTrusted() bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.connected && c.trusted
}

func (c *Client) connOrNil() *websocket.Conn {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.conn
}

func (c *Client) readClipboardText() string {
	data := clipboard.Read(clipboard.FmtText)
	return strings.TrimSpace(string(bytes.TrimSpace(data)))
}

func readResponsePreview(resp *http.Response) ([]byte, error) {
	reader := io.Reader(resp.Body)
	if strings.EqualFold(strings.TrimSpace(resp.Header.Get("Content-Encoding")), "gzip") {
		gzReader, err := gzip.NewReader(resp.Body)
		if err != nil {
			return nil, err
		}
		defer gzReader.Close()
		reader = gzReader
	}
	return io.ReadAll(io.LimitReader(reader, 2048))
}
