Investigate central apnea pattern leading up to **{{date}}**.

Resources: `sleephq://guidelines/resmed-therapy-handbook` (§6–9 central/TECSA/ASV), `resmed-titration`, `playbook/data-sources`

Workflow:
1. `get-comparison(fromDate=<date minus 13d>, toDate="{{date}}")` — extract CA index / central counts from each night's `ahi_summary`.
2. For **{{date}}**: `get-device-events(date="{{date}}")` — list CA (and all) device flags with timestamps.
3. For 1–2 CA events: `get-waveform-by-date(date="{{date}}", startMinute=<event_min-2>, maxMinutes=10)` — Flow.40ms + Press.40ms morphology.
4. `scan-apnea-events(date="{{date}}")` — compare flow-derived events vs EVE CA count (do not merge counts).

Output:
- **Trend table** (14 nights × AHI + CA split if present)
- **Findings with confidence**
- **Morphology read** for representative CA windows
- **TECSA risk** (low / moderate / high)
- **Recommendations** per resmed-titration rules with confidence
