Diagnose mask leak for **{{date}}**.

Resources: `sleephq://device/current`, `sleephq://reference/normal-ranges`

Workflow:
1. `get-combined-night-by-date(date="{{date}}")` — `leak_rate_summary`, `large_leak`, usage, pressure.
2. `list-masks` and `list-devices` for interface context.
3. If mid-session leak cluster suspected: `get-waveform-by-date(date="{{date}}", maxMinutes=10)` — inspect Press + Leak channels if present.

Output:
- **Leak metrics** (95th, large_leak flags)
- **Findings with confidence**
- **Mask/interface hypothesis**
- **Recommendations** (mask fit before pressure increase) with confidence
