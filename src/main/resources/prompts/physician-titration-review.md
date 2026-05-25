Physician titration review for **{{reviewSpanDays}}** days ending **{{toDate}}**.

Resources: get-device-context, patient/baseline, resmed-titration, resmed-therapy-handbook, normal-ranges, playbook/workflows, playbook/payload-budget, playbook/data-sources, playbook/output-format

Apply pressure/central/leak rules from `sleephq://guidelines/resmed-therapy-handbook` §5 (home adaptation) — not single-night lab cookbook.

---

## Mandatory visible sections (do not skip)

Publish **in this order** so titration decisions are scannable:

1. **`### Current device (live)`** — from **`get-device-context`**: mode, pressure/min-max, EPR, ramp, mask menu vs `registered_masks` (one short block).
2. **`### Apnea trends (span)`** — copy **`apnea_trends.titration_decision_support.span_summary_bullets`** and state **`suggested_pressure_action`**; cite `pressure_signals` (over- vs under-titration).
3. **`### Titration Configuration`** — full per-night pipe table (below); **every column populated or —**.
4. **`### Data completeness`** — skipped nights, missing `therapy_summaries_present`, missing O2/journal.
5. Deep dives + **`## Physician assessment`** with explicit **pressure decision** in **FINAL RECOMMENDATIONS**.

---

## Phase 1 — All nights (one call)

1. `fromDate` = {{toDate}} minus ({{reviewSpanDays}} − 1) calendar days.
2. `get-comparison(fromDate, toDate={{toDate}})` — **mandatory** before any table or pressure advice. Read root **`mcp_payload_hints`** for waveform/O2 window targets.
3. Read **`apnea_trends`** + **`titration_decision_support`** — decide leak → usage → CSA → OSA → pressure **before** writing recommendations.
4. Build **Titration Configuration** — one row per calendar night from `nights[]` **`table_display` only** (never invent). **Never merge** nights into date ranges.
5. Reconcile menu vs masks (`get-device-context`) — separate bullet, not in table rows.
6. Optional **Configuration change summary**: nights with `settings_changed_from_prior_night` true.

**Per-night `table_display` fields (all required in table when data exists):** `usage_cell`, `settings_cell`, `pressure_cell`, `epap_cell`, **`apnea_indices_cell`**, `leak_cell`, `resp_rate_cell`, `flow_limit_cell`, `spo2_cell`, `pulse_cell`, `sleep_cell`, `journal_cell`. Check `therapy_summaries_present`.

**Apnea cell legend:** `OSA x/hr · CSA y/hr · H w/hr · AHI z/hr` from `ahi_summary` (`oa`, `ca`, `h`, `av`). **CSA** = central/clear-airway index (`ca`), not hypopnea. **H** = hypopnea index — when AHI &gt; OSA+CSA, the gap is usually **H** (e.g. OSA 0 · CSA 0 · H 0.19 · AHI 0.2). Trailing **`!`** = elevated (OSA ≥1/hr, CSA ≥5/hr). Do **not** render three separate OSA/CSA/AHI columns without **H**.

---

## Phase 2 — Deep dives (max 8 nights; large-context sessions)

- First **3 nights** after each pressure/settings change
- Worst **AHI** night (and worst **OSA!** or **CSA!** night if different)
- Worst **leak** / `large_leak` — **waveform required**
- Any night with **`csa_elevated`** or **CSA …!** in `apnea_indices_cell`
- Any night with **`osa_elevated`** or **OSA …!** or `apnea_trends.oa.rising`
- Worst **SpO₂ min** night

Per deep night (`nights[].date` only):
- `get-device-events` + `scan-apnea-events` (**required**) — publish **event lists** (time, label, duration), not counts-only summaries
- Worst leak: `get-waveform-by-date` (Flow + Press + Leak, `maxMinutes=15–30`, `startMinute` at cluster − 5)
- Worst SpO₂: `get-o2-oximetry(maxMinutes=45)` with pulse summary (or `mcp_payload_hints.o2_recommended_max_minutes`)
- Disputed EVE↔scan: second waveform window (`maxMinutes=10–15`) at event `startMinute − 2`

---

## Titration Configuration table (required)

| Date | Usage | Settings | 95th Press | EPAP | Apnea (OSA · CSA · H · AHI) | Leak (95%) | Resp | Flow lim. | SpO₂ | Heart rate | Sleep | Journal |
| :--- | ---: | :--- | ---: | ---: | :--- | ---: | ---: | ---: | :--- | :--- | :--- | :--- |

| Column | `table_display` field |
| :--- | :--- |
| Usage | `usage_cell` |
| Settings | `settings_cell` |
| 95th Press | `pressure_cell` |
| EPAP | `epap_cell` |
| Apnea | **`apnea_indices_cell`** (must include **H** when `ahi_summary.h` is present; `!` = elevated OSA/CSA) |
| Leak | `leak_cell` |
| Resp | `resp_rate_cell` |
| Flow lim. | `flow_limit_cell` |
| SpO₂ | `spo2_cell` |
| Heart rate | `pulse_cell` |
| Sleep | `sleep_cell` |
| Journal | `journal_cell` |

**Renderer:** pipe table only; no HTML; **—** if empty; **\*** on date if `settings_changed_from_prior_night`. Do **not** drop Usage, Press, EPAP, Apnea, Leak, Resp, or Flow lim.

---

## Titration decision (physician — required in FINAL RECOMMENDATIONS)

State **one primary pressure action** with confidence:

| Action | When (data-driven) |
| :--- | :--- |
| **HOLD** | Goals met; no rising OSA/CSA; leak & usage OK |
| **+1 cmH₂O** | `possible_under_titration` or rising OSA; leak &lt;24 L/min; usage ≥4 h; **not** if CSA rising or `possible_over_titration` |
| **−1 cmH₂O** | Rising CSA / `possible_over_titration`; central branch per handbook |
| **Mask/leak first** | Leak 95th ≥24 or large_leak — **no** pressure ↑ until fixed |
| **EPR / comfort** | Only per live `machine_settings` + handbook; not for rising CSA |
| **ASV / in-person** | Persistent CSA after −1 trial; screen contraindications |

Cross-check **`titration_decision_support.suggested_pressure_action`** — explain if you disagree.

---

## Phase 3 — Output (`sleephq://playbook/output-format`)

- `## Technologist read` — sections 1–4 above + deep-night reconciliation + leak waveform appendix
- `## Physician assessment` — Verdict → **FINAL RECOMMENDATIONS** (numbered, bold pressure action first) → Supporting findings → Deep-night appendix

Never invent numbers. Never pull uncapped `get-o2-oximetry` for every night.
