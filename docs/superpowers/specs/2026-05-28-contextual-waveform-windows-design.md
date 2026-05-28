# Contextual waveform windows — design spec

**Status:** Approved (2026-05-28)  
**Author:** Brainstorm with Adrian  
**Related:** `get-waveform-by-date`, OSCAR `night_analysis`, journal `sleep_stages_summary`, `clock-alignment.md`

## Problem

`get-waveform-by-date` returns a capped BRP slice (default 10–15 minutes) at an agent-supplied `startMinute`. Without anchoring:

- Windows look arbitrary (e.g. minute 145) and are weak for “how did I sleep?” narratives.
- Flow/leak morphology varies by physiology and sleep stage; uncorrelated slices mislead interpretation.
- Prompts already say “event_min − 5”, but agents often skip event tools and guess offsets.

**Observed on 2026-05-19:** Therapy read (AHI 0.42, leak spike 01:37, hypopnea 04:31) was coherent, but **journal sleep stages in MCP disagreed with SleepHQ UI** (MCP: ~172 min awake / 9 min REM vs UI: 14 min awake / ~4 h REM). That undermines fragmentation claims and motivates **journal accuracy work** in parallel with waveform anchoring.

## Goals

1. Server auto-selects waveform window(s) from clinical signals with explicit **why** metadata.
2. Preserve manual override (`startMinute` wins).
3. Keep waveform source **SleepHQ BRP.edf** in v1 (no OSCAR EDF download).
4. Phase 2: anchor to journal REM/deep via wall-clock ↔ CPAP session overlap.
5. Phase 3 (optional): multi-window bundle tool; OSCAR BRP fallback if SleepHQ missing.

## Non-goals

- Full-night uncapped waveform in chat.
- Using waveform alone for titration decisions (comparison + EVE + scan remain primary).
- Fixing SleepHQ API semantics (only MCP parsing/aggregation/display).

---

## Approach (approved)

**v1:** Extend `get-waveform-by-date` with `anchor` + `window_selection` response block. New `WaveformWindowPlanner` service.

**v2:** `anchor=rem|deep|core|awake` using journal segment overlap with CPAP usage window.

**v3 (optional):** `get-contextual-waveforms` (1–2 windows, one call); OSCAR-local BRP if SleepHQ file missing.

---

## Anchor catalog

### v1 — session-relative (CPAP/EVE/scan/OSCAR moments)

| Priority (`anchor=auto`) | Anchor id | Resolution rule |
|--------------------------|-----------|-----------------|
| 1 | `eve_scan_overlap` | EVE event within ±120s of `scan-apnea-events` event; prefer longest duration / hypopnea+OA; `startMinute = floor(start_seconds/60) - 5` |
| 2 | `worst_obstructive` | Strongest OA/H/FL from EVE (exclude Recording*) |
| 3 | `worst_central` | Strongest CA from EVE |
| 4 | `worst_leak` | `night_analysis.notable_moments` leak/flow max clock, else scan proxy if OSCAR unavailable |
| 5 | `notable_moment` | First `notable_moments[]` with non-empty `nearby_events` |
| 6 | `worst_spo2` | Only if timed desat index exists on combined night (else skip) |

Explicit anchors call a single rule. **`auto` with `maxWindows=2`:** primary + second non-overlapping cluster (≥30 min apart).

### v2 — journal wall-clock

| Anchor | Rule |
|--------|------|
| `rem`, `deep`, `core`, `awake` | From `journal.sleep_stages_parsed` segments of that label overlapping CPAP `machine_date` usage window (or BRP `start_datetime`..end). Midpoint of overlap → map to session `startMinute`. Include `alignment_confidence` (high/medium/low) and `sleep_window` in `window_selection`. |

If overlap empty: error `no_stage_overlap` with hint to use event anchors.

---

## API (v1)

### Request (`get-waveform-by-date`)

| Param | Required | Notes |
|-------|----------|-------|
| `date` | yes | YYYY-MM-DD |
| `anchor` | no | `auto` (default when `startMinute` omitted), or explicit id |
| `startMinute` | no | Manual override; ignores `anchor` |
| `maxMinutes` | no | Server default; cap from `sleephq.mcp.payload` |
| `maxWindows` | no | 1 or 2 (`auto` only) |
| `windowIndex` | no | 0 or 1 when `maxWindows=2` |
| `teamId`, `cpapClockAdjustSeconds` | no | unchanged |

