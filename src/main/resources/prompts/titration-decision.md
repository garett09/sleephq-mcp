Apply ResMed titration rules to **{{date}}** and recommend a pressure decision.

Resources: `get-device-context`, `sleephq://guidelines/resmed-therapy-handbook`, `sleephq://guidelines/resmed-titration`, `sleephq://reference/normal-ranges`

Workflow:
1. `get-combined-night-by-date(date="{{date}}")` — `ahi_summary` (OA/CA/H/RERA), `pressure_summary`, leak, `resp_rate_summary`, usage, `pulse_rate_summary` (heart rate when O2 merged).
2. `get-device-events(date="{{date}}")` — device-flagged OA/H/CA counts for cross-check.
3. Read current pressure from `machine_settings` in the response (not from memory).
4. **`get-comparison(fromDate=<date minus 6d>, toDate="{{date}}")`** — **required before any ±1 pressure recommendation.** Read `decision_guardrails.must_not_increase_pressure`; if `true`, cite `must_not_increase_reason` and output HOLD or −1. Rising CA → do not increase. Rising OA → may increase if leak and usage OK.

> **If called as a sub-investigation** (after parent `physician_titration_review` or `nightly-review` already fetched `get-comparison`), use that session's `decision_guardrails` — no need to call again.

Apply `sleephq://guidelines/resmed-therapy-handbook` (home §5 + central branch); quick triggers in `resmed-titration`.

Output:
- **Trigger checklist** — separate **OSA (OA)**, **CSA (CA)**, and total AHI rows (rule × number × tripped)
- **Findings** with `NN% (Label)` + Explanation
- **Decision**: +1 / −1 / no change cmH₂O
- **FINAL RECOMMENDATIONS** — bold numbered actions, **Confidence: NN% (Label)**, Explanation (`sleephq://playbook/output-format`)
- **Re-evaluation date** (one week from {{date}})

Do not recommend pressure increase if leak 95th is elevated — address leak first (High confidence finding).
