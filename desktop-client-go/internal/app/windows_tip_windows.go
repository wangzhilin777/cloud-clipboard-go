//go:build windows

package app

import (
	"encoding/base64"
	"fmt"
	"os/exec"
	"strings"
)

func showWindowsTip(title string, body string, primaryLabel string, primaryURL string, secondaryLabel string, secondaryURL string, seconds int) error {
	title = strings.TrimSpace(title)
	body = strings.TrimSpace(body)
	if title == "" {
		title = "Cloud Clipboard"
	}
	if seconds <= 0 {
		seconds = 5
	}
	script := buildWindowsTipScript(title, body, primaryLabel, primaryURL, secondaryLabel, secondaryURL, seconds)
	encoded := base64.StdEncoding.EncodeToString([]byte(script))
	ps := fmt.Sprintf("[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('%s')) | Invoke-Expression", encoded)
	cmd := exec.Command("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-STA", "-WindowStyle", "Hidden", "-Command", ps)
	return cmd.Start()
}

func buildWindowsTipScript(title string, body string, primaryLabel string, primaryURL string, secondaryLabel string, secondaryURL string, seconds int) string {
	return fmt.Sprintf(`
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

function Invoke-Action($target) {
  if ([string]::IsNullOrWhiteSpace($target)) { return }
  Start-Process $target | Out-Null
}

$titleText = %s
$bodyText = %s
$primaryLabel = %s
$primaryURL = %s
$secondaryLabel = %s
$secondaryURL = %s
$timeoutMs = %d

$form = New-Object System.Windows.Forms.Form
$form.Text = 'Cloud Clipboard Tip'
$form.FormBorderStyle = [System.Windows.Forms.FormBorderStyle]::None
$form.StartPosition = [System.Windows.Forms.FormStartPosition]::Manual
$form.ShowInTaskbar = $false
$form.TopMost = $true
$form.BackColor = [System.Drawing.ColorTranslator]::FromHtml('#1f2937')
$form.ForeColor = [System.Drawing.Color]::White
$form.ClientSize = New-Object System.Drawing.Size(348, 140)

$working = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
$form.Location = New-Object System.Drawing.Point(($working.Right - $form.Width - 18), ($working.Bottom - $form.Height - 18))

$title = New-Object System.Windows.Forms.Label
$title.Text = $titleText
$title.Location = New-Object System.Drawing.Point(16, 14)
$title.Size = New-Object System.Drawing.Size(250, 24)
$title.Font = New-Object System.Drawing.Font('Microsoft YaHei UI', 10.5, [System.Drawing.FontStyle]::Bold)
$title.ForeColor = [System.Drawing.Color]::White

$close = New-Object System.Windows.Forms.Button
$close.Text = '×'
$close.Location = New-Object System.Drawing.Point(302, 10)
$close.Size = New-Object System.Drawing.Size(30, 28)
$close.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
$close.FlatAppearance.BorderSize = 0
$close.BackColor = [System.Drawing.ColorTranslator]::FromHtml('#374151')
$close.ForeColor = [System.Drawing.Color]::White
$close.Add_Click({ $form.Close() })

$body = New-Object System.Windows.Forms.Label
$body.Text = $bodyText
$body.Location = New-Object System.Drawing.Point(16, 46)
$body.Size = New-Object System.Drawing.Size(316, 48)
$body.Font = New-Object System.Drawing.Font('Microsoft YaHei UI', 9)
$body.ForeColor = [System.Drawing.ColorTranslator]::FromHtml('#e5e7eb')

$form.Controls.Add($title)
$form.Controls.Add($close)
$form.Controls.Add($body)

if (-not [string]::IsNullOrWhiteSpace($primaryLabel)) {
  $primary = New-Object System.Windows.Forms.Button
  $primary.Text = $primaryLabel
  $primary.Location = New-Object System.Drawing.Point(16, 100)
  $primary.Size = New-Object System.Drawing.Size(150, 28)
  $primary.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
  $primary.FlatAppearance.BorderSize = 0
  $primary.BackColor = [System.Drawing.ColorTranslator]::FromHtml('#2563eb')
  $primary.ForeColor = [System.Drawing.Color]::White
  $primary.Add_Click({
    Invoke-Action $primaryURL
    $form.Close()
  })
  $form.Controls.Add($primary)
}

if (-not [string]::IsNullOrWhiteSpace($secondaryLabel)) {
  $secondary = New-Object System.Windows.Forms.Button
  $secondary.Text = $secondaryLabel
  $secondary.Location = New-Object System.Drawing.Point(182, 100)
  $secondary.Size = New-Object System.Drawing.Size(150, 28)
  $secondary.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
  $secondary.FlatAppearance.BorderSize = 0
  $secondary.BackColor = [System.Drawing.ColorTranslator]::FromHtml('#374151')
  $secondary.ForeColor = [System.Drawing.Color]::White
  $secondary.Add_Click({
    Invoke-Action $secondaryURL
    $form.Close()
  })
  $form.Controls.Add($secondary)
}

$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = $timeoutMs
$timer.Add_Tick({
  $timer.Stop()
  $form.Close()
})
$timer.Start()

[System.Windows.Forms.Application]::Run($form)
`, toPSString(title), toPSString(body), toPSString(primaryLabel), toPSString(primaryURL), toPSString(secondaryLabel), toPSString(secondaryURL), seconds*1000)
}

func toPSString(value string) string {
	return "'" + strings.ReplaceAll(value, "'", "''") + "'"
}
