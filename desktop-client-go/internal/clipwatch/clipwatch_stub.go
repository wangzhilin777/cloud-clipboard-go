//go:build !windows

package clipwatch

import (
	"context"
	"log"
)

func start(_ context.Context, _ *log.Logger, _ Sink) {}
