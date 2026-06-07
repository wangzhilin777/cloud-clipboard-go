package lib

import (
	"fmt"
	"log"
	"path/filepath"
	"testing"
	"time"
)

func TestSyncHubRecentMessagesUseConfiguredLimit(t *testing.T) {
	hub := newTestSyncHub(t, 60)

	for i := 0; i < 65; i++ {
		_, err := hub.AddMessage(SyncMessageRecord{
			MessageID:      fmt.Sprintf("message-%02d", i),
			SourceDeviceID: "device-a",
			Room:           "default",
			Text:           fmt.Sprintf("text-%02d", i),
			CreatedAt:      int64(i + 1),
		})
		if err != nil {
			t.Fatalf("AddMessage returned error: %v", err)
		}
	}

	_, err := hub.AddMessage(SyncMessageRecord{
		MessageID:      "other-room-message",
		SourceDeviceID: "device-b",
		Room:           "other",
		Text:           "other",
		CreatedAt:      100,
	})
	if err != nil {
		t.Fatalf("AddMessage for other room returned error: %v", err)
	}

	recent := hub.GetRecentMessages("default")
	if len(recent) != 60 {
		t.Fatalf("GetRecentMessages returned %d items, want 60", len(recent))
	}
	if recent[0].MessageID != "message-05" {
		t.Fatalf("first recent message = %q, want message-05", recent[0].MessageID)
	}
	if recent[len(recent)-1].MessageID != "message-64" {
		t.Fatalf("last recent message = %q, want message-64", recent[len(recent)-1].MessageID)
	}

	other := hub.GetRecentMessages("other")
	if len(other) != 1 || other[0].MessageID != "other-room-message" {
		t.Fatalf("other room messages = %#v, want only other-room-message", other)
	}
}

func TestSyncHubDetectsRecentDuplicateTextFromSameSource(t *testing.T) {
	hub := newTestSyncHub(t, 60)

	_, err := hub.AddMessage(SyncMessageRecord{
		MessageID:      "message-a",
		SourceDeviceID: "device-a",
		Room:           "default",
		Text:           "same text",
		CreatedAt:      time.Now().UnixMilli(),
	})
	if err != nil {
		t.Fatalf("AddMessage returned error: %v", err)
	}

	if !hub.HasRecentTextFromSource("default", "device-a", "same text", 30*time.Second) {
		t.Fatalf("HasRecentTextFromSource did not detect same-room same-source text")
	}
	if hub.HasRecentTextFromSource("default", "device-b", "same text", 30*time.Second) {
		t.Fatalf("HasRecentTextFromSource should not match another source device")
	}
	if hub.HasRecentTextFromSource("other", "device-a", "same text", 30*time.Second) {
		t.Fatalf("HasRecentTextFromSource should not match another room")
	}
	if hub.HasRecentTextFromSource("default", "device-a", "different text", 30*time.Second) {
		t.Fatalf("HasRecentTextFromSource should not match different text")
	}
}

func TestSyncHubRecentPayloadsUseConfiguredLimit(t *testing.T) {
	hub := newTestSyncHub(t, 60)

	for i := 0; i < 65; i++ {
		_, err := hub.AddPayloadNotice(SyncPayloadNotice{
			PayloadID:      fmt.Sprintf("payload-%02d", i),
			SourceDeviceID: "device-a",
			Room:           "default",
			Kind:           "file",
			Title:          fmt.Sprintf("file-%02d.txt", i),
			CreatedAt:      int64(i + 1),
		})
		if err != nil {
			t.Fatalf("AddPayloadNotice returned error: %v", err)
		}
	}

	_, err := hub.AddPayloadNotice(SyncPayloadNotice{
		PayloadID:      "other-room-payload",
		SourceDeviceID: "device-b",
		Room:           "other",
		Kind:           "file",
		Title:          "other.txt",
		CreatedAt:      100,
	})
	if err != nil {
		t.Fatalf("AddPayloadNotice for other room returned error: %v", err)
	}

	recent := hub.GetRecentPayloads("default")
	if len(recent) != 60 {
		t.Fatalf("GetRecentPayloads returned %d items, want 60", len(recent))
	}
	if recent[0].PayloadID != "payload-05" {
		t.Fatalf("first recent payload = %q, want payload-05", recent[0].PayloadID)
	}
	if recent[len(recent)-1].PayloadID != "payload-64" {
		t.Fatalf("last recent payload = %q, want payload-64", recent[len(recent)-1].PayloadID)
	}

	other := hub.GetRecentPayloads("other")
	if len(other) != 1 || other[0].PayloadID != "other-room-payload" {
		t.Fatalf("other room payloads = %#v, want only other-room-payload", other)
	}
}

func newTestSyncHub(t *testing.T, messageLimit int) *SyncHub {
	t.Helper()
	statePath := filepath.Join(t.TempDir(), "sync-state.json")
	hub, err := NewSyncHub(log.New(testingWriter{t}, "", 0), statePath, messageLimit, 4096)
	if err != nil {
		t.Fatalf("NewSyncHub returned error: %v", err)
	}
	return hub
}

type testingWriter struct {
	t *testing.T
}

func (w testingWriter) Write(p []byte) (int, error) {
	w.t.Helper()
	w.t.Log(string(p))
	return len(p), nil
}
