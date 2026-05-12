//go:build !windows

package app

import "errors"

func showWindowsTip(_ string, _ string, _ string, _ string, _ string, _ string, _ int, _ int, _ int, _ string, _ int, _ int, _ string) error {
	return errors.New("windows tip is only supported on windows")
}

func closeWindowsTip(_ string) error {
	return nil
}
