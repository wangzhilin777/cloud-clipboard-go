package transfer

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"mime"
	"mime/multipart"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/google/uuid"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
)

type Sender struct {
	cfg        config.Config
	logger     *log.Logger
	httpClient *http.Client
}

type UploadResult struct {
	ID          string `json:"id"`
	Type        string `json:"type"`
	URL         string `json:"url"`
	Name        string `json:"name"`
	Size        int64  `json:"size"`
	ActionURL   string `json:"actionUrl"`
	DownloadURL string `json:"downloadUrl"`
	Mime        string `json:"mime"`
}

func NewSender(cfg config.Config, logger *log.Logger) *Sender {
	return &Sender{
		cfg:    cfg,
		logger: logger,
		httpClient: &http.Client{
			Timeout: 60 * time.Second,
		},
	}
}

func (s *Sender) SendFiles(ctx context.Context, paths []string) ([]UploadResult, error) {
	results := make([]UploadResult, 0, len(paths))
	for _, path := range paths {
		path = strings.TrimSpace(path)
		if path == "" {
			continue
		}
		result, err := s.sendSingleFile(ctx, path)
		if err != nil {
			return nil, err
		}
		results = append(results, result)
	}
	return results, nil
}

func (s *Sender) sendSingleFile(ctx context.Context, path string) (UploadResult, error) {
	file, err := os.Open(path)
	if err != nil {
		return UploadResult{}, err
	}
	defer file.Close()

	stat, err := file.Stat()
	if err != nil {
		return UploadResult{}, err
	}

	var body bytes.Buffer
	writer := multipart.NewWriter(&body)
	part, err := writer.CreateFormFile("file", filepath.Base(path))
	if err != nil {
		return UploadResult{}, err
	}
	if _, err := io.Copy(part, file); err != nil {
		return UploadResult{}, err
	}
	if err := writer.Close(); err != nil {
		return UploadResult{}, err
	}

	uploadURL := s.cfg.ServerBase + "/upload"
	if strings.TrimSpace(s.cfg.Room) != "" {
		uploadURL += "?room=" + url.QueryEscape(s.cfg.Room)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, uploadURL, &body)
	if err != nil {
		return UploadResult{}, err
	}
	req.Header.Set("Content-Type", writer.FormDataContentType())
	if strings.TrimSpace(s.cfg.RoomPassword) != "" {
		req.Header.Set("Authorization", "Bearer "+s.cfg.RoomPassword)
	}

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return UploadResult{}, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		raw, _ := io.ReadAll(io.LimitReader(resp.Body, 2048))
		return UploadResult{}, fmt.Errorf("上传文件失败: HTTP %d %s", resp.StatusCode, strings.TrimSpace(string(raw)))
	}

	var payload struct {
		ID   string `json:"id"`
		Type string `json:"type"`
		URL  string `json:"url"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		return UploadResult{}, err
	}

	mimeType := mime.TypeByExtension(strings.ToLower(filepath.Ext(path)))
	if mimeType == "" {
		mimeType = "application/octet-stream"
	}
	result := UploadResult{
		ID:          payload.ID,
		Type:        payload.Type,
		URL:         payload.URL,
		Name:        filepath.Base(path),
		Size:        stat.Size(),
		ActionURL:   payload.URL,
		DownloadURL: payload.URL,
		Mime:        mimeType,
	}
	if err := s.broadcastPayloadNotice(ctx, result); err != nil {
		return UploadResult{}, err
	}
	s.logger.Printf("文件发送成功: %s", result.Name)
	return result, nil
}

func (s *Sender) broadcastPayloadNotice(ctx context.Context, result UploadResult) error {
	noticeURL := s.cfg.ServerBase + "/api/sync/payload-notice"
	body := map[string]interface{}{
		"payloadId":      uuid.NewString(),
		"sourceDeviceId": s.cfg.DeviceID,
		"room":           s.cfg.Room,
		"kind":           normalizeKind(result),
		"title":          result.Name,
		"mime":           result.Mime,
		"size":           result.Size,
		"actionUrl":      result.ActionURL,
		"downloadUrl":    result.DownloadURL,
		"createdAt":      time.Now().UnixMilli(),
	}
	raw, err := json.Marshal(body)
	if err != nil {
		return err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, noticeURL, bytes.NewReader(raw))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	if strings.TrimSpace(s.cfg.RoomPassword) != "" {
		req.Header.Set("Authorization", "Bearer "+s.cfg.RoomPassword)
	}
	resp, err := s.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		raw, _ := io.ReadAll(io.LimitReader(resp.Body, 2048))
		return fmt.Errorf("发送 payload 通知失败: HTTP %d %s", resp.StatusCode, strings.TrimSpace(string(raw)))
	}
	return nil
}

func normalizeKind(result UploadResult) string {
	if strings.EqualFold(result.Type, "image") {
		return "image"
	}
	if strings.HasPrefix(strings.ToLower(result.Mime), "image/") {
		return "image"
	}
	return "file"
}
