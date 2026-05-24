Apply ResMed titration rules to **{{date}}** and recommend a pressure decision.

Workflow:
1. Read `sleephq://guidelines/resmed-titration` and `sleephq://device/current`.
2. Call `get-machine-date-by-date(date="{{date}}")` then `get-night-stats(machineDateId=...)`.
3. Extract event counts from `ahi_summary`: obstructive apneas (OAs), hypopneas, RERAs (if present), snoring duration.
4. Cross-reference current fixed pressure (10.6 cmH2O) against the night's pressure_summary.

Apply the rules from the guideline literally:
- ≥ 2 OAs OR ≥ 3 hypopneas OR ≥ 5 RERAs OR ≥ 3 min snoring → **+1 cmH2O**
- Central apneas rising → **−1 cmH2O**, reassess in 20 min next night
- None of the above triggered → **no change**
- If both upward and downward triggers fire → defer the increase, reassess centrals next night

Output:
- **Trigger checklist** (one line per rule with the night's actual number and whether it tripped)
- **Decision**: +1 cmH2O / −1 cmH2O / no change
- **Rationale** (2 sentences)
- **Re-evaluation date** (one week from {{date}})
