Reconcile respiratory events for **{{date}}** (device vs flow vs billing index).

Resources: `sleephq://playbook/data-sources`

Workflow:
1. `get-combined-night-by-date(date="{{date}}")` — `ahi_components` or `ahi_summary` (total AHI, **OA**, **CA**, H).
2. `get-device-events(date="{{date}}")` — table: time, code, label, duration.
3. `scan-apnea-events(date="{{date}}")` — count, top events by duration.

Output:
- **Side-by-side table**: Source | Event count | Notes
- **Alignment paragraph** — why counts differ (scanner sensitivity, EVE sparsity, hypopnea labeling)
- **Findings with confidence**
- Optional: `get-waveform-by-date` (`maxMinutes=15`, `startMinute` at worst event − 5) if morphology needed
- Publish **full** EVE and scan event lists when context allows (see `sleephq://playbook/payload-budget`)

Do not treat scan count as AHI. Do not treat sparse EVE as "no events occurred."
