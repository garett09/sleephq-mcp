# SleepHQ MCP workflow playbook

## Workflow → MCP prompt → primary tools

| Goose `workflow_mode` | MCP prompt | Primary tools |
|----------------------|------------|---------------|
| `morning_brief_only` | `morning-brief` | `get-combined-night-by-date` |
| `balanced` | `weekly-trend` | `get-comparison` (7d) |
| `night_summary` | `nightly-review` | `get-combined-night-by-date`, `get-comparison` |
| `clinical_deep_dive` | `clinical-deep-dive` | combined night + EVE + scan + waveform + O2 (capped) |
| `physician_titration_review` | `physician-titration-review` | `get-device-context` + `get-comparison` (`apnea_indices_cell`, `titration_decision_support`) + deep nights → explicit pressure decision |
| `waveform_dive` | `event-reconciliation` or `clinical-deep-dive` | EVE + scan + waveform segment |
| `mask_leak_with_pressure` | `leak-diagnosis` | combined night + `list-masks` |
| `longitudinal_30d` / `longitudinal_90d` | `physician-titration-review` or `weekly-trend` | `get-comparison` |
| `deep_machine_date` | `nightly-review` | `get-night-stats` / combined night |

## Payload caps (Goose)

Read **`sleephq://playbook/payload-budget`** and **`get-comparison` → `mcp_payload_hints`** after each comparison call.

| Tool | Large context (e.g. 1M session) |
|------|--------------------------------|
| `get-o2-oximetry` | `maxMinutes=45` on deep/desat nights (server default in hints) |
| `get-waveform-by-date` | Deep nights: `maxMinutes=15–30` at event/leak `startMinute`; server decimates to ~4000 pts/channel |
| `get-device-events` / `scan-apnea-events` | **Full night** — publish **event lists** in appendix, not counts only |

## Evidence equivalency

Payload caps and downsampled waveform **do not** lower confidence %. For deep nights, **EVE + scan + `ahi_summary`** = High tier when aligned. **Leak waveform** is required on the worst-leak titration night (not optional). See `playbook/data-sources`.

**Heart rate (all workflows):** use `pulse_rate_summary` / `table_display.pulse_cell` when O2 is configured; never omit from titration or weekly tables when data exists.

**OSA / CSA / H (all comparison workflows):** use **`apnea_indices_cell`** (`OSA · CSA · H · AHI`) in markdown tables (not three narrow columns without H). Read `apnea_trends` before pressure ±1.

## Grounding at session start

Read: `get-device-context` (or `sleephq://device/context`), then `sleephq://patient/baseline`, guidelines, `reference/normal-ranges`, playbook resources.
