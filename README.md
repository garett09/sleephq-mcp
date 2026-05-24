# sleephq-mcp

A Spring AI MCP server (Streamable HTTP transport) that wraps the SleepHQ REST API and exposes it as MCP **tools**, **resources**, and **prompts** for an LLM client (e.g. Goose) to drive sleep therapy analysis and titration decisions.

## Architecture

```
mcp layer        @McpTool / @McpResource / @McpPrompt — thin facades (≤5 lines)
   ↓
service layer    NightService, WaveformService, ComparisonService
   ↓
client layer     SleepHqClient — one method per endpoint family
   ↓
transport        RestClient + AuthInterceptor (bearer header + 401 retry, in one place)
```

Single sources of truth for cross-cutting concerns: auth+retry lives in `AuthInterceptor`, errors in `McpResponses`, observability in `McpInvocationLoggingAspect`, caching via `@Cacheable("nightStats")`.

## MCP surface

- **14 tools** — who-am-i, get-token-status, list-teams, list-machines, get-machine-details, list-machine-dates, get-machine-date-by-date, get-night-stats, get-sessions, get-events, get-flow-rate-data, get-pressure-data, get-leak-data, get-spo2-data, get-pulse-rate-data, get-comparison, get-share-dashboard
- **7 resources** — `sleephq://patient/baseline`, `sleephq://device/current`, `sleephq://guidelines/resmed-titration`, `sleephq://reference/normal-ranges`, `sleephq://team/{id}`, `sleephq://machine/{id}`, `sleephq://machine_date/{id}`
- **7 prompts** — nightly-review, central-apnea-investigation, weekly-trend, leak-diagnosis, titration-decision, o2-desat-review, morning-brief

## Setup

```bash
cp .env.example .env
# edit .env with your SleepHQ OAuth credentials
./mvnw package
./run.sh
```

Server listens on `http://localhost:8080/mcp` (Streamable HTTP). Health at `/actuator/health`.

## Hook up Goose

The included `goose-recipe.yaml` points at `http://localhost:8080/mcp`. Start the MCP server, then:

```bash
goose session --recipe goose-recipe.yaml
```

## Tuning the doctor

- Edit `src/main/resources/clinical/*.md` to update patient baseline, device config, or clinical guidelines — picked up on the next request.
- Edit `src/main/resources/prompts/*.md` to refine analysis workflows.

## Adding capabilities

| To add a... | Steps |
|---|---|
| New tool | One method in `SleepHqClient`, one `@McpTool` method in the right `*Tools` class |
| New waveform channel | One enum constant in `WaveformChannel` + one 2-line tool method |
| New static resource | Drop a `.md` in `clinical/`, add one `@McpResource` method |
| New prompt | Drop a `.md` in `prompts/`, add one `@McpPrompt` method |

## Build verification

```bash
./mvnw test                     # unit tests
./mvnw package                  # full build
curl -sX POST localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"curl","version":"1"}}}'
```

## Troubleshooting

- **401 from tools** — check `SLEEPHQ_CLIENT_ID` / `SLEEPHQ_CLIENT_SECRET` env vars. `/actuator/health` will show `credentialsConfigured: false` if missing.
- **Waveform tool returns `"mode":"passthrough"`** — the SleepHQ response shape didn't match any known parser. Inspect the `raw` field and adjust `WaveformService.extractSamples()`.
- **MCP client can't connect** — confirm the server is on the right port (default 8080, override via `SLEEPHQ_MCP_PORT`). Streamable HTTP requires both `Content-Type: application/json` and `Accept: application/json, text/event-stream`.
