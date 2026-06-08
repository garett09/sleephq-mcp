# sleephq-mcp

A Spring AI MCP server (Streamable HTTP transport) that wraps the SleepHQ REST API and exposes it as MCP **tools** for an LLM client (Goose, Claude Desktop, or any MCP-compatible client) to drive sleep therapy analysis and titration decisions.

## Prerequisites

- **Java 21+** — [download from Adoptium](https://adoptium.net/) or via `brew install temurin@21`
- **SleepHQ account** — free tier works; CPAP data uploaded via [Magic Uploader](https://sleephq.com) or manual import
- Maven is **not** required — `./mvnw` is the self-contained wrapper included in this repo

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

### 1. Get SleepHQ OAuth credentials

Log in to [sleephq.com](https://sleephq.com) → **Settings** → **API** → create an OAuth application. Copy the **Client ID** and **Client Secret** — these go into `.env` as `SLEEPHQ_CLIENT_ID` / `SLEEPHQ_CLIENT_SECRET`.

### 2. Configure and start

```bash
cp .env.example .env
# Required: fill in SLEEPHQ_CLIENT_ID, SLEEPHQ_CLIENT_SECRET, SLEEPHQ_MCP_API_KEY
# Optional OSCAR 2.0: set OSCAR_ENABLED=true and OSCAR_DATA_PATH (folder with oscar.db)
# Optional: set SLEEPHQ_LOCAL_DATA_PATH / SLEEPHQ_O2_LOCAL_PATH if using local SleepHQ mirror
./run.sh
```

`./run.sh` stops any old JVM on port 8080, rebuilds, copies the jar to `dist/`, and starts a single fresh process (avoids `NoClassDefFoundError` from stale servers). Wait for log line `Classpath sanity OK` before connecting a client.

**Stop when done:** `./stop.sh` frees port 8080 (the JVM keeps running until stopped).

### 3. Verify it's up

```bash
curl -s http://localhost:8080/actuator/health
# → {"status":"UP"}
```

**CPAP clock drift:** Date-based EDF tools (`get-device-events`, `get-waveform-by-date`, `scan-apnea-events` with `date`) read `time_offset` from the CPAP `machine_date` API. Optional env fallback: `SLEEPHQ_CPAP_CLOCK_ADJUST_SECONDS`. O2 and journal are never shifted. See `sleephq://playbook/clock-alignment`.

Server listens on `http://localhost:8080/mcp` (Streamable HTTP). Health at `/actuator/health`.

## Connect an MCP client

The server speaks **Streamable HTTP** at `http://localhost:8080/mcp`. Any MCP-compatible client works; add the URL and the `X-SleepHQ-MCP-Key` header matching your `SLEEPHQ_MCP_API_KEY`.

### Claude Desktop

Add to your `claude_desktop_config.json` (macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "sleephq": {
      "url": "http://localhost:8080/mcp",
      "headers": {
        "X-SleepHQ-MCP-Key": "YOUR_SLEEPHQ_MCP_API_KEY"
      }
    }
  }
}
```

Restart Claude Desktop after editing. Run `./run.sh` first so the server is up.

### Goose Desktop

`./run.sh` in a terminal → in the app add a **Streamable HTTP** extension → URL `http://localhost:8080/mcp`, header `X-SleepHQ-MCP-Key` = your key. Enable it in a new chat. When done: `./stop.sh`.

**Waveform smoke:** paste [`context/goose-smoke-waveform.txt`](context/goose-smoke-waveform.txt) into a chat — see [docs/smoke-test-waveform-windows.md](docs/smoke-test-waveform-windows.md).

### Goose CLI

`goose-recipe.yaml` uses the same URL/headers. `goose session --recipe goose-recipe.yaml` or `./scripts/goose-with-mcp.sh session --recipe goose-recipe.yaml`, then `./stop.sh`.

[sleephq-mcp-capabilities.md](sleephq-mcp-capabilities.md) lists every tool. **More smoke tests:** [waveform windows](docs/smoke-test-waveform-windows.md) · [OSCAR](docs/smoke-test-oscar-mcp.md) · [SleepHQ night summary](docs/smoke-test-sleephq-night.md)

## Optional integrations

### OSCAR (local CPAP backup)

[OSCAR](https://www.sleepfiles.com/OSCAR/) is an open-source app that stores a local backup of your ResMed CPAP data. **Requires OSCAR 2.0.0 or later** — the legacy binary format used by OSCAR 1.x is not supported.

Set `OSCAR_ENABLED=true` in `.env` and point `OSCAR_DATA_PATH` at the folder containing `oscar.db` (OSCAR 2.0 writes this alongside the `Profiles/` directory). When configured, `get-combined-night-by-date` attaches a `night_analysis` block with per-channel statistics (respiratory rate, tidal volume, minute ventilation, flow limit, leak, EPAP/IPAP, event counts) derived from the local SQLite database — richer than what the SleepHQ API returns alone.

Default path: `~/Documents/OSCAR20_Data`. Set `OSCAR_DATA_PATH` in `.env` if yours differs.

### Local SleepHQ mirror

If you run `sleephq_download.py` (from [ezscript](https://github.com/adriansian/ezscript)) to download your SleepHQ PLD/EDF files locally, `get-sleephq-night` will read from disk first for faster, offline-capable access. Configure `SLEEPHQ_LOCAL_DATA_PATH`, `SLEEPHQ_O2_LOCAL_PATH`, and `SLEEPHQ_SYNC_REPORT_PATH` in `.env` if your mirror paths differ from the defaults shown in `.env.example`. Without a local mirror the tool falls back to the SleepHQ API automatically.

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
- **OSCAR tools return empty / `get-oscar-status` says not configured** — set `OSCAR_ENABLED=true` and `OSCAR_DATA_PATH` in `.env` pointing at the folder that contains `oscar.db` (OSCAR 2.0.0+ required; OSCAR 1.x binary format is not supported). Restart `./run.sh` after changing env vars.
- **`get-sleephq-night` shows `no_sleephq_pld`** — no local mirror found at `SLEEPHQ_LOCAL_DATA_PATH`; the tool falls back to the API. If you want local-first reads, run `sleephq_download.py` first and confirm the path matches `.env`.
- **`NoClassDefFoundError` after code change** — always restart via `./run.sh` (full clean rebuild), never hot-reload into a running JVM.
