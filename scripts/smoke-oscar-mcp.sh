#!/usr/bin/env bash
# Automated OSCAR MCP smoke (see docs/smoke-test-oscar-mcp.md). Requires ./run.sh + .env SLEEPHQ_MCP_API_KEY.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
set -a && [ -f .env ] && . ./.env && set +a
TEST_DATE="${1:-2026-05-21}"
BASE="${SLEEPHQ_MCP_URL:-http://localhost:8080/mcp}"
ACCEPT='application/json, text/event-stream'
HDR_FILE="$(mktemp)"
trap 'rm -f "$HDR_FILE"' EXIT

curl -s -D "$HDR_FILE" -X POST "$BASE" \
  -H 'Content-Type: application/json' -H "Accept: $ACCEPT" \
  -H "X-SleepHQ-MCP-Key: ${SLEEPHQ_MCP_API_KEY:?set SLEEPHQ_MCP_API_KEY}" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"smoke-oscar-mcp","version":"1"}}}' \
  -o /dev/null

SESSION="$(awk -F': ' '/^[Mm]cp-[Ss]ession-[Ii]d:/ {print $2}' "$HDR_FILE" | tr -d '\r')"
[ -n "$SESSION" ] || { echo "FAIL: no Mcp-Session-Id from initialize"; exit 1; }

curl -s -X POST "$BASE" \
  -H 'Content-Type: application/json' -H "Accept: $ACCEPT" \
  -H "X-SleepHQ-MCP-Key: $SLEEPHQ_MCP_API_KEY" -H "Mcp-Session-Id: $SESSION" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized","params":{}}' -o /dev/null

curl -s --max-time 180 -X POST "$BASE" \
  -H 'Content-Type: application/json' -H "Accept: $ACCEPT" \
  -H "X-SleepHQ-MCP-Key: $SLEEPHQ_MCP_API_KEY" -H "Mcp-Session-Id: $SESSION" \
  -d "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"get-night-analysis\",\"arguments\":{\"date\":\"$TEST_DATE\"}}}" \
| python3 -c "
import json, sys
raw = sys.stdin.read()
if not raw.lstrip().startswith('{'):
    for line in raw.splitlines():
        if line.startswith('data:'):
            raw = line[5:].strip()
            break
outer = json.loads(raw)
night = json.loads(outer['result']['content'][0]['text'])
ev = night.get('events', {})
counts = ev.get('counts', {})
summary = ev.get('summary_counts', {})
subset = all(summary.get(k) == v for k, v in counts.items())
auth = ev.get('event_count_authority')
checks = [
    ('canonical subset', subset),
    ('authority oscar_summary_000', auth == 'oscar_summary_000'),
    ('event_counts_agree', ev.get('event_counts_agree') is True),
    ('no calendar_date', 'calendar_date' not in night),
]
print('SMOKE TEST_DATE:', night.get('date', '$TEST_DATE'))
for name, ok in checks:
    print(f\"  {'PASS' if ok else 'FAIL'} {name}\")
if not all(ok for _, ok in checks):
    print('counts', counts)
    print('summary_counts', summary)
    sys.exit(1)
print('OVERALL: PASS')
"
