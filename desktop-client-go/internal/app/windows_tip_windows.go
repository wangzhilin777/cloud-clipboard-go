//go:build windows

package app

import (
	"encoding/base64"
	"fmt"
	"os/exec"
	"strings"
)

func showWindowsTip(title string, body string, primaryLabel string, primaryURL string, secondaryLabel string, secondaryURL string, seconds int, width int, height int) error {
	title = strings.TrimSpace(title)
	body = strings.TrimSpace(body)
	if title == "" {
		title = "Cloud Clipboard"
	}
	if seconds <= 0 {
		seconds = 5
	}
	if width <= 0 {
		width = 348
	}
	if height <= 0 {
		height = 140
	}
	script := buildWindowsTipScript(title, body, primaryLabel, primaryURL, secondaryLabel, secondaryURL, seconds, width, height)
	encoded := base64.StdEncoding.EncodeToString([]byte(script))
	ps := fmt.Sprintf("[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('%s')) | Invoke-Expression", encoded)
	cmd := exec.Command("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-STA", "-WindowStyle", "Hidden", "-Command", ps)
	return cmd.Start()
}

func buildWindowsTipScript(title string, body string, primaryLabel string, primaryURL string, secondaryLabel string, secondaryURL string, seconds int, width int, height int) string {
	return fmt.Sprintf(`
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class DpiHelper {
  [DllImport("user32.dll")]
  public static extern bool SetProcessDPIAware();
}
"@
[void][DpiHelper]::SetProcessDPIAware()
[System.Windows.Forms.Application]::SetCompatibleTextRenderingDefault($false)

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
$tipWidth = %d
$tipHeight = %d

$form = New-Object System.Windows.Forms.Form
$form.Text = 'Cloud Clipboard Tip'
$form.FormBorderStyle = [System.Windows.Forms.FormBorderStyle]::None
$form.StartPosition = [System.Windows.Forms.FormStartPosition]::Manual
$form.ShowInTaskbar = $false
$form.TopMost = $true
$form.AutoScaleMode = [System.Windows.Forms.AutoScaleMode]::Dpi
$form.BackColor = [System.Drawing.ColorTranslator]::FromHtml('#1f2937')
$form.ForeColor = [System.Drawing.Color]::White
$form.ClientSize = New-Object System.Drawing.Size($tipWidth, $tipHeight)

$working = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
$form.Location = New-Object System.Drawing.Point(($working.Right - $form.Width - 18), ($working.Bottom - $form.Height - 18))

$contentWidth = $tipWidth - 32
$closeLeft = $tipWidth - 46
$buttonWidth = [Math]::Floor(($contentWidth - 16) / 2)
$buttonTop = $tipHeight - 40
$secondaryLeft = 16 + $buttonWidth + 16
$bodyHeight = [Math]::Max(40, $buttonTop - 56)

$title = New-Object System.Windows.Forms.Label
$title.Text = $titleText
$title.Location = New-Object System.Drawing.Point(16, 14)
$title.Size = New-Object System.Drawing.Size(($contentWidth - 40), 24)
$title.UseCompatibleTextRendering = $false
$title.AutoEllipsis = $true
$title.Font = New-Object System.Drawing.Font('Segoe UI', 10.5, [System.Drawing.FontStyle]::Bold)
$title.ForeColor = [System.Drawing.Color]::White

$close = New-Object System.Windows.Forms.Button
$close.Text = '×'
$close.Location = New-Object System.Drawing.Point($closeLeft, 10)
$close.Size = New-Object System.Drawing.Size(30, 28)
$close.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
$close.FlatAppearance.BorderSize = 0
$close.BackColor = [System.Drawing.ColorTranslator]::FromHtml('#374151')
$close.ForeColor = [System.Drawing.Color]::White
$close.Font = New-Object System.Drawing.Font('Segoe UI Symbol', 10, [System.Drawing.FontStyle]::Regular)
$close.Add_Click({ $form.Close() })

$body = New-Object System.Windows.Forms.Label
$body.Text = $bodyText
$body.Location = New-Object System.Drawing.Point(16, 46)
$body.Size = New-Object System.Drawing.Size($contentWidth, $bodyHeight)
$body.UseCompatibleTextRendering = $false
$body.AutoEllipsis = $true
$body.Font = New-Object System.Drawing.Font('Segoe UI', 9.25, [System.Drawing.FontStyle]::Regular)
$body.ForeColor = [System.Drawing.ColorTranslator]::FromHtml('#e5e7eb')

$form.Controls.Add($title)
$form.Controls.Add($close)
$form.Controls.Add($body)

if (-not [string]::IsNullOrWhiteSpace($primaryLabel)) {
  $primary = New-Object System.Windows.Forms.Button
  $primary.Text = $primaryLabel
  $primary.Location = New-Object System.Drawing.Point(16, $buttonTop)
  $primary.Size = New-Object System.Drawing.Size($buttonWidth, 28)
  $primary.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
  $primary.FlatAppearance.BorderSize = 0
  $primary.BackColor = [System.Drawing.ColorTranslator]::FromHtml('#2563eb')
  $primary.ForeColor = [System.Drawing.Color]::White
  $primary.Font = New-Object System.Drawing.Font('Segoe UI', 9, [System.Drawing.FontStyle]::Regular)
  $primary.Add_Click({
    Invoke-Action $primaryURL
    $form.Close()
  })
  $form.Controls.Add($primary)
}

if (-not [string]::IsNullOrWhiteSpace($secondaryLabel)) {
  $secondary = New-Object System.Windows.Forms.Button
  $secondary.Text = $secondaryLabel
  $secondary.Location = New-Object System.Drawing.Point($secondaryLeft, $buttonTop)
  $secondary.Size = New-Object System.Drawing.Size($buttonWidth, 28)
  $secondary.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
  $secondary.FlatAppearance.BorderSize = 0
  $secondary.BackColor = [System.Drawing.ColorTranslator]::FromHtml('#374151')
  $secondary.ForeColor = [System.Drawing.Color]::White
  $secondary.Font = New-Object System.Drawing.Font('Segoe UI', 9, [System.Drawing.FontStyle]::Regular)
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
$form.Add_Shown({ $form.Activate() })
$timer.Start()

[System.Windows.Forms.Application]::Run($form)
`, toPSString(title), toPSString(body), toPSString(primaryLabel), toPSString(primaryURL), toPSString(secondaryLabel), toPSString(secondaryURL), seconds*1000, width, height)
}

func toPSString(value string) string {
	return "'" + strings.ReplaceAll(value, "'", "''") + "'"
}
