# SleepHQ MCP — tools, resources, prompts

Reference for the `sleephq` extension (`streamable_http` → your server, e.g. `http://localhost:8080/mcp`).  
Update this file when you add or rename `@McpTool`, `@McpResource`, or `@McpPrompt` in Java.

**Official OpenAPI:** [sleephq.com/api/swagger.json](https://sleephq.com/api/swagger.json) is incomplete relative to the live routes these tools use; 404 on a waveform or night-detail call often means missing data for that night, not a broken MCP install. See `docs/sleephq-openapi-gap.md` in this repo (parked upstream alignment).

## Tools (24)

| Name | Role |
|------|------|
| `who-am-i` | Auth / identity |
| `get-token-status` | Auth / token |
| `list-teams` | List teams |
| `list-machines` | Machines for a team |
| `get-machine-details` | One machine |
| `list-machine-dates` | Nights for a machine |
| `get-machine-date-by-date` | Resolve night by calendar date |
| `get-night-stats` | **`GET /api/v1/machine_dates/{id}`** (Swagger *Machine Dates*). One JSON payload: `ahi_summary`, `pressure_summary`, `leak_rate_summary`, `spo2_summary`, `pulse_rate_summary`, `movement_summary`, `machine_settings`, usage, etc. Waveform tools are separate time-series routes, not required to *discover* those summaries. |
| `get-combined-night-by-date` | **Merged night (JSON:API):** same `{ data }` shape as `get-night-stats`. One `date` (`YYYY-MM-DD`); optional machine id overrides. Overlays `spo2_summary` / `pulse_rate_summary` / `movement_summary` from O2 when CPAP lacks them. `data.id` = CPAP `machine_date` id. |
| `get-sessions` | CPAP sessions |
| `get-events` | Events |
| `get-flow-rate-data` | Flow waveform (optional `fromTime`/`toTime` HH:MM:SS) |
| `get-pressure-data` | Pressure waveform |
| `get-leak-data` | Leak waveform |
| `get-spo2-data` | SpO₂ waveform |
| `get-pulse-rate-data` | Pulse waveform |
| `get-tidal-volume-data` | Tidal volume waveform |
| `get-correlation-window` | Multi-channel window (`machineDateId`, `fromTime`, `toTime`, optional `channels`) |
| `list-sleep-tests` | Team sleep tests |
| `list-journals` | Journals |
| `list-masks` | Masks |
| `list-devices` | Devices |
| `get-comparison` | Period comparison |
| `get-share-dashboard` | Share dashboard |

Waveform tools: required `machineDateId`; without both times → **stats only**; with `fromTime` + `toTime` → **raw samples** for that window.

## Resources (7)

### Static (no path params)

| URI |
|-----|
| `sleephq://patient/baseline` |
| `sleephq://device/current` |
| `sleephq://guidelines/resmed-titration` |
| `sleephq://reference/normal-ranges` |

### Dynamic (substitute ids)

| URI template |
|--------------|
| `sleephq://team/{teamId}` |
| `sleephq://machine/{machineId}` |
| `sleephq://machine_date/{machineDateId}` |

## Prompts (7)

| Name | Arguments |
|------|-----------|
| `nightly-review` | `date` (YYYY-MM-DD) |
| `central-apnea-investigation` | `date` |
| `weekly-trend` | `weekStartDate` |
| `leak-diagnosis` | `date` |
| `titration-decision` | `date` |
| `o2-desat-review` | `date` |
| `morning-brief` | _(none)_ |

## Goose note

Goose loads tools from the live MCP session; **resources** and **prompts** may be less prominent in the UI than tools. This file and the recipe opening **message** activity duplicate the surface so you can scan without hunting panels.
