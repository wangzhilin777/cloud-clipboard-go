package transfer

import (
	"net/url"
	"reflect"
	"strings"
	"testing"

	"github.com/jonnyan404/cloud-clipboard-go/desktop-client-go/internal/config"
)

func TestLatestContentEndpointsUseDefaultRoomWhenRoomIsBlank(t *testing.T) {
	sender := NewSender(config.Config{ServerBase: "http://127.0.0.1:9501"}, nil)

	got := sender.latestContentEndpoints()
	want := []string{
		"http://127.0.0.1:9501/content/latest?json=true&room=default",
		"http://127.0.0.1:9501/api/content/latest?json=true&room=default",
	}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("latestContentEndpoints() = %#v, want %#v", got, want)
	}
}

func TestResolveLatestFileURLDeduplicatesRootAPIPath(t *testing.T) {
	got, err := resolveLatestFileURL("https://example.com/api", "", "", "/api/file/u/name.png")
	if err != nil {
		t.Fatalf("resolveLatestFileURL() error = %v", err)
	}
	want := "https://example.com/api/file/u/name.png?download=true"
	if got != want {
		t.Fatalf("resolveLatestFileURL() = %q, want %q", got, want)
	}
	if strings.Contains(got, "/api/api/") {
		t.Fatalf("resolveLatestFileURL() contains duplicated /api path: %q", got)
	}
}

func TestResolveLatestFileURLDeduplicatesRelativeAPIPath(t *testing.T) {
	got, err := resolveLatestFileURL("https://example.com/api", "", "", "api/file/u/name.png")
	if err != nil {
		t.Fatalf("resolveLatestFileURL() error = %v", err)
	}
	want := "https://example.com/api/file/u/name.png?download=true"
	if got != want {
		t.Fatalf("resolveLatestFileURL() = %q, want %q", got, want)
	}
	if strings.Contains(got, "/api/api/") {
		t.Fatalf("resolveLatestFileURL() contains duplicated /api path: %q", got)
	}
}

func TestResolveLatestFileURLPreservesQueryAndChineseName(t *testing.T) {
	got, err := resolveLatestFileURL("https://example.com/api", "", "", "/api/file/u/%E6%B5%8B%E8%AF%95.png?token=abc&download=false")
	if err != nil {
		t.Fatalf("resolveLatestFileURL() error = %v", err)
	}
	want := "https://example.com/api/file/u/%E6%B5%8B%E8%AF%95.png?download=true&token=abc"
	if got != want {
		t.Fatalf("resolveLatestFileURL() = %q, want %q", got, want)
	}
}

func TestResolveLatestFileURLFallbackUUIDNameUsesServerBase(t *testing.T) {
	got, err := resolveLatestFileURL("https://example.com/api", "uuid-1", "测试 文件.png", "")
	if err != nil {
		t.Fatalf("resolveLatestFileURL() error = %v", err)
	}
	want := "https://example.com/api/file/uuid-1/%E6%B5%8B%E8%AF%95%20%E6%96%87%E4%BB%B6.png?download=true"
	if got != want {
		t.Fatalf("resolveLatestFileURL() = %q, want %q", got, want)
	}
}

func TestLatestContentEndpointsPreserveConfiguredRoom(t *testing.T) {
	sender := NewSender(config.Config{ServerBase: "http://127.0.0.1:9501/api", Room: "研发 房"}, nil)

	got := sender.latestContentEndpoints()
	want := []string{
		"http://127.0.0.1:9501/api/content/latest?json=true&room=%E7%A0%94%E5%8F%91+%E6%88%BF",
	}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("latestContentEndpoints() = %#v, want %#v", got, want)
	}
}

func TestRewriteLoopbackURLForLANUsesServerBaseHost(t *testing.T) {
	got := rewriteLoopbackURLForLAN("http://192.168.31.236:9501", "http://127.0.0.1:9501/file/u/name.txt?download=true")
	want := "http://192.168.31.236:9501/file/u/name.txt?download=true"
	if got != want {
		t.Fatalf("rewriteLoopbackURLForLAN() = %q, want %q", got, want)
	}
}

func TestRewriteLoopbackURLForLANLeavesNonLoopbackUntouched(t *testing.T) {
	raw := "http://192.168.31.236:9501/file/u/name.txt?download=true"
	if got := rewriteLoopbackURLForLAN("http://127.0.0.1:9501", raw); got != raw {
		t.Fatalf("rewriteLoopbackURLForLAN() = %q, want %q", got, raw)
	}
}

func TestRewriteLoopbackURLForLANPreservesPathQueryAndPort(t *testing.T) {
	got := rewriteLoopbackURLForLAN("http://192.168.31.236:9501/api", "http://localhost:9501/api/file/u/%E6%B5%8B%E8%AF%95.png?download=true&token=abc")
	parsed, err := url.Parse(got)
	if err != nil {
		t.Fatalf("url.Parse() error = %v", err)
	}
	if parsed.Host != "192.168.31.236:9501" {
		t.Fatalf("host = %q, want %q", parsed.Host, "192.168.31.236:9501")
	}
	if parsed.Path != "/api/file/u/测试.png" && parsed.EscapedPath() != "/api/file/u/%E6%B5%8B%E8%AF%95.png" {
		t.Fatalf("path = %q escaped = %q", parsed.Path, parsed.EscapedPath())
	}
	if parsed.Query().Get("download") != "true" || parsed.Query().Get("token") != "abc" {
		t.Fatalf("query = %#v", parsed.Query())
	}
}
