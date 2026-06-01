package transfer

import (
	"reflect"
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
