package lib

import "testing"

func TestRandomBytesReturnsRequestedLength(t *testing.T) {
	data, err := random_bytes(32)
	if err != nil {
		t.Fatalf("random_bytes returned error: %v", err)
	}
	if len(data) != 32 {
		t.Fatalf("random_bytes length = %d, want 32", len(data))
	}
}

func TestGenerateRandomStringReturnsRequestedLength(t *testing.T) {
	value, err := generateRandomString(12)
	if err != nil {
		t.Fatalf("generateRandomString returned error: %v", err)
	}
	if len(value) != 12 {
		t.Fatalf("generateRandomString length = %d, want 12", len(value))
	}
	for _, ch := range value {
		if !(ch >= 'a' && ch <= 'z') && !(ch >= 'A' && ch <= 'Z') && !(ch >= '0' && ch <= '9') {
			t.Fatalf("generateRandomString produced non-alphanumeric rune %q in %q", ch, value)
		}
	}
}
