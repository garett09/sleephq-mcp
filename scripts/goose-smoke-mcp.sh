#!/usr/bin/env bash
# Goose CLI — full MCP smoke (see context/goose-smoke-mcp.txt).
# Desktop: paste context/goose-smoke-mcp.txt into a new chat instead.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROMPT_FILE="${ROOT}/context/goose-smoke-mcp.txt"
WAVEFORM_DATE="${WAVEFORM_DATE:-2026-05-19}"
OSCAR_DATE="${OSCAR_DATE:-2026-05-21}"

if [ ! -f "$PROMPT_FILE" ]; then
  echo "error: missing $PROMPT_FILE" >&2
  exit 1
fi

PROMPT="$(sed -e "s/WAVEFORM_DATE = 2026-05-19/WAVEFORM_DATE = ${WAVEFORM_DATE}/" \
  -e "s/else 2026-05-21/else ${OSCAR_DATE}/" \
  "$PROMPT_FILE")"

exec "${ROOT}/scripts/goose-with-mcp.sh" run -t "$PROMPT"
