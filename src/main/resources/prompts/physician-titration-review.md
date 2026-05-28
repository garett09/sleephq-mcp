Physician titration review for **{{reviewSpanDays}}** days ending **{{toDate}}**.

Resources: get-device-context, patient/baseline, resmed-titration, resmed-therapy-handbook, normal-ranges, playbook/workflows, playbook/payload-budget, playbook/data-sources, playbook/output-format

Apply pressure/central/leak rules from `sleephq://guidelines/resmed-therapy-handbook` §5 (home adaptation) — not single-night lab cookbook.

---

## BLOCKER — tools before prose

**Do not write** `### Apnea trends`, `### Titration Configuration`, or **FINAL RECOMMENDATIONS** until **`get-comparison`** has succeeded for this span.

1. `fromDate` = **{{toDate}}** minus (**{{reviewSpanDays}}** − 1) calendar days (inclusive span = **{{reviewSpanDays}}** nights).
2. **`get-comparison(fromDate, toDate={{toDate}})`** — **first required MCP tool** for this workflow (before `get-device-context` if needed to unlock dates).
3. Confirm **`titration_readiness.ready_for_span_trends`** is true OR **`apnea_trends.nights_with_ahi_summary` ≥ 1**. If zero, publish **`### Data completeness`** only and stop — do not invent "no data in span" without calling the tool.
4. **`### Apnea trends (span)`** — copy **`apnea_trends.titration_decision_support.span_summary_bullets`** verbatim from that JSON (not from memory).
5. Then **`get-device-context`** and remaining phases.

---

## Pressure decision rules — verify BEFORE writing FINAL RECOMMENDATIONS

**CSA RULE (MANDATORY):** If `decision_guardrails.must_not_increase_pressure == true`, output **HOLD** or **−1 cmH₂O**. **Never output +1 cmH₂O.** Cite `decision_guardrails.must_not_increase_reason` verbatim in FINAL RECOMMENDATIONS.

**SCAN COUNT ≠ AHI (MANDATORY):** `scan-apnea-events` returns raw event counts, not per-hour indices. Do **not** divide scan count by sleep hours to derive AHI. Use `ahi_summary.av` from `get-comparison` nights[] or `get-combined-night-by-date` for all index-based claims and threshold comparisons.

**LEAK RULE (MANDATORY):** If any night has `leak_cell` 95th ≥24 L/min or non-zero `large_leak` minutes, recommend mask/seal investigation before any pressure change. Do not increase pressure when unresolved mask leak is present.

---

## Mandatory visible sections (do not skip)

Publish **in this order** so titration decisions are scannable:

1. **`### Current device (live)`** — from **`get-device-context`**: mode, pressure/min-max, EPR, ramp, mask menu vs `registered_masks` (one short block).
2. **`### Apnea trends (span)`** — **only after `get-comparison`**: bullets from **`apnea_trends.titration_decision_support.span_summary_bullets`** + **`suggested_pressure_action`** + **`pressure_signals`**.
3. **`### Titration Configuration`** — one row per night from `get-comparison` → `nights[]` → **`table_display` only** (never invent).
4. **`### Data completeness`** — `titration_readiness`, skipped nights, missing `therapy_summaries_present`, missing O2/journal.
5. Deep dives + **`## Physician assessment`** with explicit **pressure decision** in **FINAL RECOMMENDATIONS**.

---

## Phase 1 — Span aggregate (`get-comparison`)

1. `get-comparison(fromDate, toDate={{toDate}})` as in BLOCKER.
2. Read **`mcp_payload_hints`** for waveform/O2 window targets.
3. Read **`apnea_trends`** + **`titration_decision_support`** — decide leak → usage → CSA → OSA → pressure **before** writing recommendations.
4. Build **Titration Configuration** table from `nights[]` **`table_display` only**. **Never merge** nights into date ranges.
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
- Worst leak: `get-waveform-by-date(anchor=worst_leak, maxMinutes=15–30)` — cite `window_selection.reason`
- Worst SpO₂: `get-o2-oximetry(maxMinutes=45)` with pulse summary (or `mcp_payload_hints.o2_recommended_max_minutes`)
- Disputed EVE↔scan: `anchor=auto`, `maxWindows=2` or `anchor=eve_scan_overlap` (`maxMinutes=10–15`)

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
