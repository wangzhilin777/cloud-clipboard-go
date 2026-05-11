//go:build !windows

package picker

import (
	"context"
	"errors"
)

func PickFiles(ctx context.Context) ([]string, error) {
	_ = ctx
	return nil, errors.New("当前平台暂未实现本地文件选择")
}
