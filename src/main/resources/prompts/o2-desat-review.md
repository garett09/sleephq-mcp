Review oximetry for **{{date}}**.

Resources: `sleephq://playbook/data-sources`, `sleephq://reference/normal-ranges`

Workflow:
1. `get-combined-night-by-date(date="{{date}}")` — `spo2_summary`, `pulse_rate_summary` nightly aggregates.
2. `get-o2-oximetry(date="{{date}}", maxMinutes=15)` — **always cap maxMinutes** (1 Hz O2Ring S); O2 wall time is authoritative (`start_datetime` + `elapsed_seconds`).
3. Optional: `get-device-events` + `scan-apnea-events` same date — use CPAP `timestamp` after `SLEEPHQ_CPAP_CLOCK_ADJUST_SECONDS` drift fix; correlate by **wall time**, not equal session starts (`sleephq://playbook/clock-alignment`).

Output:
- **Nightly SpO₂/pulse summary** (from combined night)
- **Segment read** (nadir, duration below 90% if visible in capped series)
- **Findings with confidence**
- **Recommendations** (CPAP vs ring vs positional) with confidence

Never call `get-o2-oximetry` without `maxMinutes` unless user explicitly requests full-night export.
