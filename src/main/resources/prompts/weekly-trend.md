Produce a weekly therapy trend report for the week starting **{{weekStartDate}}**.

Resources: `sleephq://reference/normal-ranges`, `sleephq://playbook/workflows`, `sleephq://playbook/output-format`

Workflow:
1. Week end = weekStartDate + 6 calendar days (YYYY-MM-DD).
2. **`get-comparison(fromDate=weekStartDate, toDate=weekEnd)`** — mandatory before tables.
3. Prior week: `get-comparison(fromDate=<weekStart minus 7d>, toDate=<weekStart minus 1d>)` (same rule).
4. Build **Rolling 7-Day Performance Table** from `nights[]` (newest first).

### Rolling 7-Day Performance Table

| Night | Usage | Settings | 95th Press | Apnea (OSA · CSA · H · AHI) | Leak (95%) | Resp | SpO₂ | Heart rate | Sleep | Journal |
| :--- | ---: | :--- | ---: | :--- | ---: | ---: | :--- | :--- | :--- | :--- |

- One markdown row per night; no HTML in cells; no code-fenced rows.
- **Usage:** `usage_cell` · **Settings:** `settings_cell` · **Press:** `pressure_cell`
- **Apnea:** **`apnea_indices_cell`** only (e.g. `OSA 0.2/hr · CSA 0.1/hr · AHI 0.57/hr`) — not three separate OSA/CSA/AHI columns
- After both `get-comparison` calls, cite **`apnea_trends.oa`** / **`apnea_trends.ca`** (`rising`, recent vs prior means) and **`pressure_signals`**
- **Leak:** `leak_cell` (includes `large_leak` minutes when present)
- **Resp:** `table_display.resp_rate_cell` when present
- **SpO₂:** `table_display.spo2_cell` (includes **%**, e.g. `avg 97.4% / min 96%`)
- **Heart rate:** `table_display.pulse_cell` (O2 ring `pulse_rate_summary`; e.g. `avg 56 bpm / min 48 bpm`) — **—** when no O2 merge
- **Sleep:** `table_display.sleep_cell` — must include **light** (Apple `core`), **deep**, **rem**, **total** — not steps
- **Journal:** `table_display.journal_cell` — comma-formatted steps, SleepHQ mood (e.g. `Okay (3)`), notes only
- CPAP missing: `—` in therapy columns; journal/sleep still shown when present

### Verdict (if physician block included)

Use `sleephq://playbook/output-format` — **Perfect | On track | Needs change | Urgent** — **[dimension]:** + **Confidence: NN% (High|Medium|Low)**.

Output:
- Headline metrics this week vs prior week — include **mean OSA (OA)** and **mean CSA (CA)** from `apnea_trends`, not AHI alone (AHI mixes obstructive + central)
- Heart-rate trend when `pulse_cell` populated
- Rolling 7-Day Performance Table
- `### Data completeness` (required) — flag missing `pulse_rate_summary`, `spo2_summary`, leak, journal/sleep stages
- Therapy Trends (per-night changes, not merged epochs)
- Findings / Regressions / Improvements / Recommendations (max 2)

Never invent numbers. Do not flag F40 + Pillows menu as error without leak/fit evidence.
