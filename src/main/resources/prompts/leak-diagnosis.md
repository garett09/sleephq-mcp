Diagnose mask leak for **{{date}}**.

Resources: `get-device-context`, `sleephq://reference/normal-ranges`

Workflow:
1. `get-combined-night-by-date(date="{{date}}")` — `leak_rate_summary`, `large_leak`, usage, pressure, `pulse_rate_summary` if O2 present.
2. `list-masks` and `list-devices` for interface context.
3. If mid-session leak cluster suspected: **`get-waveform-by-date(date="{{date}}", anchor=worst_leak, maxMinutes=10–15)`** — inspect **Flow**, **Press**, and **Leak**; cite `window_selection`. Do not invent `startMinute`.

Output:
- **Leak metrics** (95th, large_leak flags)
- **Findings with confidence**
- **Mask/interface hypothesis**
- **Recommendations** (mask fit before pressure increase) with confidence
