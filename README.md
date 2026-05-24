# sleephq-mcp

A Spring AI MCP server (Streamable HTTP transport) that wraps the SleepHQ REST API and exposes it as MCP **tools**, **resources**, and **prompts** for an LLM client (e.g. Goose) to drive sleep therapy analysis and titration decisions.

## Architecture

```
mcp layer        @McpTool / @McpResource / @McpPrompt — thin facades (≤5 lines)
   ↓
service layer    NightService, WaveformService, CorrelationWaveformService, ComparisonService
   ↓
client layer     SleepHqClient — one method per endpoint family
   ↓
transport        RestClient + AuthInterceptor (bearer header + 401 retry, in one place)
```

Single sources of truth for cross-cutting concerns: auth+retry lives in `AuthInterceptor`, errors in `McpResponses`, observability in `McpInvocationLoggingAspect`, caching via `@Cacheable("nightStats")`, MCP HTTP access in `McpApiKeyAuthFilter`, and path validation in `SleepHqPathParams` / `SleepHqClient` URI templates.

## Security

- **MCP endpoint** (`/mcp`): By default requires header `X-SleepHQ-MCP-Key` matching `SLEEPHQ_MCP_API_KEY`. For trusted localhost only, you can set `SLEEPHQ_MCP_ALLOW_ANONYMOUS=true` (disables this check; not for shared networks).
- **SleepHQ OAuth**: Token scope defaults to `read` (`SLEEPHQ_OAUTH_SCOPE`). Increase only if the API returns 403 for your use case.
- **Actuator health**: Details are shown only to authorized requests (`management.endpoint.health.show-details=when_authorized`); unauthenticated `GET /actuator/health` returns status only.

## MCP surface

- **23 tools** — Grouped: auth (2), machines (5), night (3), waveforms (7 single-channel + `get-correlation-window`), team lists (4), other (2). **Canonical names and parameters:** use your MCP client’s `list_tools` against this server (the list changes with releases).
- **Correlation cost** — `get-correlation-window` issues **one full-night waveform HTTP GET per distinct channel** (same backend path as the single-channel waveform tools), then slices in memory to your window; prefer fewer channels when latency matters.
- **Resources** — 4 static URIs (`sleephq://patient/baseline`, `sleephq://device/current`, `sleephq://guidelines/resmed-titration`, `sleephq://reference/normal-ranges`) plus 3 URI templates (`sleephq://team/{id}`, `sleephq://machine/{id}`, `sleephq://machine_date/{id}`).
- **7 prompts** — nightly-review, central-apnea-investigation, weekly-trend, leak-diagnosis, titration-decision, o2-desat-review, morning-brief
- **Waveform API reference** — [docs/sleephq-waveform-segments.md](docs/sleephq-waveform-segments.md) (known `*_data` path segments and how to probe new ones).

## Setup

```bash
cp .env.example .env
# edit .env: SleepHQ OAuth credentials, SLEEPHQ_MCP_API_KEY (or SLEEPHQ_MCP_ALLOW_ANONYMOUS=true for local-only)
./mvnw package
./run.sh
```

Server listens on `http://localhost:8080/mcp` (Streamable HTTP). Health at `/actuator/health`.

## Hook up Goose

The included `goose-recipe.yaml` points at `http://localhost:8080/mcp` and sends `X-SleepHQ-MCP-Key` from `SLEEPHQ_MCP_API_KEY`. Export that variable (same value as in `.env` for the server) before starting Goose, unless the server runs with `SLEEPHQ_MCP_ALLOW_ANONYMOUS=true`. Then:

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
  -H 'X-SleepHQ-MCP-Key: YOUR_MCP_API_KEY' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"curl","version":"1"}}}'
```

If the server uses `SLEEPHQ_MCP_ALLOW_ANONYMOUS=true`, omit the `X-SleepHQ-MCP-Key` header for local checks.

## Troubleshooting

- **401 from `/mcp` (before tools run)** — send header `X-SleepHQ-MCP-Key` matching `SLEEPHQ_MCP_API_KEY`, or set `SLEEPHQ_MCP_ALLOW_ANONYMOUS=true` only on trusted localhost.
- **401 from SleepHQ tools** — check `SLEEPHQ_CLIENT_ID` / `SLEEPHQ_CLIENT_SECRET`. With anonymous health, `/actuator/health` returns only UP/DOWN; configure actuator authentication if you need credential details on that endpoint.
- **Waveform tool returns `"mode":"passthrough"`** — the SleepHQ response shape didn't match any known parser. Inspect the `raw` field and adjust `WaveformService.extractSamples()`.
- **MCP client can't connect** — confirm the server is on the right port (default 8080, override via `SLEEPHQ_MCP_PORT`). Streamable HTTP requires both `Content-Type: application/json` and `Accept: application/json, text/event-stream`.
