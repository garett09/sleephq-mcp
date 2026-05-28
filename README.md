# sleephq-mcp

A Spring AI MCP server (Streamable HTTP transport) that wraps the SleepHQ REST API and exposes it as MCP **tools** for an LLM client (e.g. Goose) to drive sleep therapy analysis and titration decisions.

## Architecture

```
mcp layer        @McpTool — thin facades (≤5 lines)
   ↓
service layer    NightService, CombinedNightService, ComparisonService
   ↓
client layer     SleepHqClient — one method per documented endpoint family
   ↓
transport        RestClient + AuthInterceptor (bearer header + 401 retry, in one place)
```

Outbound URLs are composed only from paths in [https://sleephq.com/api/swagger.json](https://sleephq.com/api/swagger.json) (`https://sleephq.com` + `/api` + `/v1/...`). Single sources of truth for cross-cutting concerns: auth+retry lives in `AuthInterceptor`, errors in `McpResponses`, observability in `McpInvocationLoggingAspect`, MCP HTTP access in `McpApiKeyAuthFilter`, and path validation in `SleepHqPathParams` / `SleepHqClient` URI templates.

## Security

- **MCP endpoint** (`/mcp`): By default requires header `X-SleepHQ-MCP-Key` matching `SLEEPHQ_MCP_API_KEY`. For trusted localhost only, you can set `SLEEPHQ_MCP_ALLOW_ANONYMOUS=true` (disables this check; not for shared networks).
- **SleepHQ OAuth**: Token scope defaults to `read` (`SLEEPHQ_OAUTH_SCOPE`). Increase only if the API returns 403 for your use case.
- **Actuator health**: Details are shown only to authorized requests (`management.endpoint.health.show-details=when_authorized`); unauthenticated `GET /actuator/health` returns status only.

## MCP surface

- **Tools** — Auth, machines, night (`get-night-stats`, `get-combined-night-by-date` with journal + optional OSCAR `night_analysis`), team data, imports/files, journals, waveform, O2, `get-comparison`, OSCAR (`get-oscar-status`, `get-night-analysis`, `get-mechanics`, `get-oscar-trend`, `get-oscar-events`, `get-plmd-night`). See [sleephq-mcp-capabilities.md](sleephq-mcp-capabilities.md).
- **OSCAR integration background** → [`context/`](context/README.md) (local ResMed backup + server-side night analysis).
- **OpenAPI contract** — [docs/sleephq-openapi-gap.md](docs/sleephq-openapi-gap.md) (how `get-comparison` / `get-combined-night-by-date` relate to documented routes).

## Setup

```bash
cp .env.example .env
# edit .env: SleepHQ OAuth credentials, SLEEPHQ_MCP_API_KEY (or SLEEPHQ_MCP_ALLOW_ANONYMOUS=true for local-only)
./run.sh
```

`./run.sh` stops any old JVM on port 8080, rebuilds, copies the jar to `dist/`, and starts a single fresh process (avoids `NoClassDefFoundError` from stale servers). Wait for log line `Classpath sanity OK` before connecting Goose.

**Stop when done:** `./stop.sh` frees port 8080 (the JVM keeps running until stopped).

**CPAP clock drift:** Date-based EDF tools (`get-device-events`, `get-waveform-by-date`, `scan-apnea-events` with `date`) read `time_offset` from the CPAP `machine_date` API. Optional env fallback: `SLEEPHQ_CPAP_CLOCK_ADJUST_SECONDS`. O2 and journal are never shifted. See `sleephq://playbook/clock-alignment`.

Server listens on `http://localhost:8080/mcp` (Streamable HTTP). Health at `/actuator/health`.

## Hook up Goose

**Goose Desktop:** `./run.sh` in a terminal, then in the app add a Streamable HTTP extension → `http://localhost:8080/mcp` with header `X-SleepHQ-MCP-Key` = your `SLEEPHQ_MCP_API_KEY` from `.env`. Enable it in a new chat. When done: `./stop.sh`.

**Waveform smoke (Desktop):** paste [`context/goose-smoke-waveform.txt`](context/goose-smoke-waveform.txt) into a chat — see [docs/smoke-test-waveform-windows.md](docs/smoke-test-waveform-windows.md).

**Goose CLI (optional):** `goose-recipe.yaml` uses the same URL/headers. `goose session --recipe goose-recipe.yaml` or `./scripts/goose-with-mcp.sh session --recipe goose-recipe.yaml`, then `./stop.sh`.

[sleephq-mcp-capabilities.md](sleephq-mcp-capabilities.md) lists every tool. **More smoke tests:** [waveform windows](docs/smoke-test-waveform-windows.md) · [OSCAR](docs/smoke-test-oscar-mcp.md)

## Adding capabilities

| To add a... | Steps |
|---|---|
| New tool | Add a path-backed method in `SleepHqClient` only if it appears in `swagger.json`, then one `@McpTool` method in the right `*Tools` class |

## Build verification

```bash
./mvnw test                     # unit tests
./mvnw package                  # full build
curl -sX POST localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'X-SleepHQ-MCP-Key: YOUR_MCP_API_KEY' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"curl","version":"1"}}}'
```

If the server uses `SLEEPHQ_MCP_ALLOW_ANONYMOUS=true`, omit the `X-SleepHQ-MCP-Key` header for local checks.

## Troubleshooting

- **401 from `/mcp` (before tools run)** — send header `X-SleepHQ-MCP-Key` matching `SLEEPHQ_MCP_API_KEY`, or set `SLEEPHQ_MCP_ALLOW_ANONYMOUS=true` only on trusted localhost.
- **401 from SleepHQ tools** — check `SLEEPHQ_CLIENT_ID` / `SLEEPHQ_CLIENT_SECRET`. With anonymous health, `/actuator/health` returns only UP/DOWN; configure actuator authentication if you need credential details on that endpoint.
- **404 / empty from `get-combined-night-by-date`** — without Magic Uploader, CPAP `machine_date` may be absent; the tool still returns O2 summaries and/or `journal` when configured (`coverage.cpap_machine_date` / `o2_machine_date` / `journal`). Use `get-journal-by-date` for sleep stages only. Fatal only when CPAP, O2, and journal are all missing for that date.
- **MCP client can't connect** — confirm the server is on the right port (default 8080, override via `SLEEPHQ_MCP_PORT`). Streamable HTTP requires both `Content-Type: application/json` and `Accept: application/json, text/event-stream`.
