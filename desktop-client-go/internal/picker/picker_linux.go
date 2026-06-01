//go:build linux

package picker

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"os/exec"
	"strings"
)

func PickFiles(ctx context.Context) ([]string, error) {
	commands := [][]string{
		{"zenity", "--file-selection", "--multiple", "--separator=\n", "--title=选择要发送到 Cloud Clipboard 的文件"},
		{"kdialog", "--multiple", "--separate-output", "--getopenfilename", "."},
		{"yad", "--file-selection", "--multiple", "--separator=\n", "--title=选择要发送到 Cloud Clipboard 的文件"},
	}
	for _, candidate := range commands {
		if _, err := exec.LookPath(candidate[0]); err != nil {
			continue
		}
		paths, err := runPicker(ctx, candidate[0], candidate[1:]...)
		if err != nil {
			return nil, err
		}
		return paths, nil
	}
	return nil, errors.New("当前 Linux 环境未找到 zenity、kdialog 或 yad，无法打开文件选择框")
}

func runPicker(ctx context.Context, name string, args ...string) ([]string, error) {
	cmd := exec.CommandContext(ctx, name, args...)
	var stdout bytes.Buffer
	var stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	if err := cmd.Run(); err != nil {
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}
		return nil, fmt.Errorf("打开文件选择框失败: %w %s", err, strings.TrimSpace(stderr.String()))
	}
	return splitPickerOutput(stdout.String()), nil
}

func splitPickerOutput(output string) []string {
	lines := strings.Split(strings.ReplaceAll(output, "\r\n", "\n"), "\n")
	results := make([]string, 0, len(lines))
	for _, line := range lines {
		line = strings.TrimSpace(line)
		if line != "" {
			results = append(results, line)
		}
	}
	return results
}
