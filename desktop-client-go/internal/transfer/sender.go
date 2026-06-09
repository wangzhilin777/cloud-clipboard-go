package transfer

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"mime"
	"mime/multipart"
	"net"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
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

type httpStatusError struct {
	Action     string
	StatusCode int
	Body       string
}

func (e httpStatusError) Error() string {
	detail := strings.TrimSpace(e.Body)
	if detail == "" {
		return fmt.Sprintf("%s: HTTP %d", e.Action, e.StatusCode)
	}
	return fmt.Sprintf("%s: HTTP %d %s", e.Action, e.StatusCode, detail)
}

type uploadResponsePayload struct {
	ID          string `json:"id"`
	Type        string `json:"type"`
	URL         string `json:"url"`
	Name        string `json:"name"`
	Size        int64  `json:"size"`
	ActionURL   string `json:"actionUrl"`
	DownloadURL string `json:"downloadUrl"`
	Result      struct {
		ID          string `json:"id"`
		Type        string `json:"type"`
		URL         string `json:"url"`
		Name        string `json:"name"`
		Size        int64  `json:"size"`
		ActionURL   string `json:"actionUrl"`
		DownloadURL string `json:"downloadUrl"`
	} `json:"result"`
}

type latestContentPayload struct {
	Type    string `json:"type"`
	Content string `json:"content"`
	Name    string `json:"name"`
	URL     string `json:"url"`
	UUID    string `json:"uuid"`
	Size    int64  `json:"size"`
}

type workerMultipartPart struct {
	PartNumber int    `json:"partNumber"`
	ETag       string `json:"etag"`
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

	bodyBytes := body.Bytes()
	contentType := writer.FormDataContentType()
	raw, err := s.doFirstSuccessful(ctx, "上传文件失败", s.endpointCandidates("upload", "api/upload"), func(endpoint string) (*http.Request, error) {
		req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, bytes.NewReader(bodyBytes))
		if err != nil {
			return nil, err
		}
		req.Header.Set("Content-Type", contentType)
		s.applyAuth(req)
		return req, nil
	})
	if err != nil {
		return UploadResult{}, err
	}

	var payload uploadResponsePayload
	if err := json.Unmarshal(raw, &payload); err != nil {
		return UploadResult{}, err
	}

	mimeType := mime.TypeByExtension(strings.ToLower(filepath.Ext(path)))
	if mimeType == "" {
		mimeType = "application/octet-stream"
	}
	result := normalizeUploadResult(payload, filepath.Base(path), stat.Size(), mimeType)
	if result.URL == "" {
		return UploadResult{}, fmt.Errorf("上传文件失败: 未返回内容地址")
	}
	result = UploadResult{
		ID:          result.ID,
		Type:        result.Type,
		URL:         result.URL,
		Name:        filepath.Base(path),
		Size:        stat.Size(),
		ActionURL:   firstNonEmpty(result.ActionURL, result.URL),
		DownloadURL: firstNonEmpty(result.DownloadURL, result.ActionURL, result.URL),
		Mime:        mimeType,
	}
	if err := s.broadcastPayloadNotice(ctx, result); err != nil {
		return UploadResult{}, err
	}
	s.logger.Printf("文件发送成功: %s", result.Name)
	return result, nil
}

func (s *Sender) sendSingleFileChunked(ctx context.Context, path string, stat os.FileInfo) (UploadResult, error) {
	mimeType := mime.TypeByExtension(strings.ToLower(filepath.Ext(path)))
	if mimeType == "" {
		mimeType = "application/octet-stream"
	}

	result, err := s.sendSingleFileGoChunked(ctx, path, stat, mimeType)
	if err != nil {
		var statusErr httpStatusError
		if !errors.As(err, &statusErr) || !canFallbackEndpointStatus(statusErr.StatusCode) {
			return UploadResult{}, err
		}
		result, err = s.sendSingleFileWorkerMultipart(ctx, path, stat, mimeType)
		if err != nil {
			return UploadResult{}, err
		}
	}
	if err := s.broadcastPayloadNotice(ctx, result); err != nil {
		return UploadResult{}, err
	}
	s.logger.Printf("大文件发送成功: %s", result.Name)
	return result, nil
}

