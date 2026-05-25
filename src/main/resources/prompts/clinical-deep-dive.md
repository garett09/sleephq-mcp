Full single-night clinical deep dive for **{{date}}** (RPSGT + physician).

Resources (read first): `patient/baseline`, `device/current`, `guidelines/resmed-therapy-handbook`, `resmed-titration`, `normal-ranges`, `playbook/workflows`, `playbook/data-sources`, `output-format`

Workflow:
1. `get-combined-night-by-date(date="{{date}}")` — anchor AHI, pressure, leak, usage, SpO₂, journal.
2. `get-device-events(date="{{date}}")` — EVE device flags (OA, CA, H, FL, etc.); ignore "Recording starts" as pathology.
3. `scan-apnea-events(date="{{date}}")` — flow-derived events; compare count to EVE and `ahi_summary`.
4. Pick 1–2 windows (EVE ∩ scan ±2 min, or worst leak/AHI).
5. `get-waveform-by-date(date="{{date}}", startMinute=<event_min-2>, maxMinutes=10)` — Flow.40ms + Press.40ms.
6. `get-o2-oximetry(date="{{date}}", maxMinutes=15)` — same window when possible.

Output (follow `sleephq://playbook/output-format`):
- `## Technologist read` — event reconciliation, mechanism, O2
- `## Physician assessment` — `### Verdict` then `### FINAL RECOMMENDATIONS` with bold numbered actions, **NN% (Label)**, and Explanation paragraphs
- `### Supporting findings` table with Explanation column
- `### Limits`
