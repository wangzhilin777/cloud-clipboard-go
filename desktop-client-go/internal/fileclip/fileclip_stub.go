//go:build !windows && !linux && !darwin

package fileclip

import "fmt"

func setFileList(_ []string) error {
	return fmt.Errorf("当前平台暂不支持文件剪贴板写入")
}
