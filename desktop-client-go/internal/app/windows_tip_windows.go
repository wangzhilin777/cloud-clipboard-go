//go:build windows

package app

import (
	"crypto/sha1"
	"encoding/hex"
	"encoding/base64"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
)

func showWindowsTip(title string, body string, primaryLabel string, primaryURL string, secondaryLabel string, secondaryURL string, dropURL string, seconds int, width int, height int, theme string, left int, top int, configPath string) error {
	title = strings.TrimSpace(title)
	body = strings.TrimSpace(body)
	if title == "" {
		title = "云剪同步"
	}
	if seconds <= 0 {
		seconds = 5
	}
	if width <= 0 {
		width = 420
	}
	if height <= 0 {
		height = 170
	}
	if strings.TrimSpace(theme) == "" {
		theme = "dark"
	}
	_ = closeWindowsTip(configPath)
	script := buildWindowsTipScript(title, body, primaryLabel, primaryURL, secondaryLabel, secondaryURL, dropURL, seconds, width, height, theme, left, top, configPath)
	encoded := base64.StdEncoding.EncodeToString([]byte(script))
	ps := fmt.Sprintf("[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('%s')) | Invoke-Expression", encoded)
	cmd := exec.Command("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-STA", "-WindowStyle", "Hidden", "-Command", ps)
	return cmd.Start()
}

func closeWindowsTip(configPath string) error {
	markerPath := activeTipMarkerPath(configPath)
	data, err := os.ReadFile(markerPath)
	if err != nil {
		return nil
	}
	pid, err := strconv.Atoi(strings.TrimSpace(string(data)))
	if err != nil || pid <= 0 {
		_ = os.Remove(markerPath)
		return nil
	}
	process, err := os.FindProcess(pid)
	if err == nil {
		_ = process.Kill()
	}
	_ = os.Remove(markerPath)
	return nil
}

func activeTipMarkerPath(configPath string) string {
	sum := sha1.Sum([]byte(strings.ToLower(strings.TrimSpace(configPath))))
	name := "cloud-clipboard-tip-" + hex.EncodeToString(sum[:8]) + ".pid"
	return filepath.Join(os.TempDir(), name)
}

