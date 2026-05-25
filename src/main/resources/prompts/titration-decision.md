Apply ResMed titration rules to **{{date}}** and recommend a pressure decision.

Resources: `get-device-context`, `sleephq://guidelines/resmed-therapy-handbook`, `sleephq://guidelines/resmed-titration`, `sleephq://reference/normal-ranges`

Workflow:
1. `get-combined-night-by-date(date="{{date}}")` — `ahi_summary` (OA/CA/H/RERA), `pressure_summary`, leak, `resp_rate_summary`, usage, `pulse_rate_summary` (heart rate when O2 merged).
2. `get-device-events(date="{{date}}")` — device-flagged OA/H/CA counts for cross-check.
3. Read current pressure from `machine_settings` in the response (not from memory).

3. Optional: `get-comparison(fromDate=<date minus 6d>, toDate="{{date}}")` — check **`apnea_trends`** before ±1 cmH₂O (rising CA → do not increase; rising OA → may increase if leak OK).

Apply `sleephq://guidelines/resmed-therapy-handbook` (home §5 + central branch); quick triggers in `resmed-titration`.

Output:
- **Trigger checklist** — separate **OSA (OA)**, **CSA (CA)**, and total AHI rows (rule × number × tripped)
- **Findings** with `NN% (Label)` + Explanation
- **Decision**: +1 / −1 / no change cmH₂O
- **FINAL RECOMMENDATIONS** — bold numbered actions, **Confidence: NN% (Label)**, Explanation (`sleephq://playbook/output-format`)
- **Re-evaluation date** (one week from {{date}})

Do not recommend pressure increase if leak 95th is elevated — address leak first (High confidence finding).
