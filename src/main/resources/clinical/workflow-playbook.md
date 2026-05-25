# SleepHQ MCP workflow playbook

## Workflow → MCP prompt → primary tools

| Goose `workflow_mode` | MCP prompt | Primary tools |
|----------------------|------------|---------------|
| `morning_brief_only` | `morning-brief` | `get-combined-night-by-date` |
| `balanced` | `weekly-trend` | `get-comparison` (7d) |
| `night_summary` | `nightly-review` | `get-combined-night-by-date`, `get-comparison` |
| `clinical_deep_dive` | `clinical-deep-dive` | combined night + EVE + scan + waveform + O2 (capped) |
| `physician_titration_review` | `physician-titration-review` | `get-comparison` (15–90d) + selective deep nights |
| `waveform_dive` | `event-reconciliation` or `clinical-deep-dive` | EVE + scan + waveform segment |
| `mask_leak_with_pressure` | `leak-diagnosis` | combined night + `list-masks` |
| `longitudinal_30d` / `longitudinal_90d` | `physician-titration-review` or `weekly-trend` | `get-comparison` |
| `deep_machine_date` | `nightly-review` | `get-night-stats` / combined night |

## Payload caps (Goose)

| Tool | Default |
|------|---------|
| `get-o2-oximetry` | `maxMinutes=10–15` |
| `get-waveform-by-date` | `maxMinutes=2–3` only if EVE↔scan dispute; downsampled OK |
| `get-device-events` / `scan-apnea-events` | full night — **primary** for High-confidence reconciliation |

## Evidence equivalency

Payload caps and downsampled waveform **do not** lower confidence %. For deep nights, **EVE + scan + `ahi_summary`** = High tier when aligned; waveform is optional. See `playbook/data-sources`.

## Grounding at session start

Read: `sleephq://patient/baseline`, `device/current`, `guidelines/resmed-titration`, `guidelines/resmed-therapy-handbook`, `reference/normal-ranges`, `playbook/workflows`, `playbook/data-sources`, `playbook/output-format`.
