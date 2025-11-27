#!/usr/bin/env bash
set -euo pipefail

# Build fanqie frontend with a configurable base path and output dir.
# Usage: VITE_BASE=/fq/ OUT_DIR=/app/web/fq ./build_frontend.sh

ROOT_DIR=$(cd "$(dirname "$0")/../.." && pwd)
FRONTEND_DIR="$ROOT_DIR/novel-fanqie-reader/frontend"
OUT_DIR="${OUT_DIR:-/app/web/fq}"
VITE_BASE="${VITE_BASE:-/fq/}"
VITE_API_BASE="${VITE_API_BASE:-/fq-api}"
VITE_SOCKET_PATH="${VITE_SOCKET_PATH:-/fq-api/socket.io}"

mkdir -p "$OUT_DIR"

cd "$FRONTEND_DIR"

if command -v bun >/dev/null 2>&1; then
  bun install
  VITE_BASE="$VITE_BASE" VITE_API_BASE="$VITE_API_BASE" VITE_SOCKET_PATH="$VITE_SOCKET_PATH" bun run build --outDir "$OUT_DIR"
else
  npm install
  VITE_BASE="$VITE_BASE" VITE_API_BASE="$VITE_API_BASE" VITE_SOCKET_PATH="$VITE_SOCKET_PATH" npm run build -- --outDir "$OUT_DIR"
fi

echo "Frontend built to $OUT_DIR with base $VITE_BASE api $VITE_API_BASE socket $VITE_SOCKET_PATH"
