# Required report formatting (Goose / Markdown)

## Confidence scale (always show BOTH label and %)

| Label | Percentage | When to use |
|-------|------------|-------------|
| **High** | **85–95%** | Multiple agreeing sources (`ahi_summary` + EVE + trend, or 7d+ `get-comparison`, or **EVE + scan-apnea-events** on same night with reconciliation) |
| **Medium** | **60–75%** | Single strong source or partial alignment (e.g. one night O2 min; **unexplained** EVE vs scan gap) |
| **Low** | **30–50%** | Inference only, one tool, or conflicting data left unresolved |

**Not a downgrade:** skipping waveform, downsampled waveform, or `maxMinutes=2` — use EVE + scan + comparison at full High tier when they agree (see `sleephq://playbook/data-sources` § Evidence equivalency).

Table cells: `90% (High)` not just `High`.

## Inline charts (Goose autovisualiser)

Optional **after** markdown tables exist for the same data. **physician_titration_review:** up to 5 span charts; **balanced:** max 2; tables and **FINAL RECOMMENDATIONS** stay authoritative. See `sleephq://playbook/autovisualiser`.

## Therapy verdict (plain language — mandatory)

Use a **headline rating** the patient can scan, then the clinical dimension. Always add **Confidence: NN% (High|Medium|Low)** on the same line.

```markdown
### Verdict
**[Perfect | On track | Needs change | Urgent]** — **[compliance | oximetry | leak | control | comfort]:** one sentence with bold numbers.
**Confidence:** 88% (High)
```

| Headline | Meaning | Typical mapping |
|----------|---------|-----------------|
| **Perfect** | Goals met with margin; no action this window | Low AHI, good usage every night, SpO₂ min ≥90%, leak controlled |
| **On track** | Adequate when used; keep current plan | Former **ADEQUATE** — therapy works on CPAP nights, minor watch items only |
| **Needs change** | One named gap to fix (not “borderline” alone) | Former **BORDERLINE** — e.g. multi-day CPAP lapse, SpO₂ nadir &lt;90%, high leak, EPR discomfort |
| **Urgent** | Safety or control failure | Former **INADEQUATE** — high AHI on used nights, severe desaturation, chronic non-use |

**Example (good):**
```markdown
### Verdict
**Needs change — compliance:** AHI **0.4–0.6** on CPAP nights but **5 nights without CPAP** (2026-05-21–25) while Apple sleep logged.
**Confidence:** 90% (High)
```

**Example (bad):** **ADEQUATE** — therapy is adequate. *(use headline + dimension + confidence)*

Legacy terms **ADEQUATE / BORDERLINE / INADEQUATE** are clinician shorthand only — never use them alone in patient-facing text.

## Section order (physician_technologist)

1. `## Technologist read` — tables and numbers only
2. `## Physician assessment` — starts with verdict, ends with **FINAL RECOMMENDATIONS**

## Physician block template

```markdown
## Physician assessment

### Verdict
**On track — control:** Mean AHI **0.5** on used nights; usage ≥4 h on 6/7 nights.
**Confidence:** 88% (High)

---

### FINAL RECOMMENDATIONS
…
```

## Markdown tables (Goose / chat UI)

- Pipe tables: header + `| :--- |` separator + data rows (not in ` ``` ` fences).
- **No HTML** in cells. Use `table_display` plain-text fields:
  - **Usage:** `usage_cell` — e.g. `8.8 h` (`machine_date.usage`)
  - **Settings:** `settings_cell` — `machine_settings` menu (mode, pressure, EPR)
  - **Pressure:** `pressure_cell` — `pressure_summary` avg / 95th cmH₂O
  - **EPAP:** `epap_cell` — `epap_summary` avg / 95th cmH₂O (bilevel / relevant modes)
  - **Apnea (preferred in pipe tables):** **`apnea_indices_cell`** — one column, e.g. `OSA 0.2/hr · CSA 0.1/hr · H 0.27/hr · AHI 0.57/hr` (header **`Apnea (OSA · CSA · H · AHI)`**). **H** = hypopnea (`ahi_summary.h`); **CSA** = central/clear-airway (`ca`). Avoid three narrow OSA/CSA/AHI columns without **H** in Goose/chat.
  - **Split cells (JSON only):** `osa_cell`, `csa_cell`, `h_cell`, `ahi_cell` — each value ends with `/hr`; `ahi_cell` is total-only when OA/CA exist
  - **Range trends:** `get-comparison` root **`apnea_trends`** — use for rising OSA/CSA before pressure changes
  - **Leak:** `leak_cell` — e.g. `9.6 L/min · large 12m` (`leak_rate_summary` / `leak_95th` + `large_leak`)
  - **Resp rate:** `resp_rate_cell` — e.g. `avg 14/min / 95th 18/min` (`resp_rate_summary`; not tidal volume)
  - **Flow limitation:** `flow_limit_cell` — nightly index when present
  - **SpO₂:** `spo2_cell` — always includes **%** (e.g. `avg 97.4% / min 84%`)
  - **Heart rate:** `pulse_cell` — O2 `pulse_rate_summary` (e.g. `avg 56 bpm / min 48 bpm`); required in titration/longitudinal tables when O2 is configured
  - **Sleep:** `sleep_cell` — use `minutes_by_stage_for_reporting` when present; else `minutes_by_stage_main_episode` when `journal_stage_mismatch`; else `minutes_by_stage` (**light** = core). If `overlap_detected`, note `sleep_window.span_minutes` — do not overstate fragmentation.
  - **Journal:** `journal_cell` — comma-formatted steps, SleepHQ mood label (e.g. `10,826 steps · Okay (3) · note: …`)
- **Do not** put steps or feeling inside the Sleep column.
- **Completeness:** each night lists `table_display.therapy_summaries_present` — flag missing `ahi_summary`, `pressure_summary`, `epap_summary`, `leak_rate_summary`, `resp_rate_summary`, `flow_limit_summary`, `machine_settings`, `usage`, `large_leak`.
- **Wide tables:** drop **Journal** or **Flow lim.** before dropping **Usage**, **Press**, **EPAP**, **OSA**, **CSA**, **AHI**, **Leak**, **Sleep**, or **Heart rate**; never split an index across lines.
- **Titration / physician tables (`physician-titration-review`):** mandatory sections: **Current device** → **Apnea trends** (`titration_decision_support`) → **Titration Configuration** table (all columns) → **Data completeness** → **FINAL RECOMMENDATIONS** with explicit **HOLD / +1 / −1 / mask-first**. `apnea_indices_cell` with `!` = elevated.

## Journal feeling (SleepHQ mood)

- API field: `feeling_score` **1–5** (1 = worst, 5 = best).
- MCP adds `feeling_label`: **Awful, Poor, Okay, Good, Great** (SleepHQ journal UI).
- Tables: use `journal_cell` or `journal.feeling_label` — not raw `feeling 3` without the label.

## Mask type (ResMed menu vs registered mask)

- **Do not** recommend changing mask menu from Pillows to Full Face **only** because `machine_settings.mask` says Pillows while `registered_masks` lists AirFit F40.
- **ResMed AirFit F40** is often configured as **Pillows** in the device menu — that can be correct per ResMed.
- Flag mask menu only when: leak remains high after seal check, wrong interface type for observed leak pattern, or user reports fit issues — cite evidence, not label mismatch alone.

## Rules

- **FINAL RECOMMENDATIONS** — `####` numbered actions, **Confidence: NN% (Label)**, Explanation paragraph.
- Never put recommendations only inside a small table.
- Bold all AHI, pressure (cmH₂O), SpO₂ %, and leak values in narrative text.
