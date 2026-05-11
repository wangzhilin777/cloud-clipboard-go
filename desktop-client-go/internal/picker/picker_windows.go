//go:build windows

package picker

import (
	"bytes"
	"context"
	"fmt"
	"os/exec"
	"strings"
)

func PickFiles(ctx context.Context) ([]string, error) {
	script := `
Add-Type -AssemblyName System.Windows.Forms
$dialog = New-Object System.Windows.Forms.OpenFileDialog
$dialog.Multiselect = $true
$dialog.Title = '选择要发送到 Cloud Clipboard 的文件'
$dialog.Filter = '所有文件 (*.*)|*.*'
$dialog.RestoreDirectory = $true
if ($dialog.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) {
  exit 0
}
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$dialog.FileNames | ForEach-Object { $_ }
`
	cmd := exec.CommandContext(ctx, "powershell", "-NoProfile", "-STA", "-Command", script)
	var stdout bytes.Buffer
	var stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	if err := cmd.Run(); err != nil {
		return nil, fmt.Errorf("打开文件选择框失败: %w %s", err, strings.TrimSpace(stderr.String()))
	}
	lines := strings.Split(strings.ReplaceAll(stdout.String(), "\r\n", "\n"), "\n")
	results := make([]string, 0, len(lines))
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if line != "" {
			results = append(results, line)
		}
	}
	return results, nil
}
