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
- Raw waveform channel arrays (`get-waveform-by-date.channels` — these are **downsampled** for MCP payload; see `sleephq://playbook/payload-budget`).
- `get-sleephq-night` **sample arrays** (the tool returns aggregates only: p99/p95/median/min/max/avg/count + markers).
- Full EVE/scan event lists (keep tables in appendix).
- Invented or baseline (`sleephq://patient/baseline`) numbers.

## `get-sleephq-night` — percentiles vs charts

**Default: markdown table, not a chart.** `get-sleephq-night` is the full-resolution nightly channel summary; render it in **`### Nightly channel summary`** (goose-recipe) as rows copied verbatim from `cpap.channels` / `oximetry.channels`.

**How percentiles are computed (all channels — same as leak):**

| Rule | Behavior |
|------|----------|
| Sample set | Every finite PLD sample after unit scaling (NaN/null only) |
| Multi-session | All sessions **concatenated** per channel, one distribution |
| Exclusions | **None** on p95/p99/median (no therapy subset, no &gt;24 L/min drop) |
| Markers | Thresholds only (e.g. leak time&gt;24 L/min) — do not alter percentiles |
| Downsampling | **No** — unlike `get-waveform-by-date` |
| O2 | Invalid / `-1` sentinels skipped before stats |
| Local mirror | `RESMED_DATA` / `SLEEPHQ_O2_RING` first; PLD &lt;2 h analysed → API when available |
| Parse window | Up to **12 h** from PLD file start |

**Charts that use leak / resp / SpO₂ percentiles:**

| Span (multi-night) | Use | Do not use |
|--------------------|-----|------------|
| Leak 95th trend | `get-comparison` → `leak_95_l_min` / `table_display.leak_rate_l_min` | `get-sleephq-night` (one night per call) |
| SpO₂ nadir trend | `get-comparison` → `spo2_min_pct` | — |
| Focal night channel table | Copy `get-sleephq-night` JSON into markdown | Do not rebuild p95 from waveform |

**Optional chart (clinical_deep_dive only, ≤1 slot):** a simple bar of `get-sleephq-night` **p95** per CPAP channel for the focal night is allowed **only** if the user asks for a visual or the recipe already returned `get-sleephq-night` — use the tool’s p95 fields only, never `get-waveform-by-date` samples. Prefer the sleep-stage donut when the single chart slot is unused.

**Validation:** when `validation.<channel>.agree` is true, chart labels for that metric should match SleepHQ cloud summaries; `agree=false` is a sanity band (±10% / ±1.0), not a reason to recompute from waveform.

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

**Do not chart here:** waveform samples (downsampled), `get-sleephq-night` raw series (aggregates only), EVE/scan event lists, raw `apnea_trends` JSON (use bullets for span summary). Span **leak 95th** comes from `get-comparison`, not nightly PLD re-aggregation.

## Ventilation mechanics chart group

A separate chart group, independent of the 5-chart titration cap. Render under `## Technologist read` → `### Ventilation mechanics (charts)`, immediately after the Ventilation summary table.

**When:** only when `get-oscar-trend` was already called this session and returned `ventilation_summary` with at least one non-null metric.

**Three line charts by night (render in this order):**

| # | Chart | Type | Data source |
|---|-------|------|-------------|
| 1 | Tidal Volume (mL) by night | line | `get-oscar-trend` slim rows — per-night `tidal_volume.median` |
| 2 | Respiratory Rate (br/min) by night | line | `get-comparison` nights — `resp_rate_summary.med` (preferred); fallback: `get-oscar-trend` slim rows `resp_rate.median` |
| 3 | Minute Ventilation (L/min) by night | line | `get-oscar-trend` slim rows — per-night `minute_vent.median` |

**Rules:**
- Skip any chart whose data series is entirely missing or empty — do not invent values.
- Use per-night **median** values only (not avg or max).
- These charts do not count toward the 5-chart physician titration cap.
- Smoke / PASS-FAIL checklists: **never** render these charts (same rule as the titration pack).
- Caption: `get-oscar-trend YYYY-MM-DD–YYYY-MM-DD` (span dates).

## Autovisualiser usage

**CRITICAL:** Autovisualiser is a **Goose builtin tool**. You MUST invoke it as a tool call — do NOT print or output chart JSON as a code block. If you output `{"type":"line",...}` or any chart spec as a code block, the chart will NOT render; it will appear as raw JSON to the user.

1. Extract the numeric arrays from the latest tool result (e.g. `nights[].table_display`).
2. **Invoke the `autovisualiser` builtin tool** with a natural-language instruction + the domain data object (see example below). Do not construct a chart-library JSON spec yourself.
3. Place the tool call inline at the point in the report where the chart should appear (after the markdown table).
4. Caption each chart: tool name + date span (e.g. `get-comparison 2026-05-19–25`).
5. On failure: one-line note "Chart skipped (autovisualiser error)" and continue — do not block the report.

**NEVER output a code block containing `"type":"line"`, `"type":"bar"`, `"series":`, or any chart-library JSON.** That is not how autovisualiser works and produces broken output.

## Example instruction to autovisualiser (tool call format)

Invoke the tool with a natural-language prompt that includes the data inline:

> Line chart: nightly total AHI (events/hr) by date. Data: {"dates":["2026-05-19","2026-05-20"],"ahi_total":[0.57,0.42]}. Source: get-comparison table_display.ahi.

Pass **domain-named keys** (`dates`, `ahi_total`, `usage_hours`, etc.) — NOT chart-library keys (`x`, `series`, `type`). The autovisualiser builtin infers chart type from the instruction text.
