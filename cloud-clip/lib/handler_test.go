package lib

import (
	"net/http/httptest"
	"strings"
	"testing"
)

func TestBuildFileURLEncodesPathSegments(t *testing.T) {
	server := testServerWithPrefix("/clip")

	request := httptest.NewRequest("GET", "http://example.test/clip/file/source", nil)
	got := server.buildFileURL(request, "uuid 123", "截图 1&2.png")
	want := "http://example.test/clip/file/uuid%20123/%E6%88%AA%E5%9B%BE%201&2.png"

	if got != want {
		t.Fatalf("buildFileURL() = %q, want %q", got, want)
	}
	if strings.Contains(got, "\\") {
		t.Fatalf("buildFileURL() contains Windows path separator: %q", got)
	}
}

func TestBuildFileURLOmitsEmptyFilename(t *testing.T) {
	server := testServerWithPrefix("")
	request := httptest.NewRequest("GET", "https://example.test/file/source", nil)

	got := server.buildFileURL(request, "uuid", "")
	want := "https://example.test/file/uuid"
	if got != want {
		t.Fatalf("buildFileURL() = %q, want %q", got, want)
	}
}

func TestBuildContentDispositionSupportsChineseFilename(t *testing.T) {
	got := buildContentDisposition("attachment", "截图 1.png")
	want := "attachment; filename=\"__ 1.png\"; filename*=UTF-8''%E6%88%AA%E5%9B%BE%201.png"
	if got != want {
		t.Fatalf("buildContentDisposition() = %q, want %q", got, want)
	}
}

func TestBuildContentDispositionSanitizesFallbackFilename(t *testing.T) {
	got := buildContentDisposition("", "bad\r\nname\".txt")
	want := "inline; filename=\"bad__name_.txt\"; filename*=UTF-8''bad%0D%0Aname%22.txt"
	if got != want {
		t.Fatalf("buildContentDisposition() = %q, want %q", got, want)
	}
}

func testServerWithPrefix(prefix string) *ClipboardServer {
	config := &Config{}
	config.Server.Prefix = prefix
	return &ClipboardServer{config: config}
}
