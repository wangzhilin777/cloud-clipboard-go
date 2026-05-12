//go:build windows

package fileclip

import (
	"fmt"
	"os/exec"
	"strings"
)

func setFileList(paths []string) error {
	items := make([]string, 0, len(paths))
	for _, path := range paths {
		path = strings.TrimSpace(path)
		if path == "" {
			continue
		}
		items = append(items, fmt.Sprintf("$list.Add('%s') | Out-Null", escapePowerShellLiteral(path)))
	}
	if len(items) == 0 {
		return fmt.Errorf("没有可写入剪贴板的文件")
	}
	script := strings.Join([]string{
		"Add-Type -AssemblyName System.Windows.Forms",
		"$list = New-Object System.Collections.Specialized.StringCollection",
		strings.Join(items, "; "),
		"[System.Windows.Forms.Clipboard]::SetFileDropList($list)",
	}, "; ")
	cmd := exec.Command("powershell", "-NoProfile", "-STA", "-Command", script)
	output, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("写入文件剪贴板失败: %s", strings.TrimSpace(string(output)))
	}
	return nil
}

func escapePowerShellLiteral(value string) string {
	return strings.ReplaceAll(value, "'", "''")
}
