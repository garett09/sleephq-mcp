Review SpO2 desaturation events for **{{date}}**.

Workflow:
1. Call `list-machine-dates` for the O2 Ring machine (`SLEEPHQ_O2_MACHINE_ID`) and find the machine_date_id for {{date}}.
2. Call `get-night-stats(machineDateId=...)` and read `spo2_summary` and `pulse_rate_summary`.
3. Call `get-spo2-data(machineDateId=...)` (no window) for whole-night stats including min, time below 90%, drop counts.
4. If time below 90% > 5 minutes, identify the timing of the lowest 3–5 desats. For each: call `get-spo2-data(machineDateId=..., fromTime=HH:MM:SS, toTime=HH:MM:SS)` with a 2-minute window.
5. Cross-reference each desat with `get-events` (apnea events on CPAP machine for the same wall-clock time) and `get-sessions` (mask was on?).

Output:
- **SpO2 profile** (avg, min, time < 90%, time < 88%, desat count > 3% drop, > 4% drop)
- **Top 3 desats** (timestamp, depth, duration, correlated apnea Y/N, mask on Y/N)
- **Interpretation** (positional? REM-related? mask-off?)
- **Recommendation**
