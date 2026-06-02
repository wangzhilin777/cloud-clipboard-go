package syncclient

import (
	"reflect"
	"testing"
)

func TestSyncServerEndpointsIncludeRootAndAPI(t *testing.T) {
	got := syncServerEndpoints("http://127.0.0.1:9501/", "研发 房")
	want := []string{
		"http://127.0.0.1:9501/sync/server?room=%E7%A0%94%E5%8F%91+%E6%88%BF",
		"http://127.0.0.1:9501/api/sync/server?room=%E7%A0%94%E5%8F%91+%E6%88%BF",
	}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("syncServerEndpoints() = %#v, want %#v", got, want)
	}
}

func TestSyncServerEndpointsDeduplicateAPIBase(t *testing.T) {
	got := syncServerEndpoints("https://example.com/api", "")
	want := []string{
		"https://example.com/api/sync/server?room=",
	}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("syncServerEndpoints() = %#v, want %#v", got, want)
	}
}
