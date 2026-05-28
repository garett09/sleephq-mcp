#!/usr/bin/env bash
# Goose CLI only — Desktop users: paste context/goose-smoke-waveform.txt (see docs/smoke-test-waveform-windows.md).
# Starts MCP if needed, runs goose run -t, stops on exit.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROMPT_FILE="${ROOT}/context/goose-smoke-waveform.txt"
TEST_DATE="${TEST_DATE:-2026-05-19}"

if [ ! -f "$PROMPT_FILE" ]; then
  echo "error: missing $PROMPT_FILE" >&2
  exit 1
fi

PROMPT="$(sed "s/2026-05-19/${TEST_DATE}/g" "$PROMPT_FILE")"

exec "${ROOT}/scripts/goose-with-mcp.sh" run -t "$PROMPT"
