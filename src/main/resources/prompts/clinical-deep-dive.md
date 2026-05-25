Full single-night clinical deep dive for **{{date}}** (RPSGT + physician).

Resources (read first): `get-device-context`, `patient/baseline`, guidelines, `normal-ranges`, playbook resources

Workflow:
1. `get-combined-night-by-date(date="{{date}}")` — **`ahi_components`** (OSA/OA, CSA/CA, total AHI), pressure, leak, usage, SpO₂, **pulse_rate_summary**, `resp_rate_summary`, journal sleep stages.
2. `get-device-events(date="{{date}}")` — EVE device flags (OA, CA, H, FL, etc.); ignore "Recording starts" as pathology.
3. `scan-apnea-events(date="{{date}}")` — flow-derived events; compare count to EVE and `ahi_summary`.
4. Pick 1–2 windows (EVE ∩ scan ±2 min, or worst leak/AHI).
5. `get-waveform-by-date(date="{{date}}", startMinute=<event_min-2>, maxMinutes=10)` — Flow.40ms + Press.40ms + **Leak** when leak or desat mechanism is in question.
6. `get-o2-oximetry(date="{{date}}", maxMinutes=15)` — SpO₂ and **pulse_bpm** in same window when possible.

Output (follow `sleephq://playbook/output-format`):
- `## Technologist read` — event reconciliation, mechanism, SpO₂ with **%**, **heart rate** (avg/min bpm), sleep stages (**light** = core), leak/resp summaries
- `## Physician assessment` — `### Verdict` as **Perfect | On track | Needs change | Urgent — dimension:** + Confidence; then `### FINAL RECOMMENDATIONS`
- `### Supporting findings` table with Explanation column
- `### Limits`
