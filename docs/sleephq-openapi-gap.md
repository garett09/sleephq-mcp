# SleepHQ OpenAPI alignment

This MCP server issues HTTP requests only to paths that appear in the published contract:

[https://sleephq.com/api/swagger.json](https://sleephq.com/api/swagger.json)

(`host` + `basePath` + each `paths` key → e.g. `https://sleephq.com/api/v1/me`, `.../machines/{machine_id}/machine_dates/{date}`, `.../machine_dates/{id}`.)

## MCP-only behavior (not extra upstream routes)

- **`get-comparison`** — Builds a local JSON document by calling the documented **Find a Machine Date** route once per calendar day (`GET /api/v1/machines/{machine_id}/machine_dates/{date}`) plus optional O2 overlay logic in `CombinedNightService`. SleepHQ does not expose a `/comparisons` API.
- **`get-combined-night-by-date`** — Same documented per-date GET(s); merges summary fields in-process when CPAP and O2 machines are configured.

## If SleepHQ adds routes later

When new paths are added to `swagger.json`, extend `SleepHqClient` and expose tools only after the contract lists them.
