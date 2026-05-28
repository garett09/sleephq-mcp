Full single-night clinical deep dive for **{{date}}** (RPSGT + physician).

Resources (read first): `get-device-context`, `patient/baseline`, guidelines, `normal-ranges`, playbook resources

Workflow:
1. `get-combined-night-by-date(date="{{date}}")` — **`ahi_components`** (OSA/OA, CSA/CA, total AHI), pressure, leak, usage, SpO₂, **pulse_rate_summary**, `resp_rate_summary`, journal sleep stages.
2. `get-device-events(date="{{date}}")` — EVE device flags (OA, CA, H, FL, etc.); ignore "Recording starts" as pathology.
3. `scan-apnea-events(date="{{date}}")` — flow-derived events; compare count to EVE and `ahi_summary`.
4. Pick 1–2 windows via **`get-waveform-by-date(date="{{date}}", anchor=auto, maxMinutes=15)`** (or `anchor=eve_scan_overlap` / `worst_leak`). Read `window_selection.reason` and evidence — do not guess `startMinute`.
5. Second disputed cluster: `anchor=auto`, `maxWindows=2`, `windowIndex=1`. Manual `startMinute` only when copied from `window_selection` or event `start_seconds`.
6. **`get-o2-oximetry(date="{{date}}", maxMinutes=45)`** — SpO₂ and **pulse_bpm** in same window when possible. Always pass `maxMinutes`; full night can exceed 1M chars.

Output (follow `sleephq://playbook/output-format`):
- `## Technologist read` — event reconciliation, mechanism, SpO₂ with **%**, **heart rate** (avg/min bpm), sleep stages (**light** = core), leak/resp summaries
- `## Physician assessment` — `### Verdict` as **Perfect | On track | Needs change | Urgent — dimension:** + Confidence; then `### FINAL RECOMMENDATIONS`
- `### Supporting findings` table with Explanation column
- `### Limits`

> **Pressure titration note:** A single-night deep dive establishes mechanism and identifies concerns. **Pressure change recommendations (+1 / −1 cmH₂O) require multi-night trend data** — use `physician_titration_review` workflow with `get-comparison` to access `decision_guardrails` and `apnea_trends` before committing to a pressure change. This prompt may recommend investigation of a trend, not the pressure change itself.
