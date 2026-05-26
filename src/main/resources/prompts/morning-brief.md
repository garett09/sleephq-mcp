Produce a morning brief for the most recent completed night.

Resources: `get-device-context`, `sleephq://reference/normal-ranges`

Workflow:
1. Discovery → resolve latest night date (or use user-provided date).
2. `get-combined-night-by-date(date)` — **`ahi_components`** (or `ahi_summary`): **OSA (OA)**, **CSA (CA)**, total AHI, leak, usage, SpO₂, **pulse_rate_summary**, `resp_rate_summary`, journal.
3. Optional MCP prompt `nightly-review` context only if user asks for 7-day comparison.

Output (6 lines max):
- **Apnea** — always list **OSA**, **CSA**, **Hypopneas (H)**, and **total AHI** when `therapy_display.apnea` or `ahi_components` has them (use `therapy_display.apnea_indices_cell` verbatim if helpful). **Never** report total AHI alone when OSA/CSA/H exist.
- Flag `osa_elevated` / `csa_elevated` on `ahi_components` when tripped
- **Key concern** (or "none") — if CA ≥ 5/hr, note possible over-titration before suggesting pressure **up**
- **One action**
- **Journal** — sleep: total · light · deep · rem; steps/feeling if present (from `table_display` or journal)
- **Heart rate** — `pulse_rate_summary` avg/min with **bpm** when O2 merged; else **—**
