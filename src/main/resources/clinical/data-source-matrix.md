# Data source trust matrix

| Question | Authoritative tool | Confidence | Do not use alone |
|----------|-------------------|------------|------------------|
| Current pressure / mode / EPR / ramp / menu mask | `get-device-context` or `sleephq://device/context` (newest therapy night; magic uploader) | High | Static markdown or memory |
| Registered masks vs menu | `get-device-context` (`registered_masks` + `machine_settings`) | High | Treating F40 + Pillows menu as automatic error (F40 often uses Pillows in ResMed menu) |
| Nightly AHI / OA/CA/H index | `ahi_summary` → `apnea_indices_cell` (OSA·CSA·H·AHI), `osa_cell`, `csa_cell`, `h_cell`, `ahi_cell` | High | `scan-apnea-events` count |
| Multi-night OA/CA trend | `get-comparison` → `apnea_trends` | High when ≥7 nights with `ahi_summary` | Total AHI trend only |
| Delivered pressure (95th / avg) | `pressure_summary` → `pressure_cell` | High | `machine_settings` alone |
| EPAP (95th / avg) | `epap_summary` → `epap_cell` | High when bilevel/EPR relevant | — |
| Therapy usage hours | `usage` → `usage_cell` | High | — |
| Device-flagged events | `get-device-events` (EVE.edf TAL) | High for labeled flags | May be sparse vs flow |
| Flow-drop / hypopnea scan | `scan-apnea-events` (BRP, full-night server-side) | **High** when aligned with EVE + `ahi_summary` | As billing AHI alone |
| Flow morphology (dispute only) | `get-waveform-by-date` (2–3 min, downsampled OK) | High in window; optional if EVE+scan agree | Full night without cap |
| SpO₂ nightly min/avg | `spo2_summary` on combined night | High | — |
| Heart rate (pulse) nightly min/avg | `pulse_rate_summary` on combined night / `table_display.pulse_cell` | High when O2 merged | Guessing from journal |
| SpO₂ time series | `get-o2-oximetry` (O2Ring S 1 Hz, cap minutes) | Medium in aligned window | Uncapped full night in chat |
| Pulse time series | `get-o2-oximetry` (`pulse_bpm` per sample) | Medium in aligned window | — |
| Leak 95th / large leak | `leak_rate_summary`, `leak_95th`, `large_leak` | High | — |
| Respiratory rate nightly | `resp_rate_summary` → `resp_rate_cell` | High | — |
| Flow limitation index | `flow_limit_summary` → `flow_limit_cell` | High when present | — |
| Flow / leak morphology | `get-waveform-by-date` (Flow, Press, Leak channels) | High in 2–5 min window | Full night uncapped |
| Tidal volume | **Not** in SleepHQ `machine_date` summaries | — | Inventing TV from resp rate alone |
| Wall-clock correlation (O2 / journal vs CPAP EDF) | `sleephq://playbook/clock-alignment`; CPAP tools apply `SLEEPHQ_CPAP_CLOCK_ADJUST_SECONDS` | High when drift env set | Matching O2 session start to CPAP t=0 |
| 7–120 day trends | `get-comparison` | High for per-night summaries | Guessing from one night |
| Steps / sleep stages | `journal` on night tools | High when present | `movement_summary` as steps |

## Clock drift (CPAP only)

O2 ring and Apple Health sleep stages use **correct** wall time. ResMed EDF may be slow; set `SLEEPHQ_CPAP_CLOCK_ADJUST_SECONDS` (e.g. 1428 = 23m48s). MCP adds that offset to CPAP `start_datetime` / `timestamp` only — **not** session-start alignment. See `sleephq://playbook/clock-alignment`.

## Reconciliation rule

Always compare **three layers** on focal nights:
1. `ahi_summary` (therapy index)
2. `get-device-events` (device)
3. `scan-apnea-events` (flow algorithm)

Expect **scan ≥ device** for hypopneas. Explain mismatches in **Event reconciliation**.

## Evidence equivalency (payload tightening ≠ lower confidence)

**Do not** reduce confidence % because you skipped raw waveform or used downsampled `samples[]`.

| Clinical claim | High (85–95%) without waveform when |
|----------------|-------------------------------------|
| Residual OA / obstructive pattern | `ahi_summary` high OA + EVE OA/H + scan obstructive events same night |
| Central apnea concern | EVE CA flags + scan `APNEA_CENTRAL` (or CA index in summary) + epoch trend |
| Therapy adequate / keep pressure | `get-comparison` 7–30d AHI goal + leak + usage (no waveform required) |
| Event reconciliation | All three layers called; mismatch explained (not ignored) |

**Use waveform only when:** EVE vs scan disagree on mechanism, or you need FOT/flow shape for one disputed event. Then **2–3 min** at event `startMinute` is enough; downsampling does **not** lower the tier.

**Downgrade confidence only for:** missing tool for the claim, usage &lt;4h on a usage conclusion, unexplained EVE↔scan conflict, or inference with no numeric anchor.

## Pressure advice confidence

Titration thresholds: `sleephq://guidelines/resmed-therapy-handbook` (lab + home §5).  
Report as **NN% (Label)** — see `sleephq://playbook/output-format`.

| Label | % | Recommendation | Assign when |
|-------|---|----------------|-------------|
| High | 85–95% | Keep pressure | 7d+ AHI goal + acceptable leak + usage |
| High | 85–95% | Increase pressure | High AHI + obstructive evidence (EVE or waveform) + leak controlled |
| High | 85–95% | Address mask first | Elevated leak 95th or large_leak |
| Medium | 60–75% | Increase pressure | High AHI but only summary AHI, no EVE/waveform |
| Low | 30–50% | Increase pressure | Scan event count only — no EVE, no `ahi_summary`, no comparison trend |
| High | 85–95% | Decrease pressure | Rising CA + guideline trigger |
