package desktopcmd

import (
	"encoding/json"
	"io"
	"log"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
)

func TestRunShellActionSendFileBroadcastsPayloadNotice(t *testing.T) {
	tempDir := t.TempDir()
	testFile := filepath.Join(tempDir, "sample.png")
	if err := os.WriteFile(testFile, []byte("png-data"), 0o644); err != nil {
		t.Fatal(err)
	}

	var uploadSeen bool
	var payloadNoticeSeen bool
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if got := r.Header.Get("Authorization"); got != "Bearer room-pass" {
			t.Fatalf("Authorization = %q", got)
		}
		if got := r.URL.Query().Get("room"); got != "desk-room" {
			t.Fatalf("room = %q", got)
		}
		switch r.URL.Path {
		case "/upload":
			uploadSeen = true
			if err := r.ParseMultipartForm(1 << 20); err != nil {
				t.Fatalf("ParseMultipartForm: %v", err)
			}
			file, header, err := r.FormFile("file")
			if err != nil {
				t.Fatalf("FormFile: %v", err)
			}
			defer file.Close()
			if header.Filename != "sample.png" {
				t.Fatalf("uploaded filename = %q", header.Filename)
			}
			w.Header().Set("Content-Type", "application/json")
			_, _ = io.WriteString(w, `{"id":"1","type":"image","url":"/content/1","name":"sample.png","size":8}`)
		case "/api/sync/payload-notice":
			payloadNoticeSeen = true
			var body map[string]any
			if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
				t.Fatalf("decode payload notice: %v", err)
			}
			if body["sourceDeviceId"] != "desktop-test-device" {
				t.Fatalf("sourceDeviceId = %v", body["sourceDeviceId"])
			}
			if body["kind"] != "image" {
				t.Fatalf("kind = %v", body["kind"])
			}
			if body["title"] != "sample.png" {
				t.Fatalf("title = %v", body["title"])
			}
			w.Header().Set("Content-Type", "application/json")
			_, _ = io.WriteString(w, `{"payload":{"payloadId":"notice-1"}}`)
		default:
			http.NotFound(w, r)
		}
	}))
	defer server.Close()

	cfg := shellTestConfig(server.URL, tempDir)
	message, err := RunShellAction(log.New(io.Discard, "", 0), cfg, testFile, "", false)
	if err != nil {
		t.Fatalf("RunShellAction send: %v", err)
	}
	if !uploadSeen || !payloadNoticeSeen {
		t.Fatalf("uploadSeen=%v payloadNoticeSeen=%v", uploadSeen, payloadNoticeSeen)
	}
	if !strings.Contains(message, "sample.png") {
		t.Fatalf("message = %q", message)
	}
}

func TestRunShellActionDownloadLatestFileToDirectory(t *testing.T) {
	tempDir := t.TempDir()
	fileBytes := []byte("downloaded-image")
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if got := r.Header.Get("Authorization"); got != "Bearer room-pass" {
			t.Fatalf("Authorization = %q", got)
		}
		switch r.URL.Path {
		case "/content/latest":
			if got := r.URL.Query().Get("room"); got != "desk-room" {
				t.Fatalf("room = %q", got)
			}
			if got := r.URL.Query().Get("json"); got != "true" {
				t.Fatalf("json = %q", got)
			}
			w.Header().Set("Content-Type", "application/json")
			_, _ = io.WriteString(w, `{"type":"image","name":"latest.png","uuid":"file-id","url":"/file/file-id/latest.png","size":16}`)
		case "/file/file-id/latest.png":
			_, _ = w.Write(fileBytes)
		default:
			http.NotFound(w, r)
		}
	}))
	defer server.Close()

	downloadDir := filepath.Join(tempDir, "downloads")
	cfg := shellTestConfig(server.URL, filepath.Join(tempDir, "cache"))
	message, err := RunShellAction(log.New(io.Discard, "", 0), cfg, "", downloadDir, false)
	if err != nil {
		t.Fatalf("RunShellAction download: %v", err)
	}
	if !strings.Contains(message, "latest.png") {
		t.Fatalf("message = %q", message)
	}
	downloaded, err := os.ReadFile(filepath.Join(downloadDir, "latest.png"))
	if err != nil {
		t.Fatalf("read downloaded file: %v", err)
	}
	if string(downloaded) != string(fileBytes) {
		t.Fatalf("downloaded bytes = %q", string(downloaded))
	}
}

func shellTestConfig(serverBase string, downloadDir string) config.Config {
	cfg := config.Default()
	cfg.ServerBase = serverBase
	cfg.Room = "desk-room"
	cfg.RoomPassword = "room-pass"
	cfg.DeviceID = "desktop-test-device"
	cfg.DownloadDir = downloadDir
	cfg.Normalize()
	return cfg
}
