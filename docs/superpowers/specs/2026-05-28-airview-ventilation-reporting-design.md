# AirView-style ventilation reporting (Tidal Volume / Respiratory Rate / Minute Ventilation)

**Date:** 2026-05-28
**Status:** Approved (design)
**Anchor workflow:** `physician_titration_review` (priority); also `weekly-trend`.

## Goal

Reproduce the ResMed AirView "Therapy Report" ventilation page (see `Adrian Sian - 11 days.pdf`,
page 3) inside the SleepHQ MCP reports: for each of **Tidal Volume (mL)**, **Respiratory Rate
(breaths/min)**, and **Minute Ventilation (L/min)**, show a span summary of **Maximum (avg) /
95th % (avg) / Median (avg)** across nights, plus per-night charts. The OSCAR waveform-derived
values are the crucial part — AirView displays TV and MV, but SleepHQ does not expose them, so the
local PLD waveforms are the only source.

## Verified data picture (live, 2026-05-18 → 2026-05-28)

SleepHQ `machine_date.attributes` summary objects use keys `{av, max, med, min, upper}` where
**`upper` = 95th percentile** (cross-checked against the AirView PDF: 2026-05-27 RR `max 28.4 /
upper 21.0 / med 16.8` aggregates to AirView span `max-avg 27 / 95th-avg 20 / median-avg 17`).

| Metric | SleepHQ `machine_date` | OSCAR PLD EDF |
| :--- | :--- | :--- |
| Respiratory Rate | ✅ `resp_rate_summary` {av, max, med, min, upper} | ✅ `RespRate.2s` |
| Tidal Volume | ❌ absent | ✅ `TidVol.2s` |
| Minute Ventilation | ❌ absent | ✅ `MinVent.2s` |

- SleepHQ exposes only: `ahi_summary`, `pressure_summary`, `leak_rate_summary`,
  `flow_limit_summary`, `resp_rate_summary`, `epap_summary`, `machine_settings`.
- OSCAR PLD (0.5 Hz / 2 s sampling) carries `RespRate.2s`, `TidVol.2s`, `MinVent.2s` for the span
  (8 nights with PLD data: 05-18, 19, 20, 22, 24, 25, 27, 28). `OscarWaveformStatistics.compute()`
  already returns avg / min / max / p95 per channel but **no median (p50)**.

## Architecture

Hybrid, with each tool staying inside its own data domain (no cross-fetching):

- **RR span aggregate** computed server-side in `get-comparison` from the SleepHQ
  `resp_rate_summary` already present on every night.
- **TV + MV span aggregate** computed server-side in `get-oscar-trend` from OSCAR PLD waveforms.
- The workflow prompt **concatenates** the two structured blocks into one AirView-style table and
  chart group. The agent does no arithmetic — it copies pre-computed numbers verbatim.
- Degrades gracefully: when OSCAR is not configured/reachable, TV/MV render as `—` and the report
  still shows the RR row; nothing blocks.

### Averaging convention (matches AirView)

Each aggregate stat is the mean **over nights that have data for that metric** (days-used), not over
all calendar nights. Report the actual `nights_used` count per source. A night missing
`resp_rate_summary` is excluded from RR averages; a night missing PLD is excluded from TV/MV.

## Server changes

### a. OSCAR median (p50)

`OscarWaveformStatistics.compute()` currently takes a single `percentile` (configured `oscar.analysis.percentile=95`).
Add a median (p50) value computed from the already-sorted sample list. Carry it on the
`ChannelStatistics` record (new `median` field, all constructors updated) and emit `median` in
`NightAnalysisSupport.channelStatsNode()` alongside `avg/min/max/p95`.

### b. `get-comparison` → new root block `ventilation_summary`

Computed in the comparison service after nights are assembled. RR only (SleepHQ has no TV/MV):

```json
"ventilation_summary": {
  "respiratory_rate_per_min": {
    "max_avg": 27.0,
    "p95_avg": 20.0,
    "median_avg": 17.0,
    "nights_used": 10,
    "source": "sleephq_resp_rate_summary"
  }
}
```

- `max_avg` = mean of per-night `resp_rate_summary.max`
- `p95_avg` = mean of per-night `resp_rate_summary.upper`
- `median_avg` = mean of per-night `resp_rate_summary.med`
- Skipped nights and nights lacking `resp_rate_summary` are excluded; `nights_used` reflects the
  actual count. Block omitted entirely if no night has `resp_rate_summary`.

### c. `get-oscar-trend` → `ventilation_summary` block

Computed in `OscarTrendService` over the span it already iterates. Adds TV and MV (and RR as an
OSCAR cross-check):

