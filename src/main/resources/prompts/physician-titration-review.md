Physician titration review for **{{reviewSpanDays}}** days ending **{{toDate}}**.

Resources: get-device-context, patient/baseline, resmed-titration, resmed-therapy-handbook, normal-ranges, playbook/workflows, playbook/data-sources, playbook/output-format

Apply pressure/central/leak rules from `sleephq://guidelines/resmed-therapy-handbook` §5 (home adaptation) — not single-night lab cookbook.

Phase 1 — All nights (one call):
1. `fromDate` = {{toDate}} minus ({{reviewSpanDays}} − 1) calendar days.
2. `get-comparison(fromDate, toDate={{toDate}})` — build **Titration period table** for every night in `nights[]`.
3. Detect **pressure epochs** when `machine_settings` change across nights.
4. Reconcile epochs with `get-device-context` (`machine_settings` vs `registered_masks`).
5. Trend narrative per epoch (AHI, leak, usage, SpO₂).

Phase 2 — Deep dives (max 5–6 nights total, not every night):
- First 3 nights after each pressure change
- Worst AHI night
- Worst leak / large_leak night
- Any night with CA in EVE or elevated CA in summary
- Worst SpO₂ min night

Per deep night only:
- `get-device-events` + `scan-apnea-events`
- Worst AHI/CA: **`get-device-events` + `scan-apnea-events` (required)** — High-confidence reconciliation without waveform when layers agree; waveform only if EVE↔scan disagree (`maxMinutes=2`)
- Worst SpO₂: `get-o2-oximetry(maxMinutes=15)` only

Phase 3 — Output (follow `sleephq://playbook/output-format`):
- `## Technologist read` — titration table, epoch table, event reconciliation
- `## Physician assessment`:
  - `### Verdict` — **ADEQUATE/BORDERLINE/INADEQUATE** one bold line
  - `### FINAL RECOMMENDATIONS` — numbered `#### 1. **Action**` with **Confidence: NN% (High/Medium/Low)** and 2–4 sentence **Explanation** each (max 3 actions)
  - `### Supporting findings` — | Finding | Confidence | Explanation | Evidence | (use `90% (High)` format)
  - `### Deep-night appendix` — one bullet block per deep night
  - `### Data completeness`

Never put the main plan only in a small recommendations table. Never pull uncapped `get-o2-oximetry` for all nights.
