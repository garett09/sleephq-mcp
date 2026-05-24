Diagnose mask leak pattern for **{{date}}**.

Workflow:
1. Call `get-machine-date-by-date(date="{{date}}")` then `get-night-stats(machineDateId=...)`. Extract `leak_rate_summary` and `large_leak` flag.
2. Call `get-sessions(machineDateId=...)` to get mask on/off boundaries.
3. Call `get-leak-data(machineDateId=...)` (no window) to get session-wide leak stats. If average is > 12 L/min or max > 24 L/min, dig deeper.
4. For each suspect spike: call `get-leak-data(machineDateId=..., fromTime=HH:MM:SS, toTime=HH:MM:SS)` with a 5-minute window centered on the spike to inspect raw morphology.

Diagnostic patterns:
- **Spike at session start** → mask seal not seated properly when donning
- **Spike at session end** → mask shift on awakening; not actionable
- **Mid-session spike** → mask shift during REM (common with nasal pillows) → consider mask refit or strap tension
- **Sustained elevation throughout** → wrong cushion size or worn-out mask

Output:
- **Leak profile** (avg, max, % time above 24 L/min)
- **Pattern classification** (which of the above)
- **Recommendation** (specific action: tighten strap, replace cushion, refit during REM, no action)
