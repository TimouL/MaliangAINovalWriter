#!/usr/bin/env bash
set -euo pipefail

# Simplified entrypoint: start main Java app only
# Fanqie novel service now uses direct third-party API (no local Python service needed)

MAIN_JAR=${MAIN_JAR:-/app/ainoval-server.jar}

# Ensure data paths exist
export DATA_BASE_PATH=${DATA_BASE_PATH:-/data}
mkdir -p "$DATA_BASE_PATH"

echo "Starting main app from $MAIN_JAR"
exec java -jar "$MAIN_JAR"
