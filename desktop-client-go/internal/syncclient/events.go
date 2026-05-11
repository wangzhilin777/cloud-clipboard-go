package syncclient

import "time"

var ErrReconnectStopped = reconnectStoppedError{}

type EventHandler interface {
	OnConnecting()
	OnConnected()
	OnTrustedChanged(trusted bool)
	OnRemoteText(text string)
	OnPayloadNotice(kind string, title string)
	OnRetrying(attempt int, maxAttempts int, delay time.Duration, err error)
	OnReconnectStopped(lastErr error)
	OnError(err error)
}

type noopEventHandler struct{}

func (noopEventHandler) OnConnecting()                 {}
func (noopEventHandler) OnConnected()                  {}
func (noopEventHandler) OnTrustedChanged(trusted bool) {}
func (noopEventHandler) OnRemoteText(text string)      {}
func (noopEventHandler) OnPayloadNotice(kind, title string) {
}
func (noopEventHandler) OnRetrying(attempt int, maxAttempts int, delay time.Duration, err error) {
}
func (noopEventHandler) OnReconnectStopped(lastErr error) {}
func (noopEventHandler) OnError(err error)                {}

type reconnectStoppedError struct{}

func (reconnectStoppedError) Error() string {
	return "同步客户端已停止自动重连"
}