func (s *Sender) sendSingleFileGoChunked(ctx context.Context, path string, stat os.FileInfo, mimeType string) (UploadResult, error) {
	fileName := filepath.Base(path)
	raw, err := s.doRequest(ctx, "初始化分块上传失败", func() (*http.Request, error) {
		req, err := http.NewRequestWithContext(ctx, http.MethodPost, s.endpointURL("upload/chunk"), strings.NewReader(fileName))
		if err != nil {
			return nil, err
		}
		req.Header.Set("Content-Type", "text/plain")
		s.applyAuth(req)
		return req, nil
	})
	if err != nil {
		return UploadResult{}, err
	}
	var initPayload struct {
		Result struct {
			UUID string `json:"uuid"`
		} `json:"result"`
	}
	if err := json.Unmarshal(raw, &initPayload); err != nil {
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
	chunkURL := s.endpointURL("upload/chunk/" + initPayload.Result.UUID)
	for {
		n, readErr := file.Read(buffer)
		if n > 0 {
			if _, err := s.doRequest(ctx, "上传文件分块失败", func() (*http.Request, error) {
				chunkReq, err := http.NewRequestWithContext(ctx, http.MethodPost, chunkURL, bytes.NewReader(buffer[:n]))
				if err != nil {
					return nil, err
				}
				chunkReq.Header.Set("Content-Type", "application/octet-stream")
				s.applyAuth(chunkReq)
				return chunkReq, nil
			}); err != nil {
				return UploadResult{}, err
			}
		}
		if readErr == io.EOF {
			break
		}
		if readErr != nil {
			return UploadResult{}, readErr
		}
	}

	raw, err = s.doRequest(ctx, "完成分块上传失败", func() (*http.Request, error) {
		finishReq, err := http.NewRequestWithContext(ctx, http.MethodPost, s.endpointURL("upload/finish/"+initPayload.Result.UUID), nil)
		if err != nil {
			return nil, err
		}
		s.applyAuth(finishReq)
		return finishReq, nil
	})
	if err != nil {
		return UploadResult{}, err
	}
	var payload uploadResponsePayload
	if err := json.Unmarshal(raw, &payload); err != nil {
		return UploadResult{}, err
	}
	result := normalizeUploadResult(payload, fileName, stat.Size(), mimeType)
	if result.URL == "" {
		return UploadResult{}, fmt.Errorf("完成分块上传失败: 未返回内容地址")
	}
	return result, nil
}

func (s *Sender) sendSingleFileWorkerMultipart(ctx context.Context, path string, stat os.FileInfo, mimeType string) (UploadResult, error) {
	fileName := filepath.Base(path)
	initBody, err := json.Marshal(map[string]interface{}{
		"name": fileName,
		"size": stat.Size(),
		"type": mimeType,
	})
	if err != nil {
		return UploadResult{}, err
	}
	raw, err := s.doRequest(ctx, "初始化 Worker 分片上传失败", func() (*http.Request, error) {
		req, err := http.NewRequestWithContext(ctx, http.MethodPost, s.endpointURL("api/upload/multipart/create"), bytes.NewReader(initBody))
		if err != nil {
			return nil, err
		}
		req.Header.Set("Content-Type", "application/json")
		s.applyAuth(req)
		return req, nil
	})
	if err != nil {
		return UploadResult{}, err
	}
	var initPayload struct {
		Result struct {
			UUID     string `json:"uuid"`
			Key      string `json:"key"`
			UploadID string `json:"uploadId"`
			PartSize int64  `json:"partSize"`
		} `json:"result"`
	}
	if err := json.Unmarshal(raw, &initPayload); err != nil {
		return UploadResult{}, err
	}
	partSize := initPayload.Result.PartSize
	if partSize <= 0 {
		partSize = 8 * defaultChunkSize
	}
	if strings.TrimSpace(initPayload.Result.Key) == "" || strings.TrimSpace(initPayload.Result.UploadID) == "" {
		return UploadResult{}, fmt.Errorf("初始化 Worker 分片上传失败: 返回参数不完整")
	}

	file, err := os.Open(path)
	if err != nil {
		return UploadResult{}, err
	}
	defer file.Close()

	buffer := make([]byte, int(partSize))
	parts := make([]workerMultipartPart, 0)
	for partNumber := 1; ; partNumber++ {
		n, readErr := io.ReadFull(file, buffer)
		if readErr == io.EOF {
			break
		}
		if readErr == io.ErrUnexpectedEOF {
			readErr = io.EOF
		}
		if n > 0 {
			partURL := s.endpointURL("api/upload/multipart/" + strconv.Itoa(partNumber))
			partURL = addQueryValues(partURL, url.Values{
				"key":      []string{initPayload.Result.Key},
				"uploadId": []string{initPayload.Result.UploadID},
			})
			raw, err := s.doRequest(ctx, "上传 Worker 文件分片失败", func() (*http.Request, error) {
				req, err := http.NewRequestWithContext(ctx, http.MethodPut, partURL, bytes.NewReader(buffer[:n]))
				if err != nil {
					return nil, err
				}
				req.Header.Set("Content-Type", "application/octet-stream")
				s.applyAuth(req)
				return req, nil
			})
			if err != nil {
				return UploadResult{}, err
			}
			var partPayload struct {
				Result workerMultipartPart `json:"result"`
			}
			if err := json.Unmarshal(raw, &partPayload); err != nil {
				return UploadResult{}, err
			}
			if partPayload.Result.PartNumber == 0 {
				partPayload.Result.PartNumber = partNumber
			}
			parts = append(parts, partPayload.Result)
		}
		if readErr == io.EOF {
			break
		}
		if readErr != nil {
			return UploadResult{}, readErr
		}
	}

	completeBody, err := json.Marshal(map[string]interface{}{
		"uploadId": initPayload.Result.UploadID,
		"key":      initPayload.Result.Key,
		"parts":    parts,
		"name":     fileName,
		"size":     stat.Size(),
	})
	if err != nil {
		return UploadResult{}, err
	}
	raw, err = s.doRequest(ctx, "完成 Worker 分片上传失败", func() (*http.Request, error) {
		req, err := http.NewRequestWithContext(ctx, http.MethodPost, s.endpointURL("api/upload/multipart/complete"), bytes.NewReader(completeBody))
		if err != nil {
			return nil, err
		}
		req.Header.Set("Content-Type", "application/json")
		s.applyAuth(req)
		return req, nil
	})
	if err != nil {
		return UploadResult{}, err
	}
	var payload uploadResponsePayload
	if err := json.Unmarshal(raw, &payload); err != nil {
		return UploadResult{}, err
	}
	result := normalizeUploadResult(payload, fileName, stat.Size(), mimeType)
	if result.URL == "" {
		return UploadResult{}, fmt.Errorf("完成 Worker 分片上传失败: 未返回内容地址")
	}
	return result, nil
}

func (s *Sender) broadcastPayloadNotice(ctx context.Context, result UploadResult) error {
	actionURL := rewriteLoopbackURLForLAN(s.cfg.ServerBase, result.ActionURL)
	downloadURL := rewriteLoopbackURLForLAN(s.cfg.ServerBase, result.DownloadURL)
	body := map[string]interface{}{
		"payloadId":      uuid.NewString(),
		"sourceDeviceId": s.cfg.DeviceID,
		"room":           s.cfg.Room,
		"kind":           normalizeKind(result),
		"title":          result.Name,
		"mime":           result.Mime,
		"size":           result.Size,
		"actionUrl":      actionURL,
		"downloadUrl":    downloadURL,
		"createdAt":      time.Now().UnixMilli(),
	}
	raw, err := json.Marshal(body)
	if err != nil {
		return err
	}
	_, err = s.doFirstSuccessful(ctx, "发送 payload 通知失败", s.endpointCandidates("api/sync/payload-notice", "api/sync/payload/notice"), func(endpoint string) (*http.Request, error) {
		req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, bytes.NewReader(raw))
		if err != nil {
			return nil, err
		}
		req.Header.Set("Content-Type", "application/json")
		s.applyAuth(req)
		return req, nil
	})
	if err != nil {
		return err
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
	raw, err := s.doFirstSuccessful(ctx, "发送文本失败", s.endpointCandidates("text", "api/text"), func(endpoint string) (*http.Request, error) {
		req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, strings.NewReader(text))
		if err != nil {
			return nil, err
		}
		req.Header.Set("Content-Type", "text/plain")
		s.applyAuth(req)
		return req, nil
	})
	if err != nil {
		return TextSendResult{}, err
	}
	var payload struct {
		ID   string `json:"id"`
		Type string `json:"type"`
		URL  string `json:"url"`
	}
	if err := json.Unmarshal(raw, &payload); err != nil {
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
	payload, err := s.fetchLatestContent(ctx, "获取最新文本失败")
	if err != nil {
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
	payload, err := s.fetchLatestContent(ctx, "获取最新文件信息失败")
	if err != nil {
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

func (s *Sender) applyAuth(req *http.Request) {
	if strings.TrimSpace(s.cfg.RoomPassword) != "" {
		req.Header.Set("Authorization", "Bearer "+s.cfg.RoomPassword)
	}
}

func (s *Sender) endpointCandidates(paths ...string) []string {
	seen := map[string]bool{}
	candidates := make([]string, 0, len(paths))
	for _, path := range paths {
		endpoint := s.endpointURL(path)
		if !seen[endpoint] {
			seen[endpoint] = true
			candidates = append(candidates, endpoint)
		}
	}
	return candidates
}

func (s *Sender) endpointURL(path string) string {
	base := strings.TrimRight(strings.TrimSpace(s.cfg.ServerBase), "/")
	path = normalizeEndpointPath(base, path)
	endpoint := base + "/" + path
	if strings.TrimSpace(s.cfg.Room) != "" {
		endpoint = addQueryValues(endpoint, url.Values{"room": []string{s.cfg.Room}})
	}
	return endpoint
}

func normalizeEndpointPath(base string, path string) string {
	path = strings.TrimLeft(strings.TrimSpace(path), "/")
	if strings.EqualFold(lastURLSegment(base), "api") && strings.HasPrefix(path, "api/") {
		return strings.TrimPrefix(path, "api/")
	}
	return path
}

func lastURLSegment(raw string) string {
	parsed, err := url.Parse(raw)
	if err == nil && parsed.Path != "" {
		parts := strings.Split(strings.Trim(strings.TrimRight(parsed.Path, "/"), "/"), "/")
		return parts[len(parts)-1]
	}
	parts := strings.Split(strings.Trim(strings.TrimRight(raw, "/"), "/"), "/")
	return parts[len(parts)-1]
}

func addQueryValues(raw string, values url.Values) string {
	parsed, err := url.Parse(raw)
	if err != nil {
		return raw
	}
	query := parsed.Query()
	for key, list := range values {
		for _, value := range list {
			query.Set(key, value)
		}
	}
	parsed.RawQuery = query.Encode()
	return parsed.String()
}

func (s *Sender) doFirstSuccessful(ctx context.Context, action string, endpoints []string, requestFactory func(string) (*http.Request, error)) ([]byte, error) {
	var lastErr error
	for index, endpoint := range endpoints {
		raw, err := s.doRequest(ctx, action, func() (*http.Request, error) {
			return requestFactory(endpoint)
		})
		if err == nil {
			return raw, nil
		}
		lastErr = err
		var statusErr httpStatusError
		if !errors.As(err, &statusErr) || !canFallbackEndpointStatus(statusErr.StatusCode) || index == len(endpoints)-1 {
			return nil, err
		}
	}
	if lastErr != nil {
		return nil, lastErr
	}
	return nil, fmt.Errorf("%s: 没有可用的服务地址", action)
}

func (s *Sender) doRequest(ctx context.Context, action string, requestFactory func() (*http.Request, error)) ([]byte, error) {
	req, err := requestFactory()
	if err != nil {
		return nil, err
	}
	if req.Context() == nil {
		req = req.WithContext(ctx)
	}
	resp, err := s.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		raw, _ := io.ReadAll(io.LimitReader(resp.Body, 2048))
		return nil, httpStatusError{Action: action, StatusCode: resp.StatusCode, Body: string(raw)}
	}
	return io.ReadAll(resp.Body)
}

func canFallbackEndpointStatus(status int) bool {
	return status == http.StatusNotFound || status == http.StatusMethodNotAllowed
}

func normalizeUploadResult(payload uploadResponsePayload, fallbackName string, fallbackSize int64, fallbackMime string) UploadResult {
	result := UploadResult{
		ID:          firstNonEmpty(payload.ID, payload.Result.ID),
		Type:        firstNonEmpty(payload.Type, payload.Result.Type, kindFromMime(fallbackMime)),
		URL:         firstNonEmpty(payload.URL, payload.ActionURL, payload.DownloadURL, payload.Result.URL, payload.Result.ActionURL, payload.Result.DownloadURL),
		Name:        firstNonEmpty(payload.Name, payload.Result.Name, fallbackName),
		Size:        payload.Size,
		ActionURL:   firstNonEmpty(payload.ActionURL, payload.Result.ActionURL, payload.URL, payload.Result.URL),
		DownloadURL: firstNonEmpty(payload.DownloadURL, payload.Result.DownloadURL, payload.ActionURL, payload.Result.ActionURL, payload.URL, payload.Result.URL),
		Mime:        fallbackMime,
	}
	if result.Size <= 0 {
		result.Size = payload.Result.Size
	}
	if result.Size <= 0 {
		result.Size = fallbackSize
	}
	if result.ActionURL == "" {
		result.ActionURL = result.URL
	}
	if result.DownloadURL == "" {
		result.DownloadURL = result.ActionURL
	}
	return result
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if strings.TrimSpace(value) != "" {
			return strings.TrimSpace(value)
		}
	}
	return ""
}

func kindFromMime(mimeType string) string {
	if strings.HasPrefix(strings.ToLower(strings.TrimSpace(mimeType)), "image/") {
		return "image"
	}
	return "file"
}

func (s *Sender) fetchLatestContent(ctx context.Context, action string) (latestContentPayload, error) {
	raw, err := s.doFirstSuccessful(ctx, action, s.latestContentEndpoints(), func(endpoint string) (*http.Request, error) {
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
		if err != nil {
			return nil, err
		}
		req.Header.Set("Accept", "application/json")
		s.applyAuth(req)
		return req, nil
	})
	if err != nil {
		return latestContentPayload{}, err
	}
	var payload latestContentPayload
	if err := json.Unmarshal(raw, &payload); err != nil {
		return latestContentPayload{}, err
	}
	return payload, nil
}

func (s *Sender) latestContentEndpoints() []string {
	query := url.Values{"json": []string{"true"}}
	if strings.TrimSpace(s.cfg.Room) == "" {
		query.Set("room", "default")
	}

	endpoints := make([]string, 0, 2)
	for _, endpoint := range s.endpointCandidates("content/latest", "api/content/latest") {
		endpoints = append(endpoints, addQueryValues(endpoint, query))
	}
	return endpoints
}

func resolveLatestFileURL(serverBase string, uuid string, name string, rawURL string) (string, error) {
	serverBase = strings.TrimRight(strings.TrimSpace(serverBase), "/")
	uuid = strings.TrimSpace(uuid)
	name = strings.TrimSpace(name)
	rawURL = strings.TrimSpace(rawURL)
	if rawURL != "" {
		normalizedURL := normalizeLegacyLatestURL(rawURL)
		if strings.HasPrefix(normalizedURL, "http://") || strings.HasPrefix(normalizedURL, "https://") {
			return appendDownloadQuery(normalizedURL)
		}
		normalizedURL = strings.ReplaceAll(normalizedURL, "\\", "/")
		normalizedURL = strings.TrimPrefix(normalizedURL, "./")
		normalizedURL = strings.TrimPrefix(normalizedURL, ".")
		if serverBase == "" {
			return appendDownloadQuery(normalizedURL)
		}
		return appendDownloadQuery(joinServerRelativeURL(serverBase, normalizedURL))
	}
	if uuid != "" && name != "" && serverBase != "" {
		return appendDownloadQuery(joinServerRelativeURL(serverBase, "file/"+url.PathEscape(uuid)+"/"+url.PathEscape(name)))
	}
	return "", fmt.Errorf("最新文件信息不完整")
}

func joinServerRelativeURL(serverBase string, target string) string {
	base := strings.TrimRight(strings.TrimSpace(serverBase), "/")
	target = strings.ReplaceAll(strings.TrimSpace(target), "\\", "/")
	target = normalizeEndpointPath(base, target)
	return base + "/" + target
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

func rewriteLoopbackURLForLAN(serverBase string, raw string) string {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return ""
	}
	parsed, err := url.Parse(raw)
	if err != nil {
		return raw
	}
	host := strings.TrimSpace(parsed.Hostname())
	if !isLoopbackHost(host) {
		return raw
	}
	lanHost := preferredLANHost(serverBase)
	if lanHost == "" {
		return raw
	}
	port := parsed.Port()
	if port == "" {
		port = defaultPortForScheme(parsed.Scheme)
	}
	if port != "" {
		parsed.Host = net.JoinHostPort(lanHost, port)
	} else {
		parsed.Host = lanHost
	}
	return parsed.String()
}

func preferredLANHost(serverBase string) string {
	if host := nonLoopbackHostFromURL(serverBase); host != "" {
		return host
	}
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return ""
	}
	for _, addr := range addrs {
		ipNet, ok := addr.(*net.IPNet)
		if !ok || ipNet == nil {
			continue
		}
		ip := ipNet.IP
		if ip == nil || ip.IsLoopback() {
			continue
		}
		ip = ip.To4()
		if ip == nil {
			continue
		}
		if isPrivateIPv4(ip) {
			return ip.String()
		}
	}
	return ""
}

func nonLoopbackHostFromURL(raw string) string {
	parsed, err := url.Parse(strings.TrimSpace(raw))
	if err != nil {
		return ""
	}
	host := strings.TrimSpace(parsed.Hostname())
	if host == "" || isLoopbackHost(host) {
		return ""
	}
	return host
}

func isLoopbackHost(host string) bool {
	if strings.EqualFold(host, "localhost") {
		return true
	}
	ip := net.ParseIP(host)
	return ip != nil && ip.IsLoopback()
}

func defaultPortForScheme(scheme string) string {
	switch strings.ToLower(strings.TrimSpace(scheme)) {
	case "http":
		return "80"
	case "https":
		return "443"
	default:
		return ""
	}
}

func isPrivateIPv4(ip net.IP) bool {
	if ip == nil {
		return false
	}
	if len(ip) != net.IPv4len {
		return false
	}
	switch {
	case ip[0] == 10:
		return true
	case ip[0] == 172 && ip[1] >= 16 && ip[1] <= 31:
		return true
	case ip[0] == 192 && ip[1] == 168:
		return true
	default:
		return false
	}
}
