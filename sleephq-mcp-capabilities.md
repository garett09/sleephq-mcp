# SleepHQ MCP — tools

Reference for the `sleephq` extension (`streamable_http` → your server, e.g. `http://localhost:8080/mcp`).  
Update this file when you add or rename `@McpTool` in Java.

**Official OpenAPI:** [sleephq.com/api/swagger.json](https://sleephq.com/api/swagger.json). All outbound HTTP paths match that spec. A **404** on `get-machine-date-by-date` / `get-combined-night-by-date` usually means no `machine_date` row for that machine and calendar date (not a missing MCP route).

## Tools (30)

| Name | Role |
|------|------|
| `who-am-i` | `GET /api/v1/me` |
| `get-token-status` | Cached OAuth token metadata (no extra SleepHQ GET) |
| `get-configured-defaults` | Non-secret env defaults (`SLEEPHQ_TEAM_ID`, CPAP/O2 machine ids, `cpap_clock_adjust_seconds` when set) |
| `list-teams` | `GET /api/v1/teams` |
| `list-machines` | `GET /api/v1/teams/{team_id}/machines` |
| `get-machine-details` | `GET /api/v1/machines/{id}` |
| `get-device-context` | Live context: `machine_settings`, CPAP/O2 machines, `registered_masks`, env ids |
| `get-latest-device-settings` | Alias for `get-device-context` |
| `list-machine-dates` | `GET /api/v1/machines/{machine_id}/machine_dates` |
| `get-machine-date-by-date` | `GET /api/v1/machines/{machine_id}/machine_dates/{date}` |
| `get-night-stats` | `GET /api/v1/machine_dates/{id}` — `{ data: machine_date, journal?: wellness }` with `sleep_stages_summary` (minutes_by_stage, Apple Health stage_type legend) |
| `get-combined-night-by-date` | Same envelope; CPAP + optional O2 summary merge + journal overlay by calendar date |
| `list-sleep-tests` | `GET /api/v1/teams/{team_id}/sleep_tests` |
| `list-journals` | `GET /api/v1/teams/{team_id}/journals` |
| `get-journal` | `GET /api/v1/journals/{id}` — single journal entry (feeling_score, weight_grams, step_count, sleep_stages, notes) |
| `get-journal-by-date` | Paged `list-journals` lookup by `YYYY-MM-DD` → `{ journal: wellness \| null }` including `sleep_stages_summary` |
| `list-masks` | `GET /api/v1/teams/{team_id}/masks` |
| `list-devices` | `GET /api/v1/devices` |
| `list-patients` | `GET /api/v1/teams/{team_id}/patients` — clinic/consult accounts |
| `list-imports` | `GET /api/v1/teams/{team_id}/imports` — import history with status and progress |
| `get-import` | `GET /api/v1/imports/{id}` — single import record (status, machine_id, linked file IDs) |
| `list-import-files` | `GET /api/v1/imports/{import_id}/files` — files attached to a specific import |
| `list-files` | `GET /api/v1/teams/{team_id}/files` — all uploaded raw files for a team |
| `get-import-file` | `GET /api/v1/imports/files/{id}` — single file metadata + signed `download_url` (expires 5 min) |
| `get-waveform` | Downloads and parses an EDF device file by `fileId`. Returns `filename`, `start_datetime` (CPAP drift-adjusted when `SLEEPHQ_CPAP_CLOCK_ADJUST_SECONDS` set), `duration_seconds`, and `channels[]`. Optional `cpapClockAdjustSeconds` override. |
| `get-waveform-by-date` | Resolves BRP.edf for `YYYY-MM-DD`; same as `get-waveform` including optional CPAP clock drift on wall times. |
| `scan-apnea-events` | Full-night flow apnea scan; `timestamp` drift-adjusted on CPAP clock. Optional `cpapClockAdjustSeconds`. |
| `get-device-events` | ResMed `EVE.edf` device events; `timestamp` drift-adjusted on CPAP clock. Optional `cpapClockAdjustSeconds`. Not `scan-apnea-events`. |
| `get-o2-oximetry` | Downloads a **Viatom O2 Ring** binary session via `list-imports` (by `fileId` or `date` + `SLEEPHQ_O2_MACHINE_ID`). Supports **O2Ring S** (`0x0301`, 1 s samples) and classic **VLD3** (~4 s). Not EDF. Nightly averages: `get-combined-night-by-date`. |
| `get-comparison` | **Local range aggregate:** `fromDate`, `toDate` (YYYY-MM-DD), optional `machineId` (CPAP). `nights[]` rows include `data`, optional `journal`, or `skipped` + `reason` |

## MCP prompts (10)

| Name | Use |
|------|-----|
| `morning-brief` | Last night 3–4 line brief |
| `nightly-review` | Single night + 7d comparison |
| `weekly-trend` | 7d vs prior week (`balanced`) |
| `leak-diagnosis` | Mask/leak troubleshooting |
| `titration-decision` | Single-night ResMed ±1 cmH₂O rules |
| `o2-desat-review` | SpO₂ summary + capped oximetry series |
| `central-apnea-investigation` | 14d CA trend + focal night waveform |
| `clinical-deep-dive` | EVE + scan + waveform + O2 one night |
| `physician-titration-review` | 15/30/60/90d comparison + selective deep nights |
| `event-reconciliation` | Device vs flow vs `ahi_summary` |

Templates: `src/main/resources/prompts/*.md`

## MCP resources

**Static (Markdown):** `sleephq://patient/baseline`, `guidelines/*`, `reference/normal-ranges`, `playbook/*` (no static device file — use live context)

**Dynamic (JSON):** `device/context`, `team/{teamId}`, `machine/{machineId}`, `machine_date/{machineDateId}`, `comparison/{fromDate}/{toDate}`

## Goose workflow

See [goose-recipe.yaml](goose-recipe.yaml). Grounding: `get-device-context` → static resources → workflow prompt → tools.

| Workflow | Default prompt |
|----------|----------------|
| `balanced` | `weekly-trend` |
| `clinical_deep_dive` | `clinical-deep-dive` |
| `physician_titration_review` | `physician-titration-review` |

**O2:** always `get-o2-oximetry(maxMinutes=10–15)` in chat unless exporting full night.

## Goose note

Goose loads tools, prompts, and resources from the live MCP session. This file and [goose-recipe.yaml](goose-recipe.yaml) are the workflow “skill” for this repo (there is no separate `SKILL.md`).

**Runtime:** services call `SleepHqClient` + `BinaryDownloadSupport` directly; `get-comparison` walks dates sequentially. Only `get-night-stats` uses Spring `@Cacheable("nightStats")` (6h Caffeine). No `sleephq.cache.*` / parallel fetch executor properties.
