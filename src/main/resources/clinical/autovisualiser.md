# Autovisualiser — SleepHQ MCP chart playbook

Use the Goose **autovisualiser** builtin **after** live MCP data returns this session. Charts supplement markdown tables; they never replace them.

## Authority

1. Every numeric label in a chart must come from MCP JSON returned **this session**.
2. Markdown tables (`*_cell`, **FINAL RECOMMENDATIONS**) stay authoritative.
3. If a chart disagrees with a table, fix or drop the chart — do not change table numbers.

## When to chart

| workflow_mode | Max charts | Call autovisualiser? |
|---------------|------------|----------------------|
| balanced | 2 | Yes — after `get-comparison` |
| physician_titration_review | **5** | Yes — after `get-comparison` (priority workflow) |
| clinical_deep_dive | 1 | Yes — sleep stages after `get-combined-night-by-date` |
| mask_leak_with_pressure | 1 | Optional — 7d leak from `get-comparison` if called |
| morning_brief_only | 0 | No — unless user explicitly asks |
| smoke / PASS-FAIL checklists | 0 | **Never** |

## When not to chart

- Before the workflow primary data tool returns (`get-comparison` or `get-combined-night-by-date`).
- Raw waveform channel arrays (`get-waveform-by-date.channels`).
- Full EVE/scan event lists (keep tables in appendix).
- Invented or baseline (`sleephq://patient/baseline`) numbers.

## Data extraction (prefer structured fields)

### `get-comparison` — nightly series

Build arrays from `nights[]` where `skipped` is not true:

```json
{
  "dates": ["2026-05-19", "2026-05-20"],
  "ahi_total": [0.57, 0.42],
  "osa_per_hr": [0.2, 0.15],
  "csa_per_hr": [0.1, 0.08],
  "leak_95_l_min": [12.0, 8.5],
  "spo2_min_pct": [92, 94],
  "usage_hours": [7.2, 6.8]
}
```

**Source paths (in order):**

| Series | Primary path | Fallback |
|--------|--------------|----------|
| date | `nights[].date` | — |
| ahi_total | `nights[].table_display.ahi` → `av` or `average` | `nights[].data.attributes.ahi_summary` |
| osa_per_hr | `table_display.ahi.oa` | `ahi_summary.oa` |
| csa_per_hr | `table_display.ahi.ca` | `ahi_summary.ca` |
| leak_95_l_min | `table_display.leak_rate_l_min` → `p95` or `95` | parse `leak_cell` only if object absent |
| spo2_min_pct | `table_display.spo2_pct.min` | `spo2_cell` parse |
| usage_hours | `table_display.usage_hours` | `usage_cell` parse |

Skip nights with `skipped: true` or missing `data`.

### Sleep stages — single night

From `get-combined-night-by-date` → `journal.sleep_stages_summary`:

```json
{
  "title": "Sleep stages (main episode)",
  "segments": [
    { "label": "Awake", "minutes": 45 },
    { "label": "Light (core)", "minutes": 180 },
    { "label": "Deep", "minutes": 60 },
    { "label": "REM", "minutes": 90 }
  ],
  "source": "minutes_by_stage_for_reporting",
  "reporting_source": "main_sleep_episode"
}
```

Use `minutes_by_stage_for_reporting` only (not `minutes_by_stage` vs `minutes_by_stage_main_episode` pick). Map `core` → **Light (core)** in the legend.

### `get-oscar-trend` (optional second chart)

Only when OSCAR was already fetched and SleepHQ span lacks nights. Use `nights[].respiratory_indices` numeric fields — **do not** divide `events.summary_counts` by hours.

## Recommended charts

1. **Nightly control (line)** — x: date, y: AHI (/hr). Series: total AHI; optional second chart for OSA + CSA lines.
2. **Sleep stages (donut/pie)** — one focal night from `minutes_by_stage_for_reporting`.
3. **Leak trend (bar)** — x: date, y: 95th % leak (L/min) when discussing mask fit.

## physician_titration_review chart pack (up to 5)

**When:** immediately after Phase 4 per-night table (`get-comparison` returned). Place under `## Technologist read` → `### Span trends (charts)` before `### Apnea trends (span)` bullets.

**Priority order** (render in this order; skip a slot if data missing — do not invent):

| # | Chart | Type | Data |
|---|-------|------|------|
| 1 | Total AHI by night | line | `ahi_total` × `dates` |
| 2 | OSA + CSA by night | line (2 series) | `osa_per_hr`, `csa_per_hr` × `dates` — **required** for pressure decisions |
| 3 | Leak 95th by night | bar | `leak_95_l_min` × `dates` — mask-first signal |
| 4 | CPAP usage by night | bar | `usage_hours` × `dates` — compliance gate |
| 5 | SpO₂ nadir by night **or** sleep stages on worst night | line **or** donut | `spo2_min_pct` × `dates`; **else** `minutes_by_stage_for_reporting` from `get-combined-night-by-date` on worst SpO₂ / worst AHI deep night |

**Deep-night only (#5 donut):** call `get-combined-night-by-date` only if already in Phase 5 deep nights — do not add extra API calls just for a chart.

**Do not chart here:** waveform samples, EVE/scan event lists, raw `apnea_trends` JSON (use bullets for span summary).

## Autovisualiser usage

1. Extract JSON arrays/objects as above from the latest tool result.
2. Call autovisualiser with a short title + the structured payload (chart type is inferred by the extension).
3. Place charts under `## Technologist read` after the markdown table for the same data.
4. Caption each chart: tool name + date span (e.g. `get-comparison 2026-05-19–25`).
5. On failure: one-line note "Chart skipped (autovisualiser error)" and continue — do not block the report.

## Example instruction to autovisualiser

> Line chart: nightly total AHI (events/hr) by date. Data: {"dates":["2026-05-19","2026-05-20"],"ahi_total":[0.57,0.42]}. Source: get-comparison table_display.ahi.
