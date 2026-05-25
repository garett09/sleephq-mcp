Apply ResMed titration rules to **{{date}}** and recommend a pressure decision.

Resources: `sleephq://guidelines/resmed-therapy-handbook`, `sleephq://guidelines/resmed-titration`, `sleephq://device/current`, `sleephq://reference/normal-ranges`

Workflow:
1. `get-combined-night-by-date(date="{{date}}")` — `ahi_summary` (OA/CA/H/RERA), `pressure_summary`, leak, usage.
2. `get-device-events(date="{{date}}")` — device-flagged OA/H/CA counts for cross-check.
3. Read current pressure from `machine_settings` in the response (not from memory).

Apply `sleephq://guidelines/resmed-therapy-handbook` (home §5 + central branch); quick triggers in `resmed-titration`.

Output:
- **Trigger checklist** (rule × actual number × tripped yes/no)
- **Findings** with `NN% (Label)` + Explanation
- **Decision**: +1 / −1 / no change cmH₂O
- **FINAL RECOMMENDATIONS** — bold numbered actions, **Confidence: NN% (Label)**, Explanation (`sleephq://playbook/output-format`)
- **Re-evaluation date** (one week from {{date}})

Do not recommend pressure increase if leak 95th is elevated — address leak first (High confidence finding).