```json
"ventilation_summary": {
  "tidal_volume_ml":      {"max_avg":712,"p95_avg":466,"median_avg":364,"nights_used":8,"source":"oscar_pld"},
  "minute_vent_l_min":    {"max_avg":12.4,"p95_avg":7.8,"median_avg":6.2,"nights_used":8,"source":"oscar_pld"},
  "respiratory_rate_per_min": {"max_avg":27,"p95_avg":20,"median_avg":17,"nights_used":8,"source":"oscar_pld"}
}
```

- Per night, take the channel's `max`, `p95`, and new `median`; average each across nights-with-PLD.
- The slim per-night trend rows must also carry per-night `tidal_volume`, `minute_vent`,
  `resp_rate` (median value) so the charts have a per-night series. Extend the trend row copy list
  if these fields are not already present.

### d. No new per-night table columns

The titration `Titration Configuration` table is already wide; `resp_rate_cell` stays as-is. TV/MV
appear only in the span summary table and the chart group (YAGNI — avoid widening the table).

## Output format (`src/main/resources/clinical/output-format.md`)

Add an AirView-style span summary table spec:

```
### Ventilation summary (span 18–28 May · 10 nights used)

| Metric | Maximum (avg) | 95th % (avg) | Median (avg) |
| :--- | ---: | ---: | ---: |
| Tidal Volume (mL)          | 712  | 466 | 364 |
| Respiratory Rate (br/min)  | 27   | 20  | 17  |
| Minute Ventilation (L/min) | 12.4 | 7.8 | 6.2 |

Source: RR from SleepHQ `resp_rate_summary`; TV & MV from OSCAR PLD. — = OSCAR not available.
```

Rules:
- Rounding matches AirView: **TV** integer mL, **RR** integer br/min, **MV** 1 decimal L/min.
- Numbers are copied verbatim from the `ventilation_summary` blocks — never computed by the agent.
- TV/MV cells show `—` with an "OSCAR not available" note when the OSCAR block is absent; RR still
  renders from `get-comparison`.
- The two sources can have different `nights_used` (RR from SleepHQ vs TV/MV from OSCAR PLD). The
  header span count reflects the SleepHQ/RR nights; when the OSCAR `nights_used` differs, note it on
  the source line (e.g. "TV & MV from OSCAR PLD, 8 nights"). Do not imply a single shared night
  count across all three rows.

## Charts (`src/main/resources/clinical/autovisualiser.md`)

Add a new **Ventilation mechanics** chart group, separate from the existing 5-chart titration pack:

- Section: `### Ventilation mechanics (charts)`.
- Three line charts by night: Tidal Volume (mL), Respiratory Rate (br/min), Minute Ventilation
  (L/min).
- Series source: per-night median values — RR from `get-comparison` nights (`resp_rate_summary.med`),
  TV/MV from `get-oscar-trend` slim rows.
- Independent of the 5-chart cap; skip any chart whose data is missing (do not invent).
- Smoke / PASS-FAIL checklists still render zero charts.

## Workflow prompts

- **`src/main/resources/prompts/physician-titration-review.md`**: add mandatory
  `### Ventilation summary (span)` and `### Ventilation mechanics (charts)` sections under
  `## Technologist read`, sourced from the two `ventilation_summary` blocks.
- **`src/main/resources/prompts/weekly-trend.md`**: add the same two sections (lighter treatment).
- **`goose-recipe.yaml`**: verify the autovisualiser playbook / clinical resources wiring still
  references the updated playbooks (already partially modified in the working tree).

## Edge cases

- OSCAR not configured or unreachable → TV/MV render `—`; RR row still shows; report never blocks.
- Night missing `resp_rate_summary` → excluded from RR averages; `nights_used` reflects real count.
- Night missing PLD → excluded from TV/MV averages.
- `ventilation_summary` block omitted entirely (not emitted empty) when a source has zero usable
  nights.

## Testing

- Unit: RR `ventilation_summary` aggregate — mean of per-night `max`/`upper`/`med`, nights without
  `resp_rate_summary` excluded, `nights_used` correct, block omitted when none present.
- Unit: `OscarWaveformStatistics` median (p50) correctness on a known sample list.
- Unit: `OscarTrendService` `ventilation_summary` for TV/MV (synthetic or fixture stats), including
  the per-night series fields in slim rows.
- Full `./mvnw test` green; existing `ComparisonTableDisplayTest` and OSCAR tests unaffected.

## Out of scope

- Adding TV/MV columns to the per-night titration table.
- Pulling OSCAR data inside `get-comparison` (cross-domain fetch).
- Workflows beyond `physician_titration_review` and `weekly-trend`.
- The AirView "Care Check-In" survey page (no data; user declined on device).
