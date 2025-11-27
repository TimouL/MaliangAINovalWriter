#!/usr/bin/env bash
set -euo pipefail

# Combined entrypoint: start fanqie backend (gunicorn) and main app.

MAIN_JAR=${MAIN_JAR:-/app/ainoval-server.jar}
FANQIE_ROOT=${FANQIE_ROOT:-/app/novel-fanqie-reader/backend}
FANQIE_BIND=${FANQIE_BIND:-0.0.0.0:5000}
FANQIE_WORKERS=${FANQIE_WORKERS:-1}

# SQLite defaults for fanqie backend
export DB_TYPE=${DB_TYPE:-sqlite}
export SQLITE_DB_FILE=${SQLITE_DB_FILE:-/data/fanqie.db}
mkdir -p "$(dirname "$SQLITE_DB_FILE")"

# Ensure data paths exist
export DATA_BASE_PATH=${DATA_BASE_PATH:-/data}
mkdir -p "$DATA_BASE_PATH"

echo "Starting fanqie backend (gunicorn) at $FANQIE_BIND with DB $DB_TYPE -> $SQLITE_DB_FILE"
cd "$FANQIE_ROOT"
gunicorn --bind "$FANQIE_BIND" --worker-class=eventlet --workers "$FANQIE_WORKERS" "app:app" &

echo "Starting main app from $MAIN_JAR"
exec java -jar "$MAIN_JAR"
