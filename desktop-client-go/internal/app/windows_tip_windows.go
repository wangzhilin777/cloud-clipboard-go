//go:build windows

package app

import (
	"encoding/base64"
	"fmt"
	"os/exec"
	"strings"
)

func showWindowsTip(title string, body string, primaryLabel string, primaryURL string, secondaryLabel string, secondaryURL string, seconds int, width int, height int, theme string) error {
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
	if strings.TrimSpace(theme) == "" {
		theme = "dark"
	}
	script := buildWindowsTipScript(title, body, primaryLabel, primaryURL, secondaryLabel, secondaryURL, seconds, width, height, theme)
	encoded := base64.StdEncoding.EncodeToString([]byte(script))
	ps := fmt.Sprintf("[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('%s')) | Invoke-Expression", encoded)
	cmd := exec.Command("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-STA", "-WindowStyle", "Hidden", "-Command", ps)
	return cmd.Start()
}

func buildWindowsTipScript(title string, body string, primaryLabel string, primaryURL string, secondaryLabel string, secondaryURL string, seconds int, width int, height int, theme string) string {
	return fmt.Sprintf(`
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class DpiHelper {
  [DllImport("user32.dll")]
  public static extern bool SetProcessDPIAware();
  [DllImport("gdi32.dll")]
  public static extern IntPtr CreateRoundRectRgn(int nLeftRect, int nTopRect, int nRightRect, int nBottomRect, int nWidthEllipse, int nHeightEllipse);
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
$theme = %s

if ($theme -eq 'light') {
  $outerBg = '#d7deea'
  $surfaceBg = '#f8fbff'
  $accentBg = '#2f6df6'
  $metaFg = '#53719b'
  $titleFg = '#16263d'
  $bodyFg = '#4c627f'
  $closeBg = '#e8eef8'
  $closeFg = '#5a6f8e'
  $primaryBg = '#2f6df6'
  $primaryFg = '#ffffff'
  $secondaryBg = '#edf2f8'
  $secondaryFg = '#24405f'
} else {
  $outerBg = '#d8e2f2'
  $surfaceBg = '#1f2430'
  $accentBg = '#4f8cff'
  $metaFg = '#8ca3c7'
  $titleFg = '#ffffff'
  $bodyFg = '#d7dfec'
  $closeBg = '#2c3342'
  $closeFg = '#d5deed'
  $primaryBg = '#4f8cff'
  $primaryFg = '#ffffff'
  $secondaryBg = '#2d3443'
  $secondaryFg = '#e6edf8'
}

$form = New-Object System.Windows.Forms.Form
$form.Text = 'Cloud Clipboard Tip'
$form.FormBorderStyle = [System.Windows.Forms.FormBorderStyle]::None
$form.StartPosition = [System.Windows.Forms.FormStartPosition]::Manual
$form.ShowInTaskbar = $false
$form.TopMost = $true
$form.AutoScaleMode = [System.Windows.Forms.AutoScaleMode]::Dpi
$form.BackColor = [System.Drawing.ColorTranslator]::FromHtml($outerBg)
$form.ForeColor = [System.Drawing.Color]::White
$form.ClientSize = New-Object System.Drawing.Size($tipWidth, $tipHeight)
$regionHandle = [DpiHelper]::CreateRoundRectRgn(0, 0, $tipWidth + 1, $tipHeight + 1, 26, 26)
$form.Region = [System.Drawing.Region]::FromHrgn($regionHandle)

$working = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
$form.Location = New-Object System.Drawing.Point(($working.Right - $form.Width - 18), ($working.Bottom - $form.Height - 18))

$contentWidth = $tipWidth - 32
$closeLeft = $tipWidth - 46
$buttonWidth = [Math]::Floor(($contentWidth - 16) / 2)
$buttonTop = $tipHeight - 44
$secondaryLeft = 16 + $buttonWidth + 16
$bodyHeight = [Math]::Max(40, $buttonTop - 64)

$surface = New-Object System.Windows.Forms.Panel
$surface.Location = New-Object System.Drawing.Point(1, 1)
$surface.Size = New-Object System.Drawing.Size(($tipWidth - 2), ($tipHeight - 2))
$surface.BackColor = [System.Drawing.ColorTranslator]::FromHtml($surfaceBg)

$accent = New-Object System.Windows.Forms.Panel
$accent.Location = New-Object System.Drawing.Point(0, 0)
$accent.Size = New-Object System.Drawing.Size(($tipWidth - 2), 4)
$accent.BackColor = [System.Drawing.ColorTranslator]::FromHtml($accentBg)

$meta = New-Object System.Windows.Forms.Label
$meta.Text = 'Cloud Clipboard'
$meta.Location = New-Object System.Drawing.Point(16, 12)
$meta.Size = New-Object System.Drawing.Size(140, 18)
$meta.UseCompatibleTextRendering = $false
$meta.Font = New-Object System.Drawing.Font('Segoe UI', 8.25, [System.Drawing.FontStyle]::Bold)
$meta.ForeColor = [System.Drawing.ColorTranslator]::FromHtml($metaFg)

$title = New-Object System.Windows.Forms.Label
$title.Text = $titleText
$title.Location = New-Object System.Drawing.Point(16, 34)
$title.Size = New-Object System.Drawing.Size(($contentWidth - 40), 26)
$title.UseCompatibleTextRendering = $false
$title.AutoEllipsis = $true
$title.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 10.5, [System.Drawing.FontStyle]::Bold)
$title.ForeColor = [System.Drawing.ColorTranslator]::FromHtml($titleFg)

$close = New-Object System.Windows.Forms.Button
$close.Text = '×'
$close.Location = New-Object System.Drawing.Point($closeLeft, 10)
$close.Size = New-Object System.Drawing.Size(30, 28)
$close.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
$close.FlatAppearance.BorderSize = 0
$close.BackColor = [System.Drawing.ColorTranslator]::FromHtml($closeBg)
$close.ForeColor = [System.Drawing.ColorTranslator]::FromHtml($closeFg)
$close.Font = New-Object System.Drawing.Font('Segoe UI Symbol', 10, [System.Drawing.FontStyle]::Regular)
$close.Add_Click({ $form.Close() })

$body = New-Object System.Windows.Forms.Label
$body.Text = $bodyText
$body.Location = New-Object System.Drawing.Point(16, 64)
$body.Size = New-Object System.Drawing.Size($contentWidth, $bodyHeight)
$body.UseCompatibleTextRendering = $false
$body.AutoEllipsis = $true
$body.Font = New-Object System.Drawing.Font('Segoe UI', 9, [System.Drawing.FontStyle]::Regular)
$body.ForeColor = [System.Drawing.ColorTranslator]::FromHtml($bodyFg)

$surface.Controls.Add($accent)
$surface.Controls.Add($meta)
$surface.Controls.Add($title)
$surface.Controls.Add($close)
$surface.Controls.Add($body)

if (-not [string]::IsNullOrWhiteSpace($primaryLabel)) {
  $primary = New-Object System.Windows.Forms.Button
  $primary.Text = $primaryLabel
  $primary.Location = New-Object System.Drawing.Point(16, $buttonTop)
  $primary.Size = New-Object System.Drawing.Size($buttonWidth, 30)
  $primary.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
  $primary.FlatAppearance.BorderSize = 0
  $primary.BackColor = [System.Drawing.ColorTranslator]::FromHtml($primaryBg)
  $primary.ForeColor = [System.Drawing.ColorTranslator]::FromHtml($primaryFg)
  $primary.Font = New-Object System.Drawing.Font('Segoe UI', 9, [System.Drawing.FontStyle]::Regular)
  $primary.Add_Click({
    Invoke-Action $primaryURL
    $form.Close()
  })
  $surface.Controls.Add($primary)
}

if (-not [string]::IsNullOrWhiteSpace($secondaryLabel)) {
  $secondary = New-Object System.Windows.Forms.Button
  $secondary.Text = $secondaryLabel
  $secondary.Location = New-Object System.Drawing.Point($secondaryLeft, $buttonTop)
  $secondary.Size = New-Object System.Drawing.Size($buttonWidth, 30)
  $secondary.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
  $secondary.FlatAppearance.BorderSize = 0
  $secondary.BackColor = [System.Drawing.ColorTranslator]::FromHtml($secondaryBg)
  $secondary.ForeColor = [System.Drawing.ColorTranslator]::FromHtml($secondaryFg)
  $secondary.Font = New-Object System.Drawing.Font('Segoe UI', 9, [System.Drawing.FontStyle]::Regular)
  $secondary.Add_Click({
    Invoke-Action $secondaryURL
    $form.Close()
  })
  $surface.Controls.Add($secondary)
}

$form.Controls.Add($surface)

$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = $timeoutMs
$timer.Add_Tick({
  $timer.Stop()
  $form.Close()
})
$timer.Start()

[System.Windows.Forms.Application]::Run($form)
`, toPSString(title), toPSString(body), toPSString(primaryLabel), toPSString(primaryURL), toPSString(secondaryLabel), toPSString(secondaryURL), seconds*1000, width, height, toPSString(strings.ToLower(strings.TrimSpace(theme))))
}

func toPSString(value string) string {
	return "'" + strings.ReplaceAll(value, "'", "''") + "'"
}
