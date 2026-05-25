Reconcile respiratory events for **{{date}}** (device vs flow vs billing index).

Resources: `sleephq://playbook/data-sources`

Workflow:
1. `get-combined-night-by-date(date="{{date}}")` — `ahi_summary` total and OA/CA/H split.
2. `get-device-events(date="{{date}}")` — table: time, code, label, duration.
3. `scan-apnea-events(date="{{date}}")` — count, top events by duration.

Output:
- **Side-by-side table**: Source | Event count | Notes
- **Alignment paragraph** — why counts differ (scanner sensitivity, EVE sparsity, hypopnea labeling)
- **Findings with confidence**
- Optional: one `get-waveform-by-date` window if user wants morphology on worst event

Do not treat scan count as AHI. Do not treat sparse EVE as "no events occurred."
