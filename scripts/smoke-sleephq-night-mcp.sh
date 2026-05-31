#!/usr/bin/env bash
# Automated get-sleephq-night MCP smoke (see docs/smoke-test-sleephq-night.md).
# Requires ./run.sh (or server on SLEEPHQ_MCP_PORT) + .env SLEEPHQ_MCP_API_KEY.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
set -a && [ -f .env ] && . ./.env && set +a
TEST_DATE="${1:-2026-05-28}"
BASE="${SLEEPHQ_MCP_URL:-http://localhost:8080/mcp}"
ACCEPT='application/json, text/event-stream'
HDR_FILE="$(mktemp)"
trap 'rm -f "$HDR_FILE"' EXIT

mcp_call() {
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
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"smoke-sleephq-night-mcp","version":"1"}}}' \
  -o /dev/null

SESSION="$(awk -F': ' '/^[Mm]cp-[Ss]ession-[Ii]d:/ {print $2}' "$HDR_FILE" | tr -d '\r')"
[ -n "$SESSION" ] || { echo "FAIL: no Mcp-Session-Id from initialize"; exit 1; }

curl -s -X POST "$BASE" \
  -H 'Content-Type: application/json' -H "Accept: $ACCEPT" \
  -H "X-SleepHQ-MCP-Key: $SLEEPHQ_MCP_API_KEY" -H "Mcp-Session-Id: $SESSION" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized","params":{}}' -o /dev/null

export TEST_DATE
mcp_call "get-sleephq-night" "{\"date\":\"$TEST_DATE\"}" \
| python3 -c "
import json, os, sys

TEST_DATE = os.environ['TEST_DATE']
REQUIRED_CHANNEL_KEYS = {
    'unit', 'p99', 'p95', 'median', 'min', 'max', 'avg', 'count'
}
PLD_FIELDS = {
    'mask_pressure', 'pressure', 'epap', 'leak_rate', 'resp_rate',
    'tidal_volume', 'minute_vent', 'snore', 'flow_limit',
}


def load_tool_json(raw):
    if not raw.lstrip().startswith('{'):
        for line in raw.splitlines():
            if line.startswith('data:'):
                raw = line[5:].strip()
                break
    outer = json.loads(raw)
    if outer.get('error'):
        print('FAIL RPC error:', outer['error'])
        sys.exit(1)
    text = outer['result']['content'][0]['text']
    if text.lstrip().startswith('{'):
        return json.loads(text)
    raise ValueError('non-JSON tool result: ' + text[:300])


def check_channel(ch):
    missing = REQUIRED_CHANNEL_KEYS - set(ch.keys())
    return len(missing) == 0, missing


raw = sys.stdin.read()
body = load_tool_json(raw)
checks = []

def add(name, ok, detail=''):
    checks.append((name, ok, detail))


add('source sleephq', body.get('source') == 'sleephq')
add('date matches', body.get('date') == TEST_DATE)
cov = body.get('coverage') or {}
add('coverage.cpap bool', isinstance(cov.get('cpap'), bool))
add('coverage.oximetry bool', isinstance(cov.get('oximetry'), bool))

cpap_ok = cov.get('cpap') is True
o2_ok = cov.get('oximetry') is True

if cpap_ok:
    ch = (body.get('cpap') or {}).get('channels') or {}
    add('cpap.channels present', bool(ch))
    press = ch.get('pressure')
    if press:
        ok, miss = check_channel(press)
        add('pressure stats complete', ok, str(miss) if miss else '')
        add('pressure has p99', isinstance(press.get('p99'), (int, float)))
    leak = ch.get('leak_rate')
    if leak:
        add('leak_rate unit L/min', leak.get('unit') == 'L/min')
    add('no cpap_reason when cpap true', 'cpap_reason' not in cov)
else:
    add('cpap block omitted', 'cpap' not in body)
    add('cpap_reason set', bool(cov.get('cpap_reason')))

if o2_ok:
    o2ch = (body.get('oximetry') or {}).get('channels') or {}
    add('oximetry.channels present', bool(o2ch))
else:
    add('oximetry block omitted', 'oximetry' not in body)

prov = body.get('provenance') or {}
if cpap_ok:
    src = prov.get('cpap_source')
    add('cpap_source local|api', src in ('local', 'sleephq_api'), src or 'missing')
    sessions = prov.get('cpap_sessions') or []
    if sessions:
        s0 = sessions[0]
        add('session uses filename', 'filename' in s0 and 'name' not in s0)
        if src == 'sleephq_api':
            add('api session has file_id', 'file_id' in s0)

add('no sleep_stages on tool', 'sleep_stages' not in body and 'journal' not in body)
add('no ahi on tool', 'ahi' not in body)

present_pld = set((body.get('cpap') or {}).get('channels') or {}).keys()) & PLD_FIELDS
if cpap_ok and present_pld:
    add('at least one PLD field', len(present_pld) >= 1, ','.join(sorted(present_pld)))

print('SMOKE get-sleephq-night TEST_DATE:', TEST_DATE)
for name, ok, detail in checks:
    line = f\"  {'PASS' if ok else 'FAIL'} {name}\"
    if detail:
        line += f' ({detail})'
    print(line)

if prov.get('cpap_source'):
    print('  INFO cpap_source:', prov.get('cpap_source'))
if cov.get('cpap_reason'):
    print('  INFO cpap_reason:', cov.get('cpap_reason'))
if cov.get('oximetry_reason'):
    print('  INFO oximetry_reason:', cov.get('oximetry_reason'))

if not all(ok for _, ok, _ in checks):
    sys.exit(1)
print('OVERALL: PASS')
"
