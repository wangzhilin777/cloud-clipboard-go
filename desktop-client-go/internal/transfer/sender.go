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
	"golang.design/x/clipboard"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
)

const defaultChunkSize = 1 << 20

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

type TextSendResult struct {
	ID   string `json:"id"`
	Type string `json:"type"`
	URL  string `json:"url"`
	Text string `json:"text"`
}

type LatestTextResult struct {
	Text string `json:"text"`
}

type LatestFileResult struct {
	Name string `json:"name"`
	Path string `json:"path"`
	Size int64  `json:"size"`
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
	if stat.Size() > defaultChunkSize {
		return s.sendSingleFileChunked(ctx, path, stat)
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

func (s *Sender) sendSingleFileChunked(ctx context.Context, path string, stat os.FileInfo) (UploadResult, error) {
	fileName := filepath.Base(path)
	initURL := s.cfg.ServerBase + "/upload/chunk"
	if strings.TrimSpace(s.cfg.Room) != "" {
		initURL += "?room=" + url.QueryEscape(s.cfg.Room)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, initURL, strings.NewReader(fileName))
	if err != nil {
		return UploadResult{}, err
	}
	req.Header.Set("Content-Type", "text/plain")
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
		return UploadResult{}, fmt.Errorf("初始化分块上传失败: HTTP %d %s", resp.StatusCode, strings.TrimSpace(string(raw)))
	}
	var initPayload struct {
		Result struct {
			UUID string `json:"uuid"`
		} `json:"result"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&initPayload); err != nil {
		return UploadResult{}, err
	}
	if strings.TrimSpace(initPayload.Result.UUID) == "" {
		return UploadResult{}, fmt.Errorf("初始化分块上传失败: 未返回 uuid")
	}

	file, err := os.Open(path)
	if err != nil {
		return UploadResult{}, err
	}
	defer file.Close()
	buffer := make([]byte, defaultChunkSize)
	chunkURL := s.cfg.ServerBase + "/upload/chunk/" + initPayload.Result.UUID
	for {
		n, readErr := file.Read(buffer)
		if n > 0 {
			chunkReq, err := http.NewRequestWithContext(ctx, http.MethodPost, chunkURL, bytes.NewReader(buffer[:n]))
			if err != nil {
				return UploadResult{}, err
			}
			chunkReq.Header.Set("Content-Type", "application/octet-stream")
			if strings.TrimSpace(s.cfg.RoomPassword) != "" {
				chunkReq.Header.Set("Authorization", "Bearer "+s.cfg.RoomPassword)
			}
			chunkResp, err := s.httpClient.Do(chunkReq)
			if err != nil {
				return UploadResult{}, err
			}
			chunkResp.Body.Close()
			if chunkResp.StatusCode != http.StatusOK {
				return UploadResult{}, fmt.Errorf("上传文件分块失败: HTTP %d", chunkResp.StatusCode)
			}
		}
		if readErr == io.EOF {
			break
		}
		if readErr != nil {
			return UploadResult{}, readErr
		}
	}

	finishURL := s.cfg.ServerBase + "/upload/finish/" + initPayload.Result.UUID
	if strings.TrimSpace(s.cfg.Room) != "" {
		finishURL += "?room=" + url.QueryEscape(s.cfg.Room)
	}
	finishReq, err := http.NewRequestWithContext(ctx, http.MethodPost, finishURL, nil)
	if err != nil {
		return UploadResult{}, err
	}
	if strings.TrimSpace(s.cfg.RoomPassword) != "" {
		finishReq.Header.Set("Authorization", "Bearer "+s.cfg.RoomPassword)
	}
	finishResp, err := s.httpClient.Do(finishReq)
	if err != nil {
		return UploadResult{}, err
	}
	defer finishResp.Body.Close()
	if finishResp.StatusCode != http.StatusOK {
		raw, _ := io.ReadAll(io.LimitReader(finishResp.Body, 2048))
		return UploadResult{}, fmt.Errorf("完成分块上传失败: HTTP %d %s", finishResp.StatusCode, strings.TrimSpace(string(raw)))
	}
	var payload struct {
		ID   string `json:"id"`
		Type string `json:"type"`
		URL  string `json:"url"`
	}
	if err := json.NewDecoder(finishResp.Body).Decode(&payload); err != nil {
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
		Name:        fileName,
		Size:        stat.Size(),
		ActionURL:   payload.URL,
		DownloadURL: payload.URL,
		Mime:        mimeType,
	}
	if err := s.broadcastPayloadNotice(ctx, result); err != nil {
		return UploadResult{}, err
	}
	s.logger.Printf("大文件发送成功: %s", result.Name)
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

func (s *Sender) SendText(ctx context.Context, text string) (TextSendResult, error) {
	text = strings.TrimSpace(text)
	if text == "" {
		return TextSendResult{}, fmt.Errorf("要发送的文本不能为空")
	}
	sendURL := s.cfg.ServerBase + "/text"
	if strings.TrimSpace(s.cfg.Room) != "" {
		sendURL += "?room=" + url.QueryEscape(s.cfg.Room)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, sendURL, strings.NewReader(text))
	if err != nil {
		return TextSendResult{}, err
	}
	req.Header.Set("Content-Type", "text/plain")
	if strings.TrimSpace(s.cfg.RoomPassword) != "" {
		req.Header.Set("Authorization", "Bearer "+s.cfg.RoomPassword)
	}
	resp, err := s.httpClient.Do(req)
	if err != nil {
		return TextSendResult{}, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		raw, _ := io.ReadAll(io.LimitReader(resp.Body, 2048))
		return TextSendResult{}, fmt.Errorf("发送文本失败: HTTP %d %s", resp.StatusCode, strings.TrimSpace(string(raw)))
	}
	var payload struct {
		ID   string `json:"id"`
		Type string `json:"type"`
		URL  string `json:"url"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		return TextSendResult{}, err
	}
	s.logger.Printf("文本发送成功: %s", payload.ID)
	return TextSendResult{
		ID:   payload.ID,
		Type: payload.Type,
		URL:  payload.URL,
		Text: text,
	}, nil
}

func (s *Sender) FetchLatestTextToClipboard(ctx context.Context) (LatestTextResult, error) {
	latestURL := s.cfg.ServerBase + "/content/latest?json=true"
	if strings.TrimSpace(s.cfg.Room) != "" {
		latestURL += "&room=" + url.QueryEscape(s.cfg.Room)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, latestURL, nil)
	if err != nil {
		return LatestTextResult{}, err
	}
	if strings.TrimSpace(s.cfg.RoomPassword) != "" {
		req.Header.Set("Authorization", "Bearer "+s.cfg.RoomPassword)
	}
	resp, err := s.httpClient.Do(req)
	if err != nil {
		return LatestTextResult{}, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		raw, _ := io.ReadAll(io.LimitReader(resp.Body, 2048))
		return LatestTextResult{}, fmt.Errorf("获取最新文本失败: HTTP %d %s", resp.StatusCode, strings.TrimSpace(string(raw)))
	}
	var payload struct {
		Type    string `json:"type"`
		Content string `json:"content"`
		Name    string `json:"name"`
		URL     string `json:"url"`
		UUID    string `json:"uuid"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		return LatestTextResult{}, err
	}
	if strings.TrimSpace(payload.Content) == "" {
		return LatestTextResult{}, fmt.Errorf("最新内容不是文本")
	}
	if err := clipboard.Init(); err != nil {
		return LatestTextResult{}, err
	}
	text := strings.TrimSpace(payload.Content)
	clipboard.Write(clipboard.FmtText, []byte(text))
	s.logger.Printf("已拉取最新文本到剪贴板")
	return LatestTextResult{Text: text}, nil
}

func (s *Sender) DownloadLatestFile(ctx context.Context) (LatestFileResult, error) {
	latestURL := s.cfg.ServerBase + "/content/latest?json=true"
	if strings.TrimSpace(s.cfg.Room) != "" {
		latestURL += "&room=" + url.QueryEscape(s.cfg.Room)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, latestURL, nil)
	if err != nil {
		return LatestFileResult{}, err
	}
	if strings.TrimSpace(s.cfg.RoomPassword) != "" {
		req.Header.Set("Authorization", "Bearer "+s.cfg.RoomPassword)
	}
	resp, err := s.httpClient.Do(req)
	if err != nil {
		return LatestFileResult{}, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		raw, _ := io.ReadAll(io.LimitReader(resp.Body, 2048))
		return LatestFileResult{}, fmt.Errorf("获取最新文件信息失败: HTTP %d %s", resp.StatusCode, strings.TrimSpace(string(raw)))
	}
	var payload struct {
		Type    string `json:"type"`
		Name    string `json:"name"`
		URL     string `json:"url"`
		UUID    string `json:"uuid"`
		Size    int64  `json:"size"`
		Content string `json:"content"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&payload); err != nil {
		return LatestFileResult{}, err
	}
	if strings.TrimSpace(payload.Content) != "" {
		return LatestFileResult{}, fmt.Errorf("最新内容不是文件")
	}
	if strings.TrimSpace(payload.Name) == "" {
		return LatestFileResult{}, fmt.Errorf("最新文件信息不完整")
	}
	fileURL, err := resolveLatestFileURL(s.cfg.ServerBase, payload.UUID, payload.Name, payload.URL)
	if err != nil {
		return LatestFileResult{}, err
	}
	fileReq, err := http.NewRequestWithContext(ctx, http.MethodGet, fileURL, nil)
	if err != nil {
		return LatestFileResult{}, err
	}
	if strings.TrimSpace(s.cfg.RoomPassword) != "" {
		fileReq.Header.Set("Authorization", "Bearer "+s.cfg.RoomPassword)
	}
	fileResp, err := s.httpClient.Do(fileReq)
	if err != nil {
		return LatestFileResult{}, err
	}
	defer fileResp.Body.Close()
	if fileResp.StatusCode != http.StatusOK {
		raw, _ := io.ReadAll(io.LimitReader(fileResp.Body, 2048))
		return LatestFileResult{}, fmt.Errorf("下载最新文件失败: HTTP %d %s", fileResp.StatusCode, strings.TrimSpace(string(raw)))
	}
	if err := os.MkdirAll(s.cfg.DownloadDir, 0o755); err != nil {
		return LatestFileResult{}, err
	}
	targetPath := uniqueFilePath(s.cfg.DownloadDir, payload.Name)
	dst, err := os.Create(targetPath)
	if err != nil {
		return LatestFileResult{}, err
	}
	defer dst.Close()
	written, err := io.Copy(dst, fileResp.Body)
	if err != nil {
		return LatestFileResult{}, err
	}
	s.logger.Printf("已下载最新文件到: %s", targetPath)
	return LatestFileResult{
		Name: payload.Name,
		Path: targetPath,
		Size: written,
	}, nil
}

func uniqueFilePath(dir string, baseName string) string {
	target := filepath.Join(dir, baseName)
	if _, err := os.Stat(target); os.IsNotExist(err) {
		return target
	}
	ext := filepath.Ext(baseName)
	name := strings.TrimSuffix(baseName, ext)
	for i := 1; ; i++ {
		candidate := filepath.Join(dir, fmt.Sprintf("%s (%d)%s", name, i, ext))
		if _, err := os.Stat(candidate); os.IsNotExist(err) {
			return candidate
		}
	}
}

func resolveLatestFileURL(serverBase string, uuid string, name string, rawURL string) (string, error) {
	serverBase = strings.TrimRight(strings.TrimSpace(serverBase), "/")
	uuid = strings.TrimSpace(uuid)
	name = strings.TrimSpace(name)
	rawURL = strings.TrimSpace(rawURL)
	if uuid != "" && name != "" && serverBase != "" {
		return fmt.Sprintf("%s/file/%s/%s?download=true", serverBase, url.PathEscape(uuid), url.PathEscape(name)), nil
	}
	if rawURL == "" {
		return "", fmt.Errorf("最新文件信息不完整")
	}
	rawURL = normalizeLegacyLatestURL(rawURL)
	if strings.HasPrefix(rawURL, "http://") || strings.HasPrefix(rawURL, "https://") {
		return appendDownloadQuery(rawURL)
	}
	rawURL = strings.ReplaceAll(rawURL, "\\", "/")
	rawURL = strings.TrimPrefix(rawURL, "./")
	rawURL = strings.TrimPrefix(rawURL, ".")
	if serverBase == "" {
		return appendDownloadQuery(rawURL)
	}
	if !strings.HasPrefix(rawURL, "/") {
		rawURL = "/" + rawURL
	}
	return appendDownloadQuery(serverBase + rawURL)
}

func normalizeLegacyLatestURL(rawURL string) string {
	rawURL = strings.TrimSpace(rawURL)
	replacer := strings.NewReplacer("\\", "/", ".//", "", "./", "", ".\\", "")
	rawURL = replacer.Replace(rawURL)
	if strings.HasPrefix(rawURL, "http:/") && !strings.HasPrefix(rawURL, "http://") {
		rawURL = "http://" + strings.TrimPrefix(rawURL, "http:/")
	}
	if strings.HasPrefix(rawURL, "https:/") && !strings.HasPrefix(rawURL, "https://") {
		rawURL = "https://" + strings.TrimPrefix(rawURL, "https:/")
	}
	return rawURL
}

func appendDownloadQuery(raw string) (string, error) {
	parsed, err := url.Parse(raw)
	if err != nil {
		return "", err
	}
	query := parsed.Query()
	query.Set("download", "true")
	parsed.RawQuery = query.Encode()
	return parsed.String(), nil
}
