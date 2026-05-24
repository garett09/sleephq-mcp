Investigate central apnea pattern leading up to **{{date}}**.

Workflow:
1. Read `sleephq://guidelines/resmed-titration` for TECSA escalation rules.
2. Call `list-machine-dates(perPage=14, sortOrder="desc")` to get the 14 most-recent nights for the CPAP machine.
3. For each night, call `get-night-stats` and extract `ahi_summary` central apnea counts. Plot the trend.
4. For {{date}}: call `get-events(machineDateId=...)` to get exact central event timestamps. For each central event, call `get-flow-rate-data(machineDateId=..., fromTime=HH:MM:SS, toTime=HH:MM:SS)` with a ± 30 s window around the event. Inspect morphology: is there inspiratory effort (obstructive) or true cessation (central)?
5. Cross-reference current pressure setting (10.6 cmH2O fixed) against the trend. If centrals are increasing, evaluate TECSA risk per the guidelines.

Output:
- **Trend table** (14 nights × central index)
- **Morphology read** for 1–3 representative central events
- **TECSA risk assessment** (low / moderate / high) with rationale
- **Recommendation** per the resmed-titration rules: pressure decrease 1 cmH2O + reassess, hold steady, or escalate to ASV evaluation
