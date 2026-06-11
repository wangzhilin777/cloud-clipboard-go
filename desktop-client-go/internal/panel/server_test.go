package panel

import (
	"bytes"
	"encoding/json"
	"io"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"reflect"
	"testing"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
)

type sendFilesBackendStub struct {
	receivedPaths []string
	receivedNames []string
	receivedData  []string
	sendResults   []string
	sendErr       error
}

func (s *sendFilesBackendStub) Status() StatusView                              { return StatusView{Config: config.Config{}} }
func (s *sendFilesBackendStub) UpdateConfig(cfg config.Config) error            { return nil }
func (s *sendFilesBackendStub) RequestReconnect()                               {}
func (s *sendFilesBackendStub) OpenPanel() error                                { return nil }
func (s *sendFilesBackendStub) OpenDownloadDir() error                          { return nil }
func (s *sendFilesBackendStub) ClearDownloadDir() (int, error)                  { return 0, nil }
func (s *sendFilesBackendStub) ConfirmPendingClipboardFiles() ([]string, error) { return nil, nil }
func (s *sendFilesBackendStub) SendText(text string, fromClipboard bool) (string, error) {
	return "", nil
}
func (s *sendFilesBackendStub) FetchLatestText() (string, error)            { return "", nil }
func (s *sendFilesBackendStub) DownloadLatestFile() (string, error)         { return "", nil }
func (s *sendFilesBackendStub) FetchLatestFileToClipboard() (string, error) { return "", nil }
func (s *sendFilesBackendStub) SuppressClipboardFiles(paths []string)       {}

func (s *sendFilesBackendStub) SendFiles(paths []string) ([]string, error) {
	s.receivedPaths = append([]string(nil), paths...)
	s.receivedNames = s.receivedNames[:0]
	s.receivedData = s.receivedData[:0]
	for _, path := range paths {
		s.receivedNames = append(s.receivedNames, filepath.Base(path))
		raw, err := os.ReadFile(path)
		if err != nil {
			continue
		}
		s.receivedData = append(s.receivedData, string(raw))
	}
	if s.sendErr != nil {
		return nil, s.sendErr
	}
	if s.sendResults != nil {
		return append([]string(nil), s.sendResults...), nil
	}
	return append([]string(nil), s.receivedNames...), nil
}

func TestHandleSendFileAcceptsJSONPaths(t *testing.T) {
	backend := &sendFilesBackendStub{sendResults: []string{"alpha.txt"}}
	server := New("127.0.0.1:0", backend)

	body, err := json.Marshal(map[string]any{"paths": []string{`C:\tmp\alpha.txt`}})
	if err != nil {
		t.Fatalf("Marshal() error = %v", err)
	}
	req := httptest.NewRequest(http.MethodPost, "/api/send-file", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()

	server.handleSendFile(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, body = %s", rec.Code, rec.Body.String())
	}
	if !reflect.DeepEqual(backend.receivedPaths, []string{`C:\tmp\alpha.txt`}) {
		t.Fatalf("receivedPaths = %#v", backend.receivedPaths)
	}
}

func TestHandleSendFileAcceptsMultipartFiles(t *testing.T) {
	backend := &sendFilesBackendStub{}
	server := New("127.0.0.1:0", backend)

	var body bytes.Buffer
	writer := multipart.NewWriter(&body)
	fileOne, err := writer.CreateFormFile("files", "alpha.txt")
	if err != nil {
		t.Fatalf("CreateFormFile(alpha) error = %v", err)
	}
	if _, err := io.WriteString(fileOne, "alpha-body"); err != nil {
		t.Fatalf("WriteString(alpha) error = %v", err)
	}
	fileTwo, err := writer.CreateFormFile("files", "alpha.txt")
	if err != nil {
		t.Fatalf("CreateFormFile(beta) error = %v", err)
	}
	if _, err := io.WriteString(fileTwo, "beta-body"); err != nil {
		t.Fatalf("WriteString(beta) error = %v", err)
	}
	if err := writer.Close(); err != nil {
		t.Fatalf("writer.Close() error = %v", err)
	}

	req := httptest.NewRequest(http.MethodPost, "/api/send-file", &body)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	rec := httptest.NewRecorder()

	server.handleSendFile(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, body = %s", rec.Code, rec.Body.String())
	}
	if !reflect.DeepEqual(backend.receivedNames, []string{"alpha.txt", "alpha (1).txt"}) {
		t.Fatalf("receivedNames = %#v", backend.receivedNames)
	}
	if !reflect.DeepEqual(backend.receivedData, []string{"alpha-body", "beta-body"}) {
		t.Fatalf("receivedData = %#v", backend.receivedData)
	}
	for _, path := range backend.receivedPaths {
		if _, err := os.Stat(path); !os.IsNotExist(err) {
			t.Fatalf("temporary upload path still exists: %s", path)
		}
	}
}
