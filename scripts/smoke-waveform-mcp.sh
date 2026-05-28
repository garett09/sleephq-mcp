#!/usr/bin/env bash
# Automated waveform-window MCP smoke (see docs/smoke-test-waveform-windows.md).
# Requires ./run.sh (or server on SLEEPHQ_MCP_PORT) + .env SLEEPHQ_MCP_API_KEY.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
set -a && [ -f .env ] && . ./.env && set +a
TEST_DATE="${1:-2026-05-19}"
BASE="${SLEEPHQ_MCP_URL:-http://localhost:8080/mcp}"
ACCEPT='application/json, text/event-stream'
HDR_FILE="$(mktemp)"
trap 'rm -f "$HDR_FILE"' EXIT

mcp_tool_call() {
  local name="$1"
  local args="$2"
  curl -s --max-time 180 -X POST "$BASE" \
    -H 'Content-Type: application/json' -H "Accept: $ACCEPT" \
    -H "X-SleepHQ-MCP-Key: $SLEEPHQ_MCP_API_KEY" -H "Mcp-Session-Id: $SESSION" \
    -d "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/call\",\"params\":{\"name\":\"$name\",\"arguments\":$args}}"
}

curl -s -D "$HDR_FILE" -X POST "$BASE" \
  -H 'Content-Type: application/json' -H "Accept: $ACCEPT" \
  -H "X-SleepHQ-MCP-Key: ${SLEEPHQ_MCP_API_KEY:?set SLEEPHQ_MCP_API_KEY}" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"smoke-waveform-mcp","version":"1"}}}' \
  -o /dev/null

SESSION="$(awk -F': ' '/^[Mm]cp-[Ss]ession-[Ii]d:/ {print $2}' "$HDR_FILE" | tr -d '\r')"
[ -n "$SESSION" ] || { echo "FAIL: no Mcp-Session-Id from initialize"; exit 1; }

curl -s -X POST "$BASE" \
  -H 'Content-Type: application/json' -H "Accept: $ACCEPT" \
  -H "X-SleepHQ-MCP-Key: $SLEEPHQ_MCP_API_KEY" -H "Mcp-Session-Id: $SESSION" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized","params":{}}' -o /dev/null

export TEST_DATE
mcp_tool_call "get-waveform-by-date" "{\"date\":\"$TEST_DATE\",\"anchor\":\"auto\"}" \
| python3 -c "
import json, os, sys
TEST_DATE = os.environ['TEST_DATE']

def load_payload(raw):
    if not raw.lstrip().startswith('{'):
        for line in raw.splitlines():
            if line.startswith('data:'):
                raw = line[5:].strip()
                break
    outer = json.loads(raw)
    if outer.get('error'):
        print('FAIL tool error:', outer['error'])
        sys.exit(1)
    text = outer['result']['content'][0]['text']
    if text.lstrip().startswith('{'):
        return json.loads(text)
    raise ValueError('non-JSON tool result: ' + text[:200])

raw = sys.stdin.read()
body = load_payload(raw)
sel = body.get('window_selection') or {}
hints = body.get('mcp_payload_hints') or {}
expected_default = hints.get('waveform_default_max_minutes')
max_min = sel.get('max_minutes')
checks = [
    ('window_selection present', bool(sel)),
    ('mcp_payload_hints present', bool(hints)),
    ('anchor_resolved set', bool(sel.get('anchor_resolved'))),
    ('reason non-empty', bool(str(sel.get('reason', '')).strip())),
    ('channels present', isinstance(body.get('channels'), list)),
    ('max_minutes matches default',
     expected_default is None or max_min == expected_default),
    ('not spurious minute 0',
     sel.get('anchor_resolved') == 'manual' or sel.get('start_minute', -1) != 0),
]
print('SMOKE waveform auto TEST_DATE:', TEST_DATE)
for name, ok in checks:
    print(f\"  {'PASS' if ok else 'FAIL'} {name}\")
print('  anchor_resolved:', sel.get('anchor_resolved'))
print('  start_minute:', sel.get('start_minute'))
print('  window_selection.max_minutes:', max_min, 'expected:', expected_default)
if not all(ok for _, ok in checks):
    sys.exit(1)
"

mcp_tool_call "get-waveform-by-date" "{\"date\":\"$TEST_DATE\",\"anchor\":\"worst_leak\"}" \
| python3 -c "
import json, os, sys
TEST_DATE = os.environ['TEST_DATE']

def load_payload(raw):
    if not raw.lstrip().startswith('{'):
        for line in raw.splitlines():
            if line.startswith('data:'):
                raw = line[5:].strip()
                break
    outer = json.loads(raw)
    text = outer['result']['content'][0]['text']
    return json.loads(text)

body = load_payload(sys.stdin.read())
sel = body.get('window_selection') or {}
resolved = sel.get('anchor_resolved')
ok = resolved == 'worst_leak'
print('SMOKE waveform worst_leak TEST_DATE:', TEST_DATE)
print(f\"  {'PASS' if ok else 'FAIL'} anchor_resolved worst_leak (got {resolved!r})\")
if not ok:
    sys.exit(1)
"

mcp_tool_call "get-combined-night-by-date" "{\"date\":\"$TEST_DATE\"}" \
| python3 -c "
import json, os, sys
TEST_DATE = os.environ['TEST_DATE']

def load_payload(raw):
    if not raw.lstrip().startswith('{'):
        for line in raw.splitlines():
            if line.startswith('data:'):
                raw = line[5:].strip()
                break
    outer = json.loads(raw)
    text = outer['result']['content'][0]['text']
    return json.loads(text)

body = load_payload(sys.stdin.read())
journal = body.get('journal') or {}
summary = journal.get('sleep_stages_summary') or {}
reporting = summary.get('minutes_by_stage_for_reporting') or {}
schema_v = summary.get('summary_schema_version')
checks = [
    ('sleep_stages_summary', bool(summary)),
    ('summary_schema_version >= 3 (restart ./run.sh if missing)',
     schema_v is not None and int(schema_v) >= 3),
    ('overlap_detected bool', isinstance(summary.get('overlap_detected'), bool)),
    ('journal_stage_mismatch bool', isinstance(summary.get('journal_stage_mismatch'), bool)),
    ('minutes_by_stage_for_reporting', isinstance(reporting, dict) and bool(reporting)),
    ('reporting_source', bool(str(summary.get('reporting_source', '')).strip())),
]
print('SMOKE journal parity TEST_DATE:', TEST_DATE)
for name, ok in checks:
    print(f\"  {'PASS' if ok else 'FAIL'} {name}\")
if summary.get('journal_stage_mismatch') and not str(summary.get('ui_parity_note', '')).strip():
    print('  FAIL ui_parity_note when mismatch')
    sys.exit(1)
if not all(ok for _, ok in checks):
    sys.exit(1)
print('OVERALL: PASS')
"

echo "OVERALL: PASS (waveform + journal automated checks)"
