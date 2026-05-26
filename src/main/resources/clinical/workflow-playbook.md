# SleepHQ MCP workflow playbook

## Workflow → MCP prompt → tools (full matrix)

See **`goose-recipe.yaml` instructions → Workflow tool and prompt matrix** for the authoritative list. Every workflow must invoke its **Required** tools; discovery/fallback tools are listed so nothing is orphaned.

| Goose `workflow_mode` | MCP prompt | Required tools (summary) |
|----------------------|------------|--------------------------|
| `morning_brief_only` | `morning-brief` | `get-combined-night-by-date` |
| `balanced` | `weekly-trend` | **`get-comparison` (7d)**, `get-device-context`; escalate: EVE + scan |
| `night_summary` | `nightly-review` | `get-combined-night-by-date`, **`get-comparison` (7d)**, `get-device-context` |
| `clinical_deep_dive` | `clinical-deep-dive` | combined night, EVE, scan, waveform, O2, device context |
| `physician_titration_review` | `physician-titration-review` | **`get-comparison` first**, device context, deep nights EVE+scan+waveform+O2 |
| `waveform_dive` | `event-reconciliation` | combined night, EVE, scan, waveform |
| `mask_leak_with_pressure` | `leak-diagnosis` | device context, `list-masks`, combined night, waveform |
| `longitudinal_30d` / `longitudinal_90d` | `physician-titration-review` or `weekly-trend` | **`get-comparison`**, device context, spotlight EVE+scan |
| `deep_machine_date` | `nightly-review` | `get-night-stats` or combined night, device context |

**Sub-investigation prompts (attach to flagged nights):** `central-apnea-investigation`, `obstructive-residual-investigation`, `o2-desat-review`, `titration-decision`, `leak-diagnosis`, `event-reconciliation`.

**Discovery / fallback:** `who-am-i`, `get-configured-defaults`, `list-teams`, `list-machines`, `list-machine-dates`, `get-machine-date-by-date`, import/file/journal tools, `get-waveform` (fileId).

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

**Event count vs AHI index (all workflows):**
- `scan-apnea-events` → raw event list (count, offsets, durations). **Never divide by sleep hours to produce AHI.** Compare against `get-device-events` for reconciliation only.
- `get-device-events` → OSCAR-coded device event log (OA, CA, H, FL). Also not a billing AHI.
- Authoritative per-hour indices: `ahi_summary.av / oa / ca / h` from `get-comparison` nights[] or `get-combined-night-by-date`. Use these for all threshold comparisons and pressure decisions.

**Pressure guardrails (`get-comparison`):** Read `decision_guardrails.must_not_increase_pressure` from the response. If `true`, cite `must_not_increase_reason` verbatim and do not recommend +1 cmH₂O regardless of total AHI.

## Grounding at session start

Read: `get-device-context` (or `sleephq://device/context`), then `sleephq://patient/baseline`, guidelines, `reference/normal-ranges`, playbook resources.
