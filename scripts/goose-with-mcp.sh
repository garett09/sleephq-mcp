#!/usr/bin/env bash
# Start MCP server, run Goose, then stop the server when Goose exits.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$SCRIPT_DIR"

if [ -f .env ]; then
  # shellcheck disable=SC1091
  set -a
  source .env
  set +a
fi

export PATH="${HOME}/.local/bin:${PATH}"

cleanup() {
  echo "" >&2
  echo "Stopping sleephq-mcp…" >&2
  "${SCRIPT_DIR}/stop.sh"
}
trap cleanup EXIT INT TERM

if command -v lsof >/dev/null 2>&1 && lsof -ti "tcp:${SLEEPHQ_MCP_PORT:-8080}" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Port ${SLEEPHQ_MCP_PORT:-8080} already in use — assuming sleephq-mcp is running." >&2
else
  echo "Starting sleephq-mcp (./run.sh)…" >&2
  "${SCRIPT_DIR}/run.sh" &
  server_pid=$!
  for _ in $(seq 1 90); do
    if curl -sf "http://localhost:${SLEEPHQ_MCP_PORT:-8080}/actuator/health" >/dev/null 2>&1; then
      break
    fi
    if ! kill -0 "${server_pid}" 2>/dev/null; then
      echo "error: run.sh exited before health check passed" >&2
      exit 1
    fi
    sleep 1
  done
  if ! curl -sf "http://localhost:${SLEEPHQ_MCP_PORT:-8080}/actuator/health" >/dev/null 2>&1; then
    echo "error: server did not become healthy within 90s" >&2
    exit 1
  fi
  echo "Classpath sanity expected in run.sh log; health OK." >&2
fi

if ! command -v goose >/dev/null 2>&1; then
  echo "error: goose not found (install or add ~/.local/bin to PATH)" >&2
  exit 1
fi

if [ "$#" -eq 0 ]; then
  exec goose session --recipe "${SCRIPT_DIR}/goose-recipe.yaml"
fi

exec goose "$@"
