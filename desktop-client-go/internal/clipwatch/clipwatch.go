package clipwatch

import (
	"context"
	"log"
)

type Sink interface {
	OnClipboardFiles(paths []string)
}

func Start(ctx context.Context, logger *log.Logger, sink Sink) {
	start(ctx, logger, sink)
}
