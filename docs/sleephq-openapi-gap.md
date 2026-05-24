# SleepHQ official OpenAPI vs this MCP

## Status: parked

We are **not** pursuing alignment, discovery work, or support tickets against the public SleepHQ OpenAPI until their published contract covers the same surface area the MCP calls. Rationale: the Swagger UI at [https://sleephq.com/api-docs/index.html](https://sleephq.com/api-docs/index.html) loads [https://sleephq.com/api/swagger.json](https://sleephq.com/api/swagger.json); that spec documents core resources (teams, machines, `GET /api/v1/machine_dates/{id}`, imports, journals, etc.) but **does not list** several routes this server uses (waveform `*_data` segments under `machine_dates`, night `sessions`, `events_data`, and similar). Period comparison is **not** proxied to SleepHQ — `get-comparison` aggregates documented per-night `machine_dates` in this MCP. Tooling and operators should assume **published docs are incomplete** relative to all live behavior.

### Clarification: machine_date already bundles summaries

The **documented** `GET /api/v1/machine_dates/{id}` response (MCP: **`get-night-stats`**) is the single source for nightly **aggregate** fields in Swagger’s `MachineDate` model—including `spo2_summary`, `pulse_rate_summary`, and `movement_summary` when the backend populates them. This repo did **not** add alternate HTTP endpoints for those summaries. The “gap” is about **extra sub-paths** (time-series waveform segments, sessions, events, etc.), not missing documentation for the core machine_date document itself.

## References in this repo

- Client split between “documented” and “undocumented but live” paths: `SleepHqClient`.
- Known waveform segments and probing notes: [sleephq-waveform-segments.md](sleephq-waveform-segments.md).

## Unpark when

SleepHQ publishes an OpenAPI (or equivalent) that includes the waveform and night-detail endpoints this MCP relies on, or provides a maintained machine-readable list of supported `machine_dates` subpaths.
