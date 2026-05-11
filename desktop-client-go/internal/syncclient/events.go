package syncclient

type EventHandler interface {
	OnConnecting()
	OnConnected()
	OnTrustedChanged(trusted bool)
	OnRemoteText(text string)
	OnPayloadNotice(kind string, title string)
	OnError(err error)
}

type noopEventHandler struct{}

func (noopEventHandler) OnConnecting()                 {}
func (noopEventHandler) OnConnected()                  {}
func (noopEventHandler) OnTrustedChanged(trusted bool) {}
func (noopEventHandler) OnRemoteText(text string)      {}
func (noopEventHandler) OnPayloadNotice(kind, title string) {
}
func (noopEventHandler) OnError(err error) {}
