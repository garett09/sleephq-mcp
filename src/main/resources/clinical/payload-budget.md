# MCP payload budget (large context)

When the Goose session has **≥500k tokens free** (e.g. 156k/1M), use richer tool windows. Server defaults are in **`get-comparison` → `mcp_payload_hints`** (also env `SLEEPHQ_MCP_*`).

## Server defaults (large-context profile)

| Setting | Default | Env override |
|---------|---------|--------------|
| Waveform default window | **10 min** | `SLEEPHQ_MCP_WAVEFORM_DEFAULT_MAX_MINUTES` |
| Waveform max window | **60 min** | `SLEEPHQ_MCP_WAVEFORM_MAX_MINUTES_CAP` |
| Waveform samples/channel after decimation | **4000** | `SLEEPHQ_MCP_WAVEFORM_MAX_SAMPLES` |
| O2 recommended window | **45 min** | `SLEEPHQ_MCP_O2_RECOMMENDED_MAX_MINUTES` |

Full-night tools (no minute cap): **`scan-apnea-events`**, **`get-device-events`**.

## Goose workflow guidance

### physician_titration_review

- **Phase 2:** up to **8** deep nights (was 6); every deep night: **EVE + scan** with **event list** in appendix (not counts only).
- **Worst leak:** `get-waveform-by-date` **`maxMinutes=15–30`**, `startMinute` at leak cluster − 5 min.
- **Worst SpO₂ / desat:** `get-o2-oximetry` **`maxMinutes=45`** (or server `o2_recommended_max_minutes`).
- **Disputed mechanism:** up to **2** extra waveform windows (`maxMinutes=10–15`) at event `startMinute − 2`.

### clinical_deep_dive

- `scan-apnea-events` + full **get-device-events** list.
- Waveform: **`maxMinutes=15`**, Flow + Press + Leak.
- O2: **`maxMinutes=45`** aligned to desat window.

### balanced / weekly

- One worst-night reconciliation: EVE + scan + optional waveform **`maxMinutes=10`**.

## Evidence tier unchanged

Richer payloads do **not** automatically raise confidence. **High** still requires agreeing sources (`ahi_summary`, EVE, scan). Downsampled waveform is transport-only.

## Legacy / tight context

Set env to legacy caps: `WAVEFORM_DEFAULT=3`, `WAVEFORM_CAP=30`, `WAVEFORM_MAX_SAMPLES=500`, `O2_RECOMMENDED=15`.
