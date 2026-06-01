//go:build darwin

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
set pickedFiles to choose file with prompt "选择要发送到 Cloud Clipboard 的文件" with multiple selections allowed
set output to ""
repeat with pickedFile in pickedFiles
  set output to output & POSIX path of pickedFile & linefeed
end repeat
return output
`
	cmd := exec.CommandContext(ctx, "osascript", "-e", script)
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
