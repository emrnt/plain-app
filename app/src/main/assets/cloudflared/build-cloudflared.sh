#!/bin/bash
# Build cloudflared for Android and copy to assets/
# Requires: Go 1.21+, Android NDK (optional for CGO, but cloudflared is pure Go)
#
# Usage: cd app/src/main/assets/cloudflared && ./build-cloudflared.sh

set -euo pipefail

VERSION="2024.12.2"
REPO="https://github.com/cloudflare/cloudflared"

echo "==> Cloning cloudflared v$VERSION..."
if [ ! -d "cloudflared-src" ]; then
  git clone --depth 1 --branch "$VERSION" "$REPO" cloudflared-src
fi

cd cloudflared-src

for TARGET in android/arm64 android/arm android/amd64 android/386; do
  OS="${TARGET%%/*}"
  ARCH="${TARGET##*/}"

  OUTPUT_NAME="cloudflared-$ARCH"
  if [ "$ARCH" = "arm" ]; then
    GOARM=7
  else
    GOARM=""
  fi

  echo "==> Building for $OS/$ARCH (GOARM=$GOARM)..."
  GOOS="$OS" GOARCH="$ARCH" GOARM="$GOARM" CGO_ENABLED=0 go build \
    -ldflags="-s -w -X main.Version=$VERSION" \
    -o "../$OUTPUT_NAME" ./cmd/cloudflared

  echo "    -> $OUTPUT_NAME ($(ls -lh "../$OUTPUT_NAME" | awk '{print $5}'))"
done

cd ..
echo ""
echo "==> Done. Binaries in assets/cloudflared/:"
ls -lh cloudflared-*
echo ""
echo "==> Add to .gitignore:"
echo "app/src/main/assets/cloudflared/cloudflared-*"
echo "app/src/main/assets/cloudflared/cloudflared-src/"
