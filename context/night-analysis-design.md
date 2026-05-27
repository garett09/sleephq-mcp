# Night analysis design

## Goal

Analyze CPAP data on the MCP server and return compact JSON so LLMs do not ingest raw waveforms or full event streams.

## Primary output: `night_analysis`

Attached by `get-combined-night-by-date` and `get-night-analysis` when OSCAR is configured.

| Field | Source |
|-------|--------|
| `session` | `Summaries.xml.gz` + `.000` header |
| `channels` | `_PLD.edf` / `_BRP.edf` stats (avg, min, max, p95, min_at, max_at) |
| `events` | `_EVE.edf` counts + capped `timed_sample` |
| `notable_moments` | Channel extrema correlated with nearby events (±120s default) |
| `data_sources` | Provenance list |

## Caps (application.properties)

- `oscar.analysis.max-notable-moments` (default 20)
- `oscar.analysis.max-timed-events` (default 100)
- `oscar.analysis.max-nearby-events-per-moment` (default 5)

## Not in scope for default tools

Raw PLD/BRP sample arrays — use `get-waveform` only for deep dives.
