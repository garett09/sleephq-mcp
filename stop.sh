#!/usr/bin/env bash
# Stop the sleephq-mcp JVM listening on SLEEPHQ_MCP_PORT (default 8080).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

readonly ARTIFACT_JAR="sleephq-mcp-0.0.1-SNAPSHOT.jar"
readonly PORT="${SLEEPHQ_MCP_PORT:-8080}"
readonly STOP_TIMEOUT_SEC=15

stop_server() {
  echo "Stopping sleephq-mcp on port ${PORT}…" >&2

  if command -v pgrep >/dev/null 2>&1; then
    local pids
    pids="$(pgrep -f "${ARTIFACT_JAR}" 2>/dev/null || true)"
    if [ -n "${pids}" ]; then
      echo "  SIGTERM jar PIDs: ${pids}" >&2
      # shellcheck disable=SC2086
      kill ${pids} 2>/dev/null || true
    fi
  fi

  if command -v lsof >/dev/null 2>&1; then
    local i=0
    while [ "${i}" -lt "${STOP_TIMEOUT_SEC}" ]; do
      local listen_pids
      listen_pids="$(lsof -ti "tcp:${PORT}" -sTCP:LISTEN 2>/dev/null || true)"
      if [ -z "${listen_pids}" ]; then
        echo "Port ${PORT} is free." >&2
        return 0
      fi
      if [ "${i}" -eq 0 ]; then
        echo "  SIGTERM port ${PORT} PIDs: ${listen_pids}" >&2
      fi
      # shellcheck disable=SC2086
      kill ${listen_pids} 2>/dev/null || true
      sleep 1
      i=$((i + 1))
    done

    listen_pids="$(lsof -ti "tcp:${PORT}" -sTCP:LISTEN 2>/dev/null || true)"
    if [ -n "${listen_pids}" ]; then
      echo "  SIGKILL port ${PORT} PIDs: ${listen_pids}" >&2
      # shellcheck disable=SC2086
      kill -9 ${listen_pids} 2>/dev/null || true
      sleep 1
    fi

    if lsof -ti "tcp:${PORT}" -sTCP:LISTEN >/dev/null 2>&1; then
      echo "error: port ${PORT} still in use" >&2
      exit 1
    fi
    echo "Port ${PORT} is free." >&2
    return 0
  fi

  echo "warning: lsof not found; sent SIGTERM to jar PIDs only" >&2
}

stop_server
