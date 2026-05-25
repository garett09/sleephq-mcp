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

Outbound URLs are composed only from paths in [https://sleephq.com/api/swagger.json](https://sleephq.com/api/swagger.json) (`https://sleephq.com` + `/api` + `/v1/...`). Single sources of truth for cross-cutting concerns: auth+retry lives in `AuthInterceptor`, errors in `McpResponses`, observability in `McpInvocationLoggingAspect`, caching via `@Cacheable("nightStats")`, MCP HTTP access in `McpApiKeyAuthFilter`, and path validation in `SleepHqPathParams` / `SleepHqClient` URI templates.

## Security

- **MCP endpoint** (`/mcp`): By default requires header `X-SleepHQ-MCP-Key` matching `SLEEPHQ_MCP_API_KEY`. For trusted localhost only, you can set `SLEEPHQ_MCP_ALLOW_ANONYMOUS=true` (disables this check; not for shared networks).
- **SleepHQ OAuth**: Token scope defaults to `read` (`SLEEPHQ_OAUTH_SCOPE`). Increase only if the API returns 403 for your use case.
- **Actuator health**: Details are shown only to authorized requests (`management.endpoint.health.show-details=when_authorized`); unauthenticated `GET /actuator/health` returns status only.

## MCP surface

- **27 tools** — Auth, machines, night (`get-night-stats`, `get-combined-night-by-date` with journal wellness overlay), team data, imports/files, journals (`get-journal`, `get-journal-by-date`), waveform, O2, `get-comparison`. See [sleephq-mcp-capabilities.md](sleephq-mcp-capabilities.md).
- **OpenAPI contract** — [docs/sleephq-openapi-gap.md](docs/sleephq-openapi-gap.md) (how `get-comparison` / `get-combined-night-by-date` relate to documented routes).

## Setup

```bash
cp .env.example .env
# edit .env: SleepHQ OAuth credentials, SLEEPHQ_MCP_API_KEY (or SLEEPHQ_MCP_ALLOW_ANONYMOUS=true for local-only)
./run.sh
```

`./run.sh` stops any old JVM on port 8080, rebuilds, copies the jar to `dist/`, and starts a single fresh process (avoids `NoClassDefFoundError` from stale servers). Wait for log line `Classpath sanity OK` before connecting Goose.

**CPAP clock drift:** Date-based EDF tools (`get-device-events`, `get-waveform-by-date`, `scan-apnea-events` with `date`) read `time_offset` from the CPAP `machine_date` API. Optional env fallback: `SLEEPHQ_CPAP_CLOCK_ADJUST_SECONDS`. O2 and journal are never shifted. See `sleephq://playbook/clock-alignment`.

Server listens on `http://localhost:8080/mcp` (Streamable HTTP). Health at `/actuator/health`.

### Performance tuning

| Property | Default | Purpose |
|----------|---------|---------|
| `sleephq.fetch.parallelism` | `8` | Parallel per-day fetches in `get-comparison`; parallel CPAP+O2 per night |
| `sleephq.cache.enabled` | `true` | Set `false` to force live SleepHQ on every call |
| `sleephq.cache.historical-ttl` | `6h` | Cache past calendar dates (today is never cached) |
| `sleephq.observability.phase-timing` | `false` | Debug logs: `journal_ms`, `fetch_ms`, `download_ms`, `parse_ms` |

## Hook up Goose

The included `goose-recipe.yaml` points at `http://localhost:8080/mcp` and sends `X-SleepHQ-MCP-Key` from `SLEEPHQ_MCP_API_KEY`. Export that variable (same value as in `.env` for the server) before starting Goose, unless the server runs with `SLEEPHQ_MCP_ALLOW_ANONYMOUS=true`. Then:

```bash
goose session --recipe goose-recipe.yaml
```

The recipe’s opening **message** activity and [sleephq-mcp-capabilities.md](sleephq-mcp-capabilities.md) list every tool with its backing endpoint.

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
- **404 from `get-machine-date-by-date` / `get-combined-night-by-date`** — usually no `machine_date` for that machine and calendar date; confirm with `list-machine-dates`. Not the same as a missing API route.
- **MCP client can't connect** — confirm the server is on the right port (default 8080, override via `SLEEPHQ_MCP_PORT`). Streamable HTTP requires both `Content-Type: application/json` and `Accept: application/json, text/event-stream`.
