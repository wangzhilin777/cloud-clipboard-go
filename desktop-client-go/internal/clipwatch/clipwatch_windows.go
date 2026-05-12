//go:build windows

package clipwatch

import (
	"context"
	"log"
	"strings"
	"time"
	"unsafe"

	"golang.org/x/sys/windows"
)

const cfHDrop = 15

var (
	user32                = windows.NewLazySystemDLL("user32.dll")
	shell32               = windows.NewLazySystemDLL("shell32.dll")
	procOpenClipboard     = user32.NewProc("OpenClipboard")
	procCloseClipboard    = user32.NewProc("CloseClipboard")
	procIsFormatAvailable = user32.NewProc("IsClipboardFormatAvailable")
	procGetClipboardData  = user32.NewProc("GetClipboardData")
	procDragQueryFileW    = shell32.NewProc("DragQueryFileW")
)

func start(ctx context.Context, logger *log.Logger, sink Sink) {
	if sink == nil {
		return
	}
	go func() {
		ticker := time.NewTicker(700 * time.Millisecond)
		defer ticker.Stop()
		lastSignature := ""
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				paths, err := readClipboardFiles()
				if err != nil {
					continue
				}
				if len(paths) == 0 {
					lastSignature = ""
					continue
				}
				signature := strings.Join(paths, "\n")
				if signature == lastSignature {
					continue
				}
				lastSignature = signature
				logger.Printf("检测到新的剪贴板文件列表: %d 项", len(paths))
				sink.OnClipboardFiles(paths)
			}
		}
	}()
}

func readClipboardFiles() ([]string, error) {
	ok, _, _ := procOpenClipboard.Call(0)
	if ok == 0 {
		return nil, windows.ERROR_ACCESS_DENIED
	}
	defer procCloseClipboard.Call()
	available, _, _ := procIsFormatAvailable.Call(cfHDrop)
	if available == 0 {
		return nil, nil
	}
	handle, _, err := procGetClipboardData.Call(cfHDrop)
	if handle == 0 {
		return nil, err
	}
	count, _, _ := procDragQueryFileW.Call(handle, 0xFFFFFFFF, 0, 0)
	if count == 0 {
		return nil, nil
	}
	results := make([]string, 0, count)
	for i := uint32(0); i < uint32(count); i++ {
		length, _, _ := procDragQueryFileW.Call(handle, uintptr(i), 0, 0)
		if length == 0 {
			continue
		}
		buffer := make([]uint16, length+1)
		procDragQueryFileW.Call(handle, uintptr(i), uintptr(unsafe.Pointer(&buffer[0])), uintptr(len(buffer)))
		path := windows.UTF16ToString(buffer)
		path = strings.TrimSpace(path)
		if path != "" {
			results = append(results, path)
		}
	}
	return results, nil
}
