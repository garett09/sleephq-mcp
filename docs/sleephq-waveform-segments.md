# SleepHQ waveform path segments

Waveform samples are loaded from:

`GET /api/v1/machine_dates/{machine_date_id}/{segment}`

The MCP server only allows **known** segments (see `WaveformChannel` in code) so path traversal and arbitrary URL suffixes cannot be injected.

## Confirmed segments (wired in MCP)

| Segment | Approx. rate | Notes |
|---------|----------------|-------|
| `flow_rate_data` | 25 Hz | L/min |
| `pressure_data` | 25 Hz | cmH2O |
| `leak_data` | 25 Hz | L/min |
| `spo2_data` | 1 Hz | O2 Ring night |
| `pulse_rate_data` | 1 Hz | O2 Ring night |
| `tidal_volume_data` | 25 Hz (assumed CPAP trace) | mL; may be absent until SleepHQ finishes processing that import |

## How to probe live API

1. Obtain a bearer token (`POST /oauth/token` with client credentials or password grant per SleepHQ docs).
2. Resolve a `machine_date_id` (`list-machine-dates` MCP tool or `GET /api/v1/machines/{id}/machine_dates`).
3. `curl -H "Authorization: Bearer $TOKEN" "https://sleephq.com/api/v1/machine_dates/$MDID/tidal_volume_data"`  
   - **200** with JSON body → segment is valid for that night.  
   - **404** → segment or night unsupported (common for older imports or non-ResMed sources).

If SleepHQ adds new `*_data` segments, add a matching enum constant in `WaveformChannel`, a two-line tool in `WaveformTools`, and extend `WaveformService.extractSamples` only if the JSON shape differs from existing parsers.

## Candidate segments (not wired until verified)

These names are **guesses** from common CPAP reporting and UI labels; do **not** add to `WaveformChannel` without a successful live response or official OpenAPI entry:

- `minute_ventilation_data`
- `snore_data` / `snoring_data`
- `respiratory_rate_data`
- `motor_data` / `blower_data`

## Payload shapes

The MCP `WaveformService` accepts:

- `{ "data": [ number, ... ] }` (implicit timestep from `native_sample_rate_hz`)
- `{ "data": [ { "t", "v" }, ... ] }`
- JSON:API-style `{ "data": { "attributes": { "values": [...], "interval_seconds": ... } } }`

Unknown shapes return `mode: passthrough` with the raw body for inspection.
