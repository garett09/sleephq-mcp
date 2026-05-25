# ResMed Therapy Handbook (condensed for SleepHQ MCP)

**Source:** [ResMed Therapy Handbook AMER Eng v07](https://document.resmed.com/documents/us/10114280r1_ResMed_Therapy_Handbook_AMER_Eng_v07_cw_Interactive.pdf) (doc **10114280r1**).  
**Basis:** AASM PAP titration practice parameters; ResMed lab protocols (CPAP/APAP, S/VAuto, ASV).  
**Scope:** OSA positive airway pressure — adult (≥12 y) and pediatric (<12 y). Adrian = **adult**.

**Always combine with:** `get-device-context` (live mode/EPR/pressure), `sleephq://patient/baseline`, `sleephq://playbook/data-sources`.

> **Coverage honesty:** This file is a **SleepHQ-focused digest**, not a full reproduction of the 180+ page PDF. Verified against extracted handbook text (May 2026). For modes you do not use (ST, iVAPS, overlap hypoventilation), see the PDF directly.

### What this digest includes (verified in PDF)

| PDF section | In digest? |
|-------------|------------|
| AASM preamble (education, lab, non-cookbook, device limits) | §1 |
| CPAP titration flowchart (adult + pediatric thresholds) | §3, §13 |
| Central branch (−1 cmH₂O, 20 min, ASV if TECSA persists) | §3, §9 |
| APAP / AutoSet / AutoSet for Her (overview + conversion) | §4 |
| Leak &lt;24 L/min, mask type in device menu | §2, §4 notes |
| Supplemental O₂ triggers | §7 |
| Pressure intolerance → bilevel | §8 |
| S/VAuto & S / VAuto conversion (OSA noncompliant) | §8 (brief) |
| ASV contraindication (CHF LVEF ≤45%, CSA-predominant) | §9 |
| Titration considerations (ramp, EPR, BMI, split-night increments) | §4, §13 |

### Intentionally abbreviated or omitted (still in PDF)

- **Full APAP titration flowchart** (same obstructive triggers as CPAP; separate “APAP for Her” page)
- **S/VAuto & S titration** step-by-step (IPAP+EPAP +1, hypopnea → IPAP only, max IPAP 20 adult / 30 ST)
- **ASV / ASVAuto titration** (EPAP, Min/Max PS, obese PS 5, EPAP +1 q20 min)
- **ST, iVAPS, PAC** — respiratory disease / hypoventilation chapters
- **Synchrony** (trigger, rise time, TiControl, cycle) and **comfort** (Easy-Breathe, Vsync, Ramp detail)
- **Medicare HCPC** billing codes (E0601, E0470, E0471)
- **Disease-specific** pages (OHS, COPD, restrictive, normal lung)

> Handbook rules are **not a cookbook** for home data — apply clinical judgment. For **home APAP/CPAP reviews**, use **multi-night trends** (`get-comparison`) before single-night pressure changes.

---

## 1. Pre-titration (lab; analogous at home)

- PAP education, mask fitting, acclimation before titration.
- Optimal titration: AASM-accredited lab, RPSGT protocol, board-certified sleep physician selects final pressure.
- **Home analogue:** resolve **leak >24 L/min** and **usage <4 h** before interpreting AHI or changing pressure.

---

## 2. Universal gates (every mode)

| Gate | Handbook rule | SleepHQ signals |
|------|---------------|-----------------|
| **Leak** | Unintentional leak **<24 L/min**; refit mask if exceeded | `leak_95th`, `large_leak`, waveform Leak channel |
| **Start pressure** | ≥4 cmH₂O for ≥5 min (pediatric & adult) | `machine_settings`, epoch table |
| **Central branch** | If events are **central** → do **not** titrate up for obstruction | EVE `CA`, scan `APNEA_CENTRAL`, `ahi_summary` CA index |
| **Goals** | Control obstruction; **SpO₂ ≥90%** when events cleared (bilevel ST path); leak acceptable; lab endpoint **≥30 min** event-free (§3). **AHI/RDI &lt;5/hr** at final pressure is from **AASM parameters cited by handbook**, not a separate ResMed numeric on the CPAP flowchart | `get-comparison`, `spo2_summary`, combined night |

---

## 3. CPAP titration protocol (adult ≥12 years) — lab single-night

**Increase pressure** — consider **≥+1 cmH₂O for ≥5 minutes** to eliminate obstructive events when **any** trigger in **one titration epoch**:

| Trigger | Adult threshold |
|---------|-----------------|
| Obstructive apneas | **≥2** OAs |
| Hypopneas | **≥3** |
| RERAs | **≥5** |
| Loud/ambiguous snoring | **≥3 minutes** |

**Continue upward titration:** +1 cmH₂O every ≥5 min until **≥30 minutes** without breathing events, max **20 cmH₂O** (adult).

**After control:** may explore **+2 cmH₂O** above controlling pressure (not >+5 cmH₂O total over control level).

**Central / TECSA branch (YES to “Are events central?”):**

- **Decrease 1 cmH₂O**, wait **20 minutes**, reassess.
- If TECSA / Cheyne-Stokes **not** eliminated by down-titration → consider **ASV** (see contraindications below).
- **Do not** increase CPAP for central events.

**Bilevel (S/VAuto) if:**

- Obstructive events persist at **15 cmH₂O** CPAP, or
- CPAP intolerance, or
- History of ventilatory insufficiency.

**Document final settings:** CPAP pressure, EPR, ramp, mask type.

---

## 4. APAP / AutoSet (home therapy)

**Typical initial range (lab conversion):**

| Prior CPAP | Suggested APAP min | Max |
|------------|-------------------|-----|
| <10 cmH₂O | 4 cmH₂O (or comfort) | 20 |
| >10 cmH₂O | 6–8 cmH₂O (or comfort) | 20 |

- **EPR:** handbook — consider for **difficulty exhaling** (Levels 1–3; **full-time or ramp only**; floor 4 cmH₂O). PDF does **not** list “EPR causes TECSA” — reconcile live `machine_settings` with `patient/baseline` before recommending EPR changes.
- **AutoSet Response:** **Soft** for patients sensitive to pressure change (gentler AutoSet).
- **Ramp:** consider if difficulty initiating sleep (pressure rises to minimum treatment pressure).
- **Mask type in device menu:** should match interface class for leak algorithms — **AirFit F40 full face may correctly show as Pillows** in ResMed menu; change only for persistent leak, fit issues, or misclassified events, not label mismatch alone.
- **Elevated BMI / re-titration:** higher starting CPAP may be considered.
- **Split-night lab:** adults may use **+2 or +2.5 cmH₂O** steps (not only +1) per shorter titration time.
- **Pressure too high (awake):** lower pressure to return to sleep, then resume titration.
- **AutoSet algorithm:** flow limitation, snoring, obstructive apnea; **qualifies hypopneas** (without flow limitation often not scored). **FOT** ~4 s into apnea: oscillations return → obstructive; none → central.
- **AutoSet for Her:** flow limitation/snore-driven; EPAP rises in small increments; prioritized above 12 cmH₂O.
- **APAP titration:** same obstructive **increase triggers** as CPAP (§3); separate “APAP for Her” protocol in PDF.

---

## 5. Home-data adaptation (personas: technologist / physician)

Lab triggers are **per observation window**; home reviews use **aggregated nights**.

| Lab action | Home review equivalent |
|------------|------------------------|
| +1 cmH₂O for ≥2 OA in one epoch | **Do not** change pressure on one night with 2 OA unless **7–30d** trend shows residual OA **and** leak/usage OK |
| Central branch: −1 cmH₂O, wait 20 min | **7d+ rising CA** + EVE CA + scan central → consider **decrease 1** or ASV workup; not “increase because AHI bumped one night” |
| Leak >24 L/min | **Mask fit first** — High confidence; invalidates night for pressure decisions |
| 30 min event-free | **≥7 nights** with AHI <5 (or at patient goal) + acceptable leak + usage ≥4 h |
| Snoring ≥3 min | Journal + flow limitation / leak; optional waveform only if disputed |

**Pressure increase (home) — High confidence when ALL:**

1. `get-comparison` ≥7d: mean or majority nights **AHI ≥5** (or above patient goal)
2. **Obstructive** pattern: elevated OA/H, EVE OA/H, scan obstructive events
3. Leak 95th **<24 L/min** (or improving after mask change)
4. Usage **≥4 h** on nights used for the decision

**Pressure decrease / ASV (home) — High confidence when:**

1. **Rising CA index** or TECSA pattern on **multiple nights**
2. EVE CA + scan `APNEA_CENTRAL` aligned
3. Down-titration trial per handbook (−1 cmH₂O) documented in plan; ASV only if persistent after reduction **and** no CHF ASV contraindication

---

## 6. EPR (Expiratory Pressure Relief)

- Reduces expiratory pressure (1/2/3 cmH₂O); not below 4 cmH₂O delivered.
- **Indication (handbook):** difficulty exhaling, comfort.
- **TECSA (handbook):** do not **increase** CPAP for central events; down-titrate first (§3). **EPR:** handbook indication is comfort only; if live `machine_settings` show EPR Off, treat current menu as baseline unless span + clinician agree otherwise.

---

## 7. Supplemental O₂ (titration lab)

Consider O₂ when:

- Awake supine SpO₂ ≤88% on room air before PAP, or
- SpO₂ ≤88% for ≥5 min **without** obstructive events during titration.

Start **1 L/min** ≥15 min; titrate toward **88–94%** SpO₂.

**Home:** use `spo2_summary` + capped `get-o2-oximetry`; distinguish desat from apnea vs leak/off-mask.

---

## 8. Pressure intolerance → bilevel

Signs: mask removal, arousals, no REM, air hunger, failed CPAP, congestion, EPR insufficient, chest discomfort.

→ Consider **S/VAuto** per handbook (IPAP–EPAP differential **4–10 cmH₂O**, maintain ≥5 min per step).

**S/VAuto obstructive titration (PDF, adult ≥12 y) — if bilevel is in scope:**

- **Obstructive apneas:** increase **IPAP and EPAP** ≥+1 cmH₂O every ≥5 min (≥2 obstructive events).
- **Hypopneas / RERAs / snoring:** increase **IPAP** (≥3 H, ≥5 RERAs, ≥3 min snoring); also if SpO₂ &lt;90% ≥5 min with low tidal volume.
- **Central:** decrease to previous settings 20 min; ASV if TECSA/Cheyne-Stokes not cleared by down-titration.
- **VAuto conversion from CPAP:** Min EPAP 4 or 6–8 if CPAP &gt;10; Max IPAP **25**; PS 4–6; defaults Min EPAP 4, PS 4, Max IPAP 20, Ramp Off.

---

## 9. ASV / CSA (summary)

- **ASV indicated:** Cheyne-Stokes or **TECSA not eliminated** by pressure reduction. Target: normalize **AHI** (handbook).
- **ASV contraindicated (class effect):** CHF **NYHA 2–4**, **LVEF ≤45%**, moderate–severe **CSA-predominant** SDB.
- **Not contraindicated (handbook clarifies):** HFpEF (EF &gt;45%), mild SDB (AHI &lt;15), OSA-predominant SDB.
- **ASV may be inappropriate (undertreated):** chronic profound hypoventilation, moderate–severe **COPD**, restrictive thoracic / **NMD** → consider **iVAPS**.
- **Initial ASV (PDF):** EPAP 4+; Min PS 3 (5 if obese); Max PS ≥10; weight &gt;66 lb (30 kg); obstructive events during ASV → EPAP +1 q20 min.

---

## 10. SleepHQ tool mapping

| Handbook question | MCP tools |
|-------------------|-----------|
| Nightly AHI / OA / CA / H | `ahi_summary` on `get-combined-night-by-date` |
| Device-flagged events | `get-device-events` (EVE) |
| Flow-derived events | `scan-apnea-events` (BRP) |
| 15–90 d epochs | `get-comparison` |
| Leak | combined night leak fields; `leak-diagnosis` workflow |
| SpO₂ | `spo2_summary`; windowed `get-o2-oximetry` |
| Journal / comfort | `journal` on night tools |
| Mechanism dispute only | `get-waveform-by-date` maxMinutes=2–3 |

---

## 11. Recommendation confidence (handbook-aligned)

| Recommendation | Typical % | Requires |
|----------------|-----------|----------|
| Mask/leak first | 85–95% | leak 95th elevated or large_leak |
| Keep pressure | 85–95% | 7d+ AHI at goal, leak OK, usage OK |
| Increase +1 cmH₂O | 85–95% | Handbook obstructive triggers met **across trend**, not one spurious night |
| Decrease −1 cmH₂O (central) | 85–95% | Rising CA + EVE/scan central, per central branch |
| Enable EPR | 60–75% | Exhale discomfort + **low CA**; contradicts live EPR Off in `get-device-context` → explain conflict |
| ASV referral | 70–85% | Persistent TECSA after −1 trial; screen contraindications |

---

## 12. Quick reference (adult OSA CPAP)

```
LEAK > 24 L/min?  → mask first (stop pressure titration)
CENTRAL events?    → −1 cmH₂O, wait 20 min; reassess; consider ASV if TECSA persists
OBSTRUCTIVE?       → +1 cmH₂O if ≥2 OA OR ≥3 H OR ≥5 RERA OR ≥3 min snoring (lab epoch)
HOME change?       → need 7–30d get-comparison + EVE/scan on worst nights
GOAL?              → 30 min event-free (lab); AHI/RDI <5 (AASM, cited); SpO₂ ≥90%; leak <24 L/min
```

---

## 13. Pediatric thresholds (&lt;12 years) — PDF only

| Trigger | Pediatric |
|---------|-----------|
| Increase CPAP | ≥1 OA, ≥1 H, ≥3 RERAs, ≥1 min snoring |
| Continue up | max **15 cmH₂O** (vs 20 adult) |
| Snoring increase | ≥1 min |
| Bilevel OA step | ≥1 obstructive event |

Adrian = adult (≥12 y) — use §3, not this table.

---

**Quick rules URI:** `sleephq://guidelines/resmed-titration` (1-screen triggers).  
**Authoritative PDF:** link at top — anything marked “omitted” above is only there.
