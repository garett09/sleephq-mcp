# ResMed titration — quick rules

**Full handbook:** `sleephq://guidelines/resmed-therapy-handbook`  
**PDF:** [10114280r1 ResMed Therapy Handbook v07](https://document.resmed.com/documents/us/10114280r1_ResMed_Therapy_Handbook_AMER_Eng_v07_cw_Interactive.pdf)

**Patient device:** always read `sleephq://device/current` before pressure/EPR advice.

---

## Adult (≥12 y) — increase CPAP (+1 cmH₂O, ≥5 min) when **one lab epoch** shows:

- ≥2 obstructive apneas, **or**
- ≥3 hypopneas, **or**
- ≥5 RERAs, **or**
- ≥3 min loud/ambiguous snoring

**Home:** require **7d+ trend** via `get-comparison` + obstructive evidence (EVE/scan) — see handbook §5.

## Central / TECSA

- **−1 cmH₂O**, wait **20 min**, reassess.
- Persistent TECSA after reduction → ASV evaluation (screen CHF LVEF ≤45% contraindication).
- **Do not** increase pressure for central events.
- **EPR:** handbook — comfort if difficulty exhaling; **this patient:** EPR Off in `device/current` (patient-specific; not a verbatim PDF line).

## Leak

- ResMed: large leak **>24 L/min** → mask fit **before** pressure changes.

## Goals

- **AHI/RDI <5/hr**, SpO₂ **≥90%**, leak **<24 L/min**, usage **≥4 h** (see `sleephq://reference/normal-ranges`).

## Escalate in-person care if

- SpO₂ regularly **<85%**
- AHI consistently **>20/hr** despite titration
- New cardiac symptoms or daytime concerns

## Bilevel

- Consider if obstructive events persist at **15 cmH₂O** CPAP or CPAP intolerance (handbook).
