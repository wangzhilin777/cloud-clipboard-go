//go:build windows

package fileclip

import (
	"fmt"
	"os/exec"
	"strings"
)

func setFileList(paths []string) error {
	items := make([]string, 0, len(paths))
	expected := make([]string, 0, len(paths))
	for _, path := range paths {
		path = strings.TrimSpace(path)
		if path == "" {
			continue
		}
		items = append(items, fmt.Sprintf("$list.Add('%s') | Out-Null", escapePowerShellLiteral(path)))
		expected = append(expected, fmt.Sprintf("'%s'", escapePowerShellLiteral(path)))
	}
	if len(items) == 0 {
		return fmt.Errorf("没有可写入剪贴板的文件")
	}

	expectedList := "$expected = @(" + strings.Join(expected, ", ") + ")"
	script := strings.Join([]string{
		"$ErrorActionPreference='Stop'",
		"Add-Type -AssemblyName System.Windows.Forms",
		expectedList,
		"$list = New-Object System.Collections.Specialized.StringCollection",
		strings.Join(items, "; "),
		"$data = New-Object System.Windows.Forms.DataObject",
		"$data.SetFileDropList($list)",
		"try { [System.Windows.Forms.Clipboard]::SetDataObject($data, $true, 12, 150) } catch { " +
			"if ([System.Windows.Forms.Clipboard]::ContainsFileDropList()) { " +
			"$current = [System.Windows.Forms.Clipboard]::GetFileDropList(); " +
			"if ($current.Count -eq $expected.Count) { " +
			"$ok = $true; " +
			"for ($i = 0; $i -lt $expected.Count; $i++) { if ($current[$i] -ne $expected[$i]) { $ok = $false; break } }; " +
			"if ($ok) { exit 0 } } }; throw }",
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
