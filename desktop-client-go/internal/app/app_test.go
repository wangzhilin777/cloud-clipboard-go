package app

import "testing"

func TestDesktopOSIntegrationsDisabled(t *testing.T) {
	t.Setenv("CLOUD_CLIPBOARD_DESKTOP_DISABLE_OS_INTEGRATIONS", "1")
	if !desktopOSIntegrationsDisabled() {
		t.Fatalf("desktopOSIntegrationsDisabled() = false, want true")
	}

	t.Setenv("CLOUD_CLIPBOARD_DESKTOP_DISABLE_OS_INTEGRATIONS", "off")
	if desktopOSIntegrationsDisabled() {
		t.Fatalf("desktopOSIntegrationsDisabled() = true, want false")
	}
}
