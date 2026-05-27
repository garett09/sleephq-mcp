# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
./mvnw test                     # run all unit tests
./mvnw test -Dtest=ClassName    # run a single test class
./mvnw package                  # full build (produces target/sleephq-mcp-0.0.1-SNAPSHOT.jar)
./run.sh                        # stop old JVM, clean rebuild, copy to dist/, start server
```

`./run.sh` is the correct way to (re)start the server — it prevents stale JVM / `NoClassDefFoundError` by doing a full clean build before starting. Wait for `Classpath sanity OK` in the log before connecting a client.

Health check: `GET http://localhost:8080/actuator/health`

## Architecture

```
tools/        @McpTool facades — one thin method per tool, all wrapped in McpResponses.safe()
   ↓
service/      NightService, CombinedNightService, ComparisonService, DeviceEventService,
              WaveformService, OximetryService, JournalLookupService, DeviceContextService
   ↓
client/       SleepHqClient — one method per documented endpoint family; path composition only
   ↓
auth/         AuthInterceptor (bearer token + 401 retry), TokenManager (token cache)
```

**Single sources of truth:**
- Auth + 401 retry → `AuthInterceptor`
- Tool error handling → `McpResponses.safe()` (wraps every `@McpTool` body)
- Path validation → `SleepHqPathParams` + URI templates in `SleepHqClient`
- MCP HTTP auth → `McpApiKeyAuthFilter` (`X-SleepHQ-MCP-Key` header check)
- Observability → `McpInvocationLoggingAspect` (AOP)

## Key conventions

**Adding a new tool:** Add a path-backed method to `SleepHqClient` only if the path appears in `swagger.json`, then one `@McpTool` method in the right `*Tools` class, body wrapped in `McpResponses.safe()`.

**SleepHQ API contract:** All upstream calls use `https://sleephq.com` + `/api` + `/v1/...` paths from the published Swagger. Do not invent undocumented routes. See `docs/sleephq-openapi-gap.md` for tools that do local processing on top of documented routes (e.g. `get-comparison`, EDF parsing, Viatom binary parsing).

**EDF processing:** `get-waveform`, `get-device-events`, `scan-apnea-events` download binary EDF files via the import/S3 route and parse locally (`EdfParser`, `EdfAnnotationParser`, `EdfBinarySupport`). O2 oximetry uses a Viatom proprietary binary (`ViatomSessionParser`).

**CPAP clock drift:** Date-based EDF tools read `time_offset` from the `machine_date` API per night. Fallback env: `SLEEPHQ_CPAP_CLOCK_ADJUST_SECONDS`. See `CpapClockAlignment` and `src/main/resources/clinical/clock-alignment.md`.

**Clinical/prompt resources:** MCP prompts (`src/main/resources/prompts/*.md`) and static clinical context (`src/main/resources/clinical/*.md`) are served as MCP resources. These are classpath resources loaded via `StaticContextResources` and `AnalysisPrompts`. Do not embed clinical guidance in Java code.

**OSCAR local data:** Optional integration reads `~/Documents/OSCAR_Data` (configurable). Background → [`context/`](context/README.md). Primary LLM output is compact `night_analysis` on `get-combined-night-by-date` (no raw PLD/EVE by default). Tools: `get-oscar-status`, `get-night-analysis`, `get-mechanics`, `get-oscar-trend`, `get-oscar-events` (`detail=summary` default), `get-plmd-night`. Env: `OSCAR_DATA_PATH`, `OSCAR_PROFILE_NAME`, `OSCAR_DEVICE_FOLDER`.

**`session_metric` (channel 0x1158):** ResMed extended summary metric with unconfirmed semantics; treat as opaque. Do not use for clinical interpretation.

## Environment variables

Required at runtime (set in `.env`):
- `SLEEPHQ_CLIENT_ID` / `SLEEPHQ_CLIENT_SECRET` — SleepHQ OAuth credentials
- `SLEEPHQ_MCP_API_KEY` — header key for the `/mcp` endpoint (or set `SLEEPHQ_MCP_ALLOW_ANONYMOUS=true` for trusted localhost only)

Optional defaults (see `application.properties` for all):
- `SLEEPHQ_TEAM_ID`, `SLEEPHQ_CPAP_MACHINE_ID`, `SLEEPHQ_O2_MACHINE_ID` — used by journal overlay and `get-combined-night-by-date` defaults
- `SLEEPHQ_MCP_PORT` — default `8080`
- `SLEEPHQ_CPAP_CLOCK_ADJUST_SECONDS` — fallback drift correction
- `OSCAR_DATA_PATH`, `OSCAR_PROFILE_NAME`, `OSCAR_DEVICE_FOLDER` — local OSCAR CPAP backup (optional)
