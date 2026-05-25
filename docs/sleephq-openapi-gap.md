# SleepHQ OpenAPI alignment

This MCP server issues HTTP requests only to paths that appear in the published contract:

[https://sleephq.com/api/swagger.json](https://sleephq.com/api/swagger.json)

(`host` + `basePath` + each `paths` key → e.g. `https://sleephq.com/api/v1/me`, `.../machines/{machine_id}/machine_dates/{date}`, `.../machine_dates/{id}`.)

## MCP-only behavior (not extra upstream routes)

- **`get-comparison`** — Builds a local JSON document by calling the documented **Find a Machine Date** route once per calendar day (`GET /api/v1/machines/{machine_id}/machine_dates/{date}`) plus optional O2 overlay logic in `CombinedNightService`. SleepHQ does not expose a `/comparisons` API.
- **`get-combined-night-by-date`** — Same documented per-date GET(s); merges summary fields in-process when CPAP and O2 machines are configured.
- **`get-device-events`** — Downloads `EVE.edf` via `get-import-file` + S3; parses EDF+ TAL annotations locally (ResMed device flags).
- **`get-o2-oximetry`** — Resolves O2 import file via `list-imports` / `list-import-files`; parses Viatom binary locally (O2Ring S `0x0301` or VLD3; not EDF).
- **`get-waveform`**, **`get-waveform-by-date`**, **`scan-apnea-events`** — EDF parse/detect on `BRP.edf` (and other EDF uploads) via `get-import-file` + S3.
- **`get-journal-by-date`** — Paged `list-journals` + local date index (no upstream find-by-date route).
- **Journal overlay on night tools** — `get-night-stats`, `get-combined-night-by-date`, and `get-comparison` attach top-level `journal` from team journals (not from `machine_date`).
- **MCP prompts (10)** — Markdown playbooks under `src/main/resources/prompts/`; reduce hallucinated tool names and workflows.
- **MCP resources** — Static clinical context (`clinical/*.md`) + dynamic `sleephq://comparison/{from}/{to}` for multi-night JSON in context.

## Journal vs machine_date vs O2 tools

| Data | SleepHQ source | MCP access |
|------|----------------|------------|
| AHI, pressure, leak, usage | `machine_date` (CPAP) | `get-night-stats`, `get-combined-night-by-date` |
| SpO₂ / pulse nightly summaries | `machine_date` (often O2 machine) | Merged in `get-combined-night-by-date` |
| SpO₂ / pulse time series | Viatom import binary | `get-o2-oximetry` only |
| Steps, sleep stages, active energy | `journal` (one row per user per day) | `journal` on night tools, `get-journal`, `get-journal-by-date` |
| Respiratory events (device flags) | `EVE.edf` | `get-device-events` |

**Swagger gap:** `active_energy_joules` is accepted on journal POST/PUT but omitted from `Api_V1_JournalSerializer` GET attributes; MCP passes through the field when the live API returns it.

**`sleep_stages`:** Documented as a string (JSON segment array from Apple Health via SleepHQ). MCP adds `sleep_stages_parsed` and **`sleep_stages_summary`** (`minutes_by_stage`, `stage_type_legend`, `asleep_minutes`, `sleep_window`). Legend: `0` in_bed · `1` asleep_unspecified · `2` awake · `3` core · `4` deep · `5` rem. Omits the raw long string when summary is built.

## If SleepHQ adds routes later

When new paths are added to `swagger.json`, extend `SleepHqClient` and expose tools only after the contract lists them.
