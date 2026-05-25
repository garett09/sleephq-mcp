# CPAP clock drift (not session start)

## Authoritative clocks

| Source | MCP adjustment |
|--------|----------------|
| O2 ring (`get-o2-oximetry`) | **None** — wall time is correct |
| Apple Health sleep stages (journal) | **None** |
| CPAP EDF (`get-waveform`, `get-device-events`, `scan-apnea-events`) | **+N seconds** on `start_datetime` and event `timestamp` when configured |

**Primary source:** `time_offset` on the CPAP `machine_date` for the same calendar night (`GET .../machines/{cpap_id}/machine_dates/{date}`), when the date-based EDF tools run.

**Fallback:** `SLEEPHQ_CPAP_CLOCK_ADJUST_SECONDS` in `.env` if the API omits or rejects `time_offset` (values over 24h are ignored).

**Override:** optional `cpapClockAdjustSeconds` on EDF tools wins over both.

## Clock drift vs session start

- **Drift:** ResMed internal clock wrong → fixed offset on CPAP wall times only.
- **Session start gap:** CPAP turned on before O2 ring (or stopped later) → **no MCP fix**. After drift correction, `start_datetime` on CPAP and O2 may still differ by many minutes; that is normal.

## Correlation

- O2 sample wall time = `start_datetime` + `elapsed_seconds`
- CPAP event wall time = adjusted `timestamp` (or adjusted `start_datetime` + `start_seconds`)
- Journal stages = Apple Health instants (unchanged)

Do **not** align by “minute 0 of CPAP” = “minute 0 of O2”.
