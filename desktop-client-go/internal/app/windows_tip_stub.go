//go:build !windows

package app

import "errors"

func showWindowsTip(_ string, _ string, _ string, _ string, _ string, _ string, _ int, _ int, _ int) error {
	return errors.New("windows tip is only supported on windows")
}
