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

func TestSyncHubRemoveAndClearMessagesByRoom(t *testing.T) {
	hub := newTestSyncHub(t, 60)

	_, err := hub.AddMessage(SyncMessageRecord{
		MessageID:      "default-a",
		SourceDeviceID: "device-a",
		Room:           "default",
		Text:           "default text a",
	})
	if err != nil {
		t.Fatalf("AddMessage default-a returned error: %v", err)
	}
	_, err = hub.AddMessage(SyncMessageRecord{
		MessageID:      "default-b",
		SourceDeviceID: "device-a",
		Room:           "default",
		Text:           "default text b",
	})
	if err != nil {
		t.Fatalf("AddMessage default-b returned error: %v", err)
	}
	_, err = hub.AddMessage(SyncMessageRecord{
		MessageID:      "other-a",
		SourceDeviceID: "device-b",
		Room:           "other",
		Text:           "other text",
	})
	if err != nil {
		t.Fatalf("AddMessage other-a returned error: %v", err)
	}

	removed, err := hub.RemoveMessage("default", "default-a")
	if err != nil {
		t.Fatalf("RemoveMessage returned error: %v", err)
	}
	if !removed {
		t.Fatalf("RemoveMessage did not remove existing message")
	}
	if removed, err := hub.RemoveMessage("default", "other-a"); err != nil || removed {
		t.Fatalf("RemoveMessage should not remove another room message, removed=%v err=%v", removed, err)
	}

	defaultMessages := hub.GetRecentMessages("default")
	if len(defaultMessages) != 1 || defaultMessages[0].MessageID != "default-b" {
		t.Fatalf("default messages after remove = %#v, want only default-b", defaultMessages)
	}

	removedCount, err := hub.ClearMessages("default")
	if err != nil {
		t.Fatalf("ClearMessages returned error: %v", err)
	}
	if removedCount != 1 {
		t.Fatalf("ClearMessages removed %d messages, want 1", removedCount)
	}
	if len(hub.GetRecentMessages("default")) != 0 {
		t.Fatalf("default messages should be empty after ClearMessages")
	}
	otherMessages := hub.GetRecentMessages("other")
	if len(otherMessages) != 1 || otherMessages[0].MessageID != "other-a" {
		t.Fatalf("other room messages = %#v, want only other-a", otherMessages)
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

func TestSyncHubPayloadNoticeDefaultsAndIdempotency(t *testing.T) {
	hub := newTestSyncHub(t, 60)

	created, err := hub.AddPayloadNotice(SyncPayloadNotice{
		SourceDeviceID: "device-a",
		Title:          "image.png",
		Mime:           "image/png",
		Size:           1234,
	})
	if err != nil {
		t.Fatalf("AddPayloadNotice returned error: %v", err)
	}
	if created.PayloadID == "" {
		t.Fatalf("AddPayloadNotice did not generate PayloadID")
	}
	if created.Room != "default" {
		t.Fatalf("AddPayloadNotice room = %q, want default", created.Room)
	}
	if created.Kind != "file" {
		t.Fatalf("AddPayloadNotice kind = %q, want file", created.Kind)
	}
	if created.CreatedAt == 0 {
		t.Fatalf("AddPayloadNotice did not set CreatedAt")
	}

	duplicate, err := hub.AddPayloadNotice(SyncPayloadNotice{
		PayloadID:      created.PayloadID,
		SourceDeviceID: "device-b",
		Room:           "other",
		Kind:           "image",
		Title:          "changed.png",
		CreatedAt:      created.CreatedAt + 1000,
	})
	if err != nil {
		t.Fatalf("AddPayloadNotice duplicate returned error: %v", err)
	}
	if duplicate != created {
		t.Fatalf("duplicate payload = %#v, want original %#v", duplicate, created)
	}
	if len(hub.GetRecentPayloads("default")) != 1 {
		t.Fatalf("default room should still contain only the original payload")
	}
	if len(hub.GetRecentPayloads("other")) != 0 {
		t.Fatalf("duplicate payload should not be inserted into another room")
	}
}

func TestSyncHubCleanupRemovesExpiredStateByPolicy(t *testing.T) {
	hub := newTestSyncHub(t, 60)
	now := time.Date(2026, 6, 8, 12, 0, 0, 0, time.UTC)
	nowMillis := now.UnixMilli()

	_, err := hub.AddMessage(SyncMessageRecord{
		MessageID:      "old-message",
		SourceDeviceID: "device-a",
		Room:           "default",
		Text:           "old",
		CreatedAt:      nowMillis - 10_000,
	})
	if err != nil {
		t.Fatalf("AddMessage old returned error: %v", err)
	}
	_, err = hub.AddMessage(SyncMessageRecord{
		MessageID:      "fresh-message",
		SourceDeviceID: "device-a",
		Room:           "default",
		Text:           "fresh",
		CreatedAt:      nowMillis - 1_000,
	})
	if err != nil {
		t.Fatalf("AddMessage fresh returned error: %v", err)
	}
	_, err = hub.AddPayloadNotice(SyncPayloadNotice{
		PayloadID:      "old-payload",
		SourceDeviceID: "device-a",
		Room:           "default",
		Kind:           "file",
		Title:          "old.txt",
		CreatedAt:      nowMillis - 10_000,
	})
	if err != nil {
		t.Fatalf("AddPayloadNotice old returned error: %v", err)
	}
	_, err = hub.AddPayloadNotice(SyncPayloadNotice{
		PayloadID:      "fresh-payload",
		SourceDeviceID: "device-a",
		Room:           "default",
		Kind:           "file",
		Title:          "fresh.txt",
		CreatedAt:      nowMillis - 1_000,
	})
	if err != nil {
		t.Fatalf("AddPayloadNotice fresh returned error: %v", err)
	}

	hub.mu.Lock()
	hub.state.Devices = append(hub.state.Devices,
		SyncDevice{DeviceID: "old-pending", Name: "old pending", Room: "default", Platform: "android", ClientType: "native", Trusted: false, Status: "pending", CreatedAt: nowMillis - 10_000, LastSeenAt: nowMillis - 10_000, Meta: map[string]interface{}{}},
		SyncDevice{DeviceID: "fresh-pending", Name: "fresh pending", Room: "default", Platform: "android", ClientType: "native", Trusted: false, Status: "pending", CreatedAt: nowMillis - 1_000, LastSeenAt: nowMillis - 1_000, Meta: map[string]interface{}{}},
		SyncDevice{DeviceID: "old-trusted", Name: "old trusted", Room: "default", Platform: "desktop", ClientType: "native", Trusted: true, Status: "trusted", CreatedAt: nowMillis - 20_000, LastSeenAt: nowMillis - 20_000, Meta: map[string]interface{}{}},
		SyncDevice{DeviceID: "fresh-trusted", Name: "fresh trusted", Room: "default", Platform: "desktop", ClientType: "native", Trusted: true, Status: "trusted", CreatedAt: nowMillis - 1_000, LastSeenAt: nowMillis - 1_000, Meta: map[string]interface{}{}},
	)
	hub.mu.Unlock()

	result, err := hub.Cleanup(SyncCleanupPolicy{
		MessageExpireMillis:       5_000,
		PayloadExpireMillis:       5_000,
		PendingDeviceExpireMillis: 5_000,
		TrustedDeviceExpireMillis: 15_000,
	}, now)
	if err != nil {
		t.Fatalf("Cleanup returned error: %v", err)
	}
	if result.RemovedMessages != 1 || result.RemovedPayloads != 1 || result.RemovedDevices != 2 {
		t.Fatalf("Cleanup result = %#v, want 1 message, 1 payload, 2 devices", result)
	}

	if hub.HasMessage("old-message") {
		t.Fatalf("old message should be removed")
	}
	if !hub.HasMessage("fresh-message") {
		t.Fatalf("fresh message should remain")
	}
	if len(hub.GetRecentPayloads("default")) != 1 || hub.GetRecentPayloads("default")[0].PayloadID != "fresh-payload" {
		t.Fatalf("payloads after cleanup = %#v, want only fresh-payload", hub.GetRecentPayloads("default"))
	}

	devices := hub.ListDevices("default")
	if len(devices) != 2 {
		t.Fatalf("devices after cleanup = %#v, want 2 fresh devices", devices)
	}
	for _, device := range devices {
		deviceID, _ := device["deviceId"].(string)
		if deviceID == "old-pending" || deviceID == "old-trusted" {
			t.Fatalf("expired device %q should be removed", deviceID)
		}
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
