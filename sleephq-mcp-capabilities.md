# SleepHQ MCP — tools

Reference for the `sleephq` extension (`streamable_http` → your server, e.g. `http://localhost:8080/mcp`).  
Update this file when you add or rename `@McpTool` in Java.

**Official OpenAPI:** [sleephq.com/api/swagger.json](https://sleephq.com/api/swagger.json). All outbound HTTP paths match that spec. A **404** on `get-machine-date-by-date` / `get-combined-night-by-date` usually means no `machine_date` row for that machine and calendar date (not a missing MCP route).

## Tools (24)

| Name | Role |
|------|------|
| `who-am-i` | `GET /api/v1/me` |
| `get-token-status` | Cached OAuth token metadata (no extra SleepHQ GET) |
| `list-teams` | `GET /api/v1/teams` |
| `list-machines` | `GET /api/v1/teams/{team_id}/machines` |
| `get-machine-details` | `GET /api/v1/machines/{id}` |
| `list-machine-dates` | `GET /api/v1/machines/{machine_id}/machine_dates` |
| `get-machine-date-by-date` | `GET /api/v1/machines/{machine_id}/machine_dates/{date}` |
| `get-night-stats` | `GET /api/v1/machine_dates/{id}` — full `machine_date` JSON:API document (`ahi_summary`, `pressure_summary`, `leak_rate_summary`, `spo2_summary`, `pulse_rate_summary`, `movement_summary`, `machine_settings`, usage, etc.) |
| `get-combined-night-by-date` | Same `{ data }` shape as `get-night-stats`; one calendar `date`; calls documented per-date GET for CPAP (and optional O2) then merges summary attributes in-process |
| `list-sleep-tests` | `GET /api/v1/teams/{team_id}/sleep_tests` |
| `list-journals` | `GET /api/v1/teams/{team_id}/journals` |
| `get-journal` | `GET /api/v1/journals/{id}` — single journal entry (feeling_score, weight_grams, step_count, sleep_stages, notes) |
| `list-masks` | `GET /api/v1/teams/{team_id}/masks` |
| `list-devices` | `GET /api/v1/devices` |
| `list-patients` | `GET /api/v1/teams/{team_id}/patients` — clinic/consult accounts |
| `list-imports` | `GET /api/v1/teams/{team_id}/imports` — import history with status and progress |
| `get-import` | `GET /api/v1/imports/{id}` — single import record (status, machine_id, linked file IDs) |
| `list-import-files` | `GET /api/v1/imports/{import_id}/files` — files attached to a specific import |
| `list-files` | `GET /api/v1/teams/{team_id}/files` — all uploaded raw files for a team |
| `get-import-file` | `GET /api/v1/imports/files/{id}` — single file metadata + signed `download_url` (expires 5 min) |
| `get-waveform` | Downloads and parses an EDF device file by `fileId`. Returns `filename`, `start_datetime`, `duration_seconds`, and `channels[]` with `label`, `sample_rate`, `unit`, `samples`. Capped at `maxMinutes` (default 10, max 30). Supports `startMinute` offset to chunk full recordings. |
| `get-waveform-by-date` | Date-based auto-correlation wrapper. Resolves the correct BRP.edf file ID for a YYYY-MM-DD date and retrieves the specified waveform segment. |
| `scan-apnea-events` | Downloads the BRP.edf file for a file ID or date and runs an in-process sliding-window envelope apnea detector on the full respiration flow channel. Returns a structured JSON list of detected apnea event offsets, timestamps, and durations. |
| `get-comparison` | **Local range aggregate:** `fromDate`, `toDate` (YYYY-MM-DD), optional `machineId` (CPAP). Uses documented per-date GET per day; `nights[]` entries include `data` or `skipped` + `reason` |

## Goose note

Goose loads tools from the live MCP session. This file and the recipe opening **message** activity duplicate the tool list so you can scan without hunting panels.
