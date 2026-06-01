//go:build linux

package fileclip

import (
	"bytes"
	"fmt"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

func setFileList(paths []string) error {
	body, err := uriList(paths)
	if err != nil {
		return err
	}

	if _, err := exec.LookPath("wl-copy"); err == nil {
		return runClipboardCommand(body, "wl-copy", "--type", "text/uri-list")
	}
	if _, err := exec.LookPath("xclip"); err == nil {
		return runClipboardCommand(body, "xclip", "-selection", "clipboard", "-t", "text/uri-list")
	}

	return fmt.Errorf("当前 Linux 环境未找到 wl-copy 或 xclip，无法写入文件剪贴板")
}

func uriList(paths []string) ([]byte, error) {
	items := make([]string, 0, len(paths))
	for _, path := range paths {
		path = strings.TrimSpace(path)
		if path == "" {
			continue
		}
		abs, err := filepath.Abs(path)
		if err != nil {
			return nil, fmt.Errorf("解析文件路径失败: %w", err)
		}
		if _, err := os.Stat(abs); err != nil {
			return nil, fmt.Errorf("文件不可访问: %s", abs)
		}
		items = append(items, (&url.URL{Scheme: "file", Path: filepath.ToSlash(abs)}).String())
	}
	if len(items) == 0 {
		return nil, fmt.Errorf("没有可写入剪贴板的文件")
	}
	return []byte(strings.Join(items, "\r\n") + "\r\n"), nil
}

func runClipboardCommand(input []byte, name string, args ...string) error {
	cmd := exec.Command(name, args...)
	cmd.Stdin = bytes.NewReader(input)
	output, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("写入文件剪贴板失败: %s", strings.TrimSpace(string(output)))
	}
	return nil
}