### Response (additive)

```json
{
  "filename": "…",
  "start_datetime": "…",
  "duration_seconds": 900,
  "channels": [],
  "window_selection": {
    "anchor_requested": "auto",
    "anchor_resolved": "eve_scan_overlap",
    "start_minute": 92,
    "lead_in_minutes": 5,
    "max_minutes": 15,
    "reason": "Human sentence for LLM/report",
    "evidence": [
      { "source": "get-device-events", "label": "Hypopnea", "start_seconds": 5820, "timestamp": "…" },
      { "source": "scan-apnea-events", "type": "hypopnea", "start_seconds": 5817 }
    ],
    "alignment_confidence": null
  }
}
```

Manual override: `anchor_resolved: "manual"`.

### Errors (fail loud)

| Code / message | When |
|----------------|------|
| `no_sleephq_brp` | No BRP file for date |
| `no_anchor_candidates` | `auto` exhausted priorities |
| `no_stage_overlap` | v2 stage anchor, no overlap |
| `invalid_anchor` | Unknown anchor string |

**Never** default to `startMinute=0` on failure.

---

## Architecture

```
WaveformTools.getWaveformByDate
  → WaveformWindowPlanner.plan(date, anchor, maxWindows, windowIndex)
       → DeviceEventService (EVE)
       → WaveformService.scanApneaEvents (BRP)
       → UnifiedNightAnalysisService.analyzeNight (optional, notable_moments only)
       → JournalLookupService (v2 only)
       → MachineDateTimeOffsetLoader / usage window
  → WaveformService.getWaveformByDate(startSeconds from plan)
  → attach window_selection to JSON
```

- Reuse single BRP `fileId` resolution per request where possible.
- Planner is pure ranking + math; downloadable slice stays in `WaveformService`.

---

## Parallel track: journal sleep stages accuracy

**Symptom:** MCP `sleep_stages_summary.minutes_by_stage` can disagree with SleepHQ dashboard cards for the same calendar date.

**Investigation hypotheses:**

1. Wrong journal row matched to date (`JournalLookupService` paging/timezone).
2. `JournalSleepStagesSummary` merged timeline vs naive sum — agent quoting wrong fields.
3. Apple Health segments span wider than “night” shown in UI (UI filters to main sleep episode).
4. Stale journal on `machine_date` vs team `list-journals` source mismatch.

**Deliverables:**

- Unit test with fixture mirroring SleepHQ UI totals for 2026-05-19 (when sample available).
- Expose `sleep_stages_summary.aggregation_method`, `overlap_detected`, `sleep_window` in reports (already partially present).
- Document in playbook: **SleepHQ dashboard is authoritative for wellness stages**; MCP must match or flag `journal_stage_mismatch: true`.
- Optional: `get-journal-by-date` cross-check field `dashboard_parity` after manual verification.

---

## Prompt / recipe updates

- `clinical-deep-dive.md`, `physician-titration-review.md`, `leak-diagnosis.md`, `goose-recipe.yaml`, `payload-budget.md`:
  - Prefer `get-waveform-by-date(date, anchor=auto)` or explicit anchor.
  - Forbid undocumented `startMinute` unless copied from `window_selection` or event `start_seconds`.
- Report template: cite `window_selection.reason` in “Notable windows”.

---

## Testing

| Layer | Cases |
|-------|--------|
| `WaveformWindowPlannerTest` | overlap pick, priority order, manual override, two windows ≥30 min apart |
| `WaveformServiceTest` / tools | `window_selection` present in JSON |
| Integration | Mock SleepHQ file + EVE/scan fixtures |
| Manual | 2026-05-19: anchors hit 01:37 leak+CA and 04:31 hypopnea |

---

## Phasing

| Phase | Scope |
|-------|--------|
| **P0** | Planner + anchor API + errors + tests + prompts |
| **P1** | Journal stage investigation + parity flags |
| **P2** | `rem`/`deep`/… anchors + alignment_confidence |
| **P3** | Bundle tool + OSCAR BRP fallback (if needed) |

---

## Success criteria

- Agent can call `get-waveform-by-date(date=2026-05-19, anchor=auto)` and get windows anchored to 01:37 leak and/or 04:31 hypopnea with evidence JSON.
- No production path silently uses minute 0.
- Sleep stage narrative either matches SleepHQ UI or explicitly flags mismatch.
