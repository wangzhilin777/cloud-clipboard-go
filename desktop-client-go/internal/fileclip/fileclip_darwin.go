//go:build darwin

package fileclip

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

func setFileList(paths []string) error {
	items := make([]string, 0, len(paths))
	for _, path := range paths {
		path = strings.TrimSpace(path)
		if path == "" {
			continue
		}
		abs, err := filepath.Abs(path)
		if err != nil {
			return fmt.Errorf("解析文件路径失败: %w", err)
		}
		if _, err := os.Stat(abs); err != nil {
			return fmt.Errorf("文件不可访问: %s", abs)
		}
		items = append(items, fmt.Sprintf("POSIX file %q", abs))
	}
	if len(items) == 0 {
		return fmt.Errorf("没有可写入剪贴板的文件")
	}

	script := fmt.Sprintf("set the clipboard to {%s}", strings.Join(items, ", "))
	output, err := exec.Command("osascript", "-e", script).CombinedOutput()
	if err != nil {
		return fmt.Errorf("写入文件剪贴板失败: %s", strings.TrimSpace(string(output)))
	}
	return nil
}
