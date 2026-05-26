Investigate residual obstructive apnea (OSA) pattern leading up to **{{date}}**.

Resources: `sleephq://guidelines/resmed-therapy-handbook` (§3 obstructive triggers, §5 home), `resmed-titration`, `playbook/data-sources`

Workflow:
1. `get-comparison(fromDate=<date minus 13d>, toDate="{{date}}")` — read **`apnea_trends.oa`** (rising, recent vs prior means) and per-night **`table_display.osa_cell`** / **`ahi_cell`**.
2. For **{{date}}**: `get-combined-night-by-date(date="{{date}}")` — `ahi_components` or `ahi_summary` OA/H split.
3. `get-device-events(date="{{date}}")` — OA/H device flags with timestamps.
4. `scan-apnea-events(date="{{date}}")` — obstructive vs central flow events; do not merge counts with EVE.
5. If OA elevated and leak controlled: optional **`get-waveform-by-date`** on worst OA cluster (`startMinute=<event_min-3>, maxMinutes=15`) — need preceding flow-limited breaths and recovery to confirm obstructive morphology; 5 min is insufficient.

Output:
- **Trend table** (14 nights × **OSA (/hr)** + **CSA (/hr)** + AHI — use `osa_cell`, `csa_cell`, `ahi_cell`; cite `apnea_trends.pressure_signals.possible_under_titration` when rising OA
- **Findings with confidence**
- **Pressure direction** — increase only if rising OA + low CA + leak/usage OK; **do not** increase if `apnea_trends.ca.rising` or `possible_over_titration`
- **Recommendations** per resmed-titration with confidence