func buildWindowsTipScript(title string, body string, primaryLabel string, primaryURL string, secondaryLabel string, secondaryURL string, dropURL string, seconds int, width int, height int, theme string, left int, top int, configPath string) string {
	return fmt.Sprintf(`
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Web.Extensions
Add-Type -AssemblyName System.Net.Http
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

function Invoke-DropUpload($target, $paths) {
  if ([string]::IsNullOrWhiteSpace($target)) { throw '未配置拖拽上传地址' }
  if ($null -eq $paths -or $paths.Count -eq 0) { throw '未检测到可发送的文件' }
  $client = New-Object System.Net.Http.HttpClient
  try {
    $client.Timeout = [TimeSpan]::FromSeconds(120)
    $content = New-Object System.Net.Http.MultipartFormDataContent
    foreach ($path in $paths) {
      if ([string]::IsNullOrWhiteSpace($path) -or -not (Test-Path -LiteralPath $path -PathType Leaf)) { continue }
      $fileName = [System.IO.Path]::GetFileName($path)
      $fileStream = [System.IO.File]::OpenRead($path)
      try {
        $fileContent = New-Object System.Net.Http.StreamContent($fileStream)
        $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('application/octet-stream')
        $content.Add($fileContent, 'files', $fileName)
        $fileStream = $null
      } finally {
        if ($null -ne $fileStream) {
          $fileStream.Dispose()
        }
      }
    }
    $response = $client.PostAsync($target, $content).GetAwaiter().GetResult()
    try {
      $rawStatus = [int]$response.StatusCode
      if (-not $response.IsSuccessStatusCode) {
        $detail = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if ([string]::IsNullOrWhiteSpace($detail)) {
          $detail = $response.ReasonPhrase
        }
        throw ('上传失败: {0} {1}' -f $rawStatus, $detail)
      }
      return $rawStatus
    } finally {
      $response.Dispose()
    }
  } finally {
    $content.Dispose()
    $client.Dispose()
  }
}

function Clamp-Value($value, $min, $max) {
  if ($value -lt $min) { return $min }
  if ($value -gt $max) { return $max }
  return $value
}

function Save-TipPosition($configPath, $left, $top) {
  if ([string]::IsNullOrWhiteSpace($configPath)) { return }
  try {
    if (-not (Test-Path -LiteralPath $configPath)) { return }
    $cfg = Get-Content -LiteralPath $configPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $cfg | Add-Member -NotePropertyName tipLeft -NotePropertyValue ([int]$left) -Force
    $cfg | Add-Member -NotePropertyName tipTop -NotePropertyValue ([int]$top) -Force
    $cfg | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $configPath -Encoding UTF8
  } catch {
  }
}

function Save-TipMarker($markerPath) {
  if ([string]::IsNullOrWhiteSpace($markerPath)) { return }
  try {
    $PID | Set-Content -LiteralPath $markerPath -Encoding UTF8
  } catch {
  }
}

function Clear-TipMarker($markerPath) {
  if ([string]::IsNullOrWhiteSpace($markerPath)) { return }
  try {
    if (Test-Path -LiteralPath $markerPath) {
      $saved = Get-Content -LiteralPath $markerPath -Raw -ErrorAction SilentlyContinue
      if ([string]::Trim($saved) -eq [string]$PID) {
        Remove-Item -LiteralPath $markerPath -Force -ErrorAction SilentlyContinue
      }
    }
  } catch {
  }
}

$titleText = %s
$bodyText = %s
$primaryLabel = %s
$primaryURL = %s
$secondaryLabel = %s
$secondaryURL = %s
$dropURL = %s
$timeoutMs = %d
$tipWidth = %d
$tipHeight = %d
$theme = %s
$savedLeft = %d
$savedTop = %d
$configPath = %s
$markerPath = %s

if ($theme -eq 'light') {
  $outerBg = '#d7deea'
  $surfaceBg = '#f7fbff'
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
$form.Text = '云剪同步提示'
$form.FormBorderStyle = [System.Windows.Forms.FormBorderStyle]::None
$form.StartPosition = [System.Windows.Forms.FormStartPosition]::Manual
$form.ShowInTaskbar = $false
$form.TopMost = $true
$form.AutoScaleMode = [System.Windows.Forms.AutoScaleMode]::Dpi
$form.BackColor = [System.Drawing.ColorTranslator]::FromHtml($outerBg)
$form.ForeColor = [System.Drawing.Color]::White
$form.ClientSize = New-Object System.Drawing.Size($tipWidth, $tipHeight)
$regionHandle = [DpiHelper]::CreateRoundRectRgn(0, 0, $tipWidth + 1, $tipHeight + 1, 22, 22)
$form.Region = [System.Drawing.Region]::FromHrgn($regionHandle)

$working = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
$minLeft = $working.Left
$maxLeft = $working.Right - $form.Width
$minTop = $working.Top
$maxTop = $working.Bottom - $form.Height
$defaultLeft = $working.Right - $form.Width - 18
$defaultTop = $working.Bottom - $form.Height - 18
$initialLeft = $defaultLeft
$initialTop = $defaultTop
if ($savedLeft -ge 0 -and $savedTop -ge 0) {
  $initialLeft = Clamp-Value $savedLeft $minLeft $maxLeft
  $initialTop = Clamp-Value $savedTop $minTop $maxTop
}
$form.Location = New-Object System.Drawing.Point($initialLeft, $initialTop)

$contentWidth = $tipWidth - 28
$closeLeft = $tipWidth - 46
$buttonWidth = [Math]::Floor(($contentWidth - 12) / 2)
$buttonTop = $tipHeight - 40
$secondaryLeft = 14 + $buttonWidth + 12
$bodyHeight = [Math]::Max(36, $buttonTop - 58)

$surface = New-Object System.Windows.Forms.Panel
$surface.Location = New-Object System.Drawing.Point(1, 1)
$surface.Size = New-Object System.Drawing.Size(($tipWidth - 2), ($tipHeight - 2))
$surface.BackColor = [System.Drawing.ColorTranslator]::FromHtml($surfaceBg)

$accent = New-Object System.Windows.Forms.Panel
$accent.Location = New-Object System.Drawing.Point(0, 0)
$accent.Size = New-Object System.Drawing.Size(($tipWidth - 2), 4)
$accent.BackColor = [System.Drawing.ColorTranslator]::FromHtml($accentBg)

$meta = New-Object System.Windows.Forms.Label
$meta.Text = '云剪同步'
$meta.Location = New-Object System.Drawing.Point(14, 10)
$meta.Size = New-Object System.Drawing.Size(140, 18)
$meta.UseCompatibleTextRendering = $false
$meta.Font = New-Object System.Drawing.Font('Segoe UI', 8.25, [System.Drawing.FontStyle]::Bold)
$meta.ForeColor = [System.Drawing.ColorTranslator]::FromHtml($metaFg)

$title = New-Object System.Windows.Forms.Label
$title.Text = $titleText
$title.Location = New-Object System.Drawing.Point(14, 32)
$title.Size = New-Object System.Drawing.Size(($contentWidth - 36), 24)
$title.UseCompatibleTextRendering = $false
$title.AutoEllipsis = $true
$title.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 10.5, [System.Drawing.FontStyle]::Bold)
$title.ForeColor = [System.Drawing.ColorTranslator]::FromHtml($titleFg)

$close = New-Object System.Windows.Forms.Button
$close.Text = '×'
$close.Location = New-Object System.Drawing.Point($closeLeft, 8)
$close.Size = New-Object System.Drawing.Size(30, 28)
$close.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
$close.FlatAppearance.BorderSize = 0
$close.BackColor = [System.Drawing.ColorTranslator]::FromHtml($closeBg)
$close.ForeColor = [System.Drawing.ColorTranslator]::FromHtml($closeFg)
$close.Font = New-Object System.Drawing.Font('Segoe UI Symbol', 10, [System.Drawing.FontStyle]::Regular)
$close.Add_Click({ $form.Close() })

$body = New-Object System.Windows.Forms.Label
$body.Text = $bodyText
$body.Location = New-Object System.Drawing.Point(14, 58)
$body.Size = New-Object System.Drawing.Size($contentWidth, $bodyHeight)
$body.UseCompatibleTextRendering = $false
$body.AutoEllipsis = $true
$body.Font = New-Object System.Drawing.Font('Segoe UI', 9, [System.Drawing.FontStyle]::Regular)
$body.ForeColor = [System.Drawing.ColorTranslator]::FromHtml($bodyFg)

$dropHint = New-Object System.Windows.Forms.Label
$dropHint.Text = '也可以把文件拖到这里直接发送'
$dropHint.Location = New-Object System.Drawing.Point(14, ($buttonTop - 18))
$dropHint.Size = New-Object System.Drawing.Size($contentWidth, 16)
$dropHint.UseCompatibleTextRendering = $false
$dropHint.Font = New-Object System.Drawing.Font('Segoe UI', 8.25, [System.Drawing.FontStyle]::Regular)
$dropHint.ForeColor = [System.Drawing.ColorTranslator]::FromHtml($metaFg)
$dropHint.Visible = -not [string]::IsNullOrWhiteSpace($dropURL)

$surface.Controls.Add($accent)
$surface.Controls.Add($meta)
$surface.Controls.Add($title)
$surface.Controls.Add($close)
$surface.Controls.Add($body)
$surface.Controls.Add($dropHint)

if (-not [string]::IsNullOrWhiteSpace($primaryLabel)) {
  $primary = New-Object System.Windows.Forms.Button
  $primary.Text = $primaryLabel
  $primary.Location = New-Object System.Drawing.Point(14, $buttonTop)
  $primary.Size = New-Object System.Drawing.Size($buttonWidth, 28)
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
  $secondary.Size = New-Object System.Drawing.Size($buttonWidth, 28)
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

$script:dragging = $false
$script:dragStartCursor = $null
$script:dragStartForm = $null

function Register-DragTarget($control) {
  $control.Add_MouseDown({
    if ($_.Button -ne [System.Windows.Forms.MouseButtons]::Left) { return }
    $script:dragging = $true
    $script:dragStartCursor = [System.Windows.Forms.Cursor]::Position
    $script:dragStartForm = $form.Location
    $timer.Stop()
  })
  $control.Add_MouseMove({
    if (-not $script:dragging) { return }
    $cursor = [System.Windows.Forms.Cursor]::Position
    $nextLeft = $script:dragStartForm.X + ($cursor.X - $script:dragStartCursor.X)
    $nextTop = $script:dragStartForm.Y + ($cursor.Y - $script:dragStartCursor.Y)
    $nextLeft = Clamp-Value $nextLeft $minLeft $maxLeft
    $nextTop = Clamp-Value $nextTop $minTop $maxTop
    $form.Location = New-Object System.Drawing.Point($nextLeft, $nextTop)
  })
  $control.Add_MouseUp({
    if (-not $script:dragging) { return }
    $script:dragging = $false
    $timer.Stop()
    $timer.Start()
  })
}

Register-DragTarget $surface
Register-DragTarget $meta
Register-DragTarget $title
Register-DragTarget $body
Register-DragTarget $dropHint

if (-not [string]::IsNullOrWhiteSpace($dropURL)) {
  $dropTargets = @($form, $surface, $meta, $title, $body, $dropHint)
  foreach ($dropTarget in $dropTargets) {
    $dropTarget.AllowDrop = $true
    $dropTarget.Add_DragEnter({
      if ($_.Data.GetDataPresent([System.Windows.Forms.DataFormats]::FileDrop)) {
        $_.Effect = [System.Windows.Forms.DragDropEffects]::Copy
        $timer.Stop()
      } else {
        $_.Effect = [System.Windows.Forms.DragDropEffects]::None
      }
    })
    $dropTarget.Add_DragLeave({
      $timer.Stop()
      $timer.Start()
    })
    $dropTarget.Add_DragDrop({
      try {
        $fileDrop = $_.Data.GetData([System.Windows.Forms.DataFormats]::FileDrop)
        $paths = @($fileDrop | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) })
        if ($paths.Count -eq 0) {
          throw '请拖入文件，暂不支持目录直接发送。'
        }
        Invoke-DropUpload $dropURL $paths | Out-Null
        $form.Close()
      } catch {
        [System.Windows.Forms.MessageBox]::Show($_.Exception.Message, '发送失败', [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Warning) | Out-Null
        $timer.Stop()
        $timer.Start()
      }
    })
  }
}

$form.Add_FormClosing({
  Save-TipPosition $configPath $form.Left $form.Top
  Clear-TipMarker $markerPath
})

Save-TipMarker $markerPath

[System.Windows.Forms.Application]::Run($form)
`, toPSString(title), toPSString(body), toPSString(primaryLabel), toPSString(primaryURL), toPSString(secondaryLabel), toPSString(secondaryURL), toPSString(dropURL), seconds*1000, width, height, toPSString(strings.ToLower(strings.TrimSpace(theme))), left, top, toPSString(configPath), toPSString(activeTipMarkerPath(configPath)))
}

func toPSString(value string) string {
	return "'" + strings.ReplaceAll(value, "'", "''") + "'"
}
