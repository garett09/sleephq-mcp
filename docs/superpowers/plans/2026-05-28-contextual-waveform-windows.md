# Contextual waveform windows — Implementation Plan

> **For agentic workers:** Use `superpowers:subagent-driven-development` or `superpowers:executing-plans`. Check off tasks as you go. Run `./mvnw test` after each task.

**Goal:** Auto-anchor `get-waveform-by-date` to clinical events (and later sleep stages), return `window_selection` metadata, fix journal sleep-stage parity with SleepHQ UI where wrong.

**Architecture:** New `WaveformWindowPlanner` + `WindowSelection` DTO; extend `WaveformTools` / `WaveformService` serialization; parallel journal audit in `JournalSleepStagesSummary` / `JournalLookupService`.

**Tech stack:** Java 21, Spring Boot 3, JUnit 5, `./mvnw test`.

**Design spec:** [`docs/superpowers/specs/2026-05-28-contextual-waveform-windows-design.md`](../specs/2026-05-28-contextual-waveform-windows-design.md)

**Manual validation date:** `2026-05-19` (leak 01:37:01, CA 01:37:12, hypopnea 04:31:00; SleepHQ UI stages ≠ prior MCP report).

---

## File map

**Create:**

| File | Purpose |
|------|---------|
| `domain/WaveformWindowPlan.java` | record: startSeconds, startMinute, anchorResolved, reason, evidence list |
| `domain/WindowEvidence.java` | record: source, label, startSeconds, timestamp optional |
| `service/WaveformWindowPlanner.java` | anchor resolution |
| `test/.../WaveformWindowPlannerTest.java` | planner unit tests |
| `test/.../WaveformToolsWindowSelectionTest.java` | JSON contains `window_selection` |

**Modify:**

| File | Purpose |
|------|---------|
| `tools/WaveformTools.java` | `anchor`, `maxWindows`, `windowIndex` params; call planner |
| `service/WaveformService.java` | optional hook to merge `window_selection` into result JSON |
| `service/DeviceEventService.java` | ensure events expose `start_seconds` for planner (if not already) |
| `resources/prompts/clinical-deep-dive.md` | anchor=auto |
| `resources/prompts/physician-titration-review.md` | anchor usage |
| `resources/prompts/leak-diagnosis.md` | `anchor=worst_leak` |
| `resources/clinical/payload-budget.md` | document window_selection |
| `goose-recipe.yaml` | forbid random startMinute |
| `sleephq-mcp-capabilities.md` | anchor param docs |
| `CLAUDE.md` | one paragraph on contextual waveform |

**P1 journal parity (may add):**

| File | Purpose |
|------|---------|
| `support/JournalSleepStagesSummary.java` | parity flags, episode filter if spec'd |
| `service/JournalLookupService.java` | date match audit |
| `test/.../JournalSleepStagesSummaryTest.java` | 2026-05-19 fixture |

**P2:**

| File | Purpose |
|------|---------|
| `service/WaveformWindowPlanner.java` | stage overlap anchors |
| `support/JournalSleepStageAlignment.java` | CPAP window ↔ parsed segments |

---

## Implementation order

```
P0 planner + tool API + prompts
  → P1 journal parity investigation
  → P2 rem/deep anchors
  → P3 bundle tool (optional, only if requested)
```

---

## P0 — Contextual waveform (core)

### Task 1: Domain types + planner skeleton

- [ ] Add `WaveformWindowPlan`, `WindowEvidence` records.
- [ ] Add `WaveformWindowPlanner` with `plan(String date, String anchor, int maxWindows, int windowIndex, Integer manualStartMinute)`.
- [ ] Throw `IllegalArgumentException` with stable message tokens (`no_anchor_candidates`, etc.).

**Test:** `plan_manualStartMinute_returnsManualAnchor` — startMinute 145 → `anchorResolved=manual`.

### Task 2: EVE + scan overlap resolver

- [ ] Inject `DeviceEventService`, use existing scan path from `WaveformService` (extract package-private helper if needed to avoid double S3 download in one request).
- [ ] Implement `eve_scan_overlap` and `worst_obstructive` / `worst_central`.
- [ ] Lead-in: 5 minutes (config constant `WAVEFORM_LEAD_IN_MINUTES = 5`).

**Test:** Fixture EVE + scan JSON at 04:31:00 → `startMinute` ≈ 86 (depends on session start); assert evidence size ≥ 2.

### Task 3: OSCAR notable_moment + worst_leak

- [ ] Optional `UnifiedNightAnalysisService.analyzeNight` when OSCAR configured.
- [ ] Map `notable_moments[].clock` + session start → seconds (reuse `OscarEventCorrelator` clock parse pattern).
- [ ] `worst_leak` prefers leak channel max moment.

**Test:** Mock night_analysis JSON with leak max at `01:37:01` → plan reason contains "leak".

### Task 4: `auto` priority + second window

- [ ] Implement priority chain from spec.
- [ ] `maxWindows=2`: second cluster ≥ 30 min from first on session timeline.

**Test:** Two events 3h apart → windowIndex 0 and 1 differ.

### Task 5: Wire `WaveformTools`

- [ ] Add `@McpToolParam` for `anchor`, `maxWindows`, `windowIndex`.
- [ ] If `startMinute != null` → skip planner (manual).
- [ ] Else planner → `getWaveformByDate` with computed startSeconds.
- [ ] Attach `window_selection` node to response (new package `support/WaveformResponseSupport` if needed).

**Test:** Mock planner + service → JSON has `window_selection.anchor_resolved`.

### Task 6: Docs + prompts

- [ ] Update prompts and `goose-recipe.yaml` per spec.
- [ ] Update `sleephq-mcp-capabilities.md` tool row for `get-waveform-by-date`.

### Task 7: Verify

- [ ] `./mvnw test`
- [ ] Manual: `./run.sh`, MCP call `get-waveform-by-date(date=2026-05-19, anchor=auto)` — expect evidence for 01:37 and/or 04:31.

---

## P1 — Journal sleep stages parity

### Task 8: Reproduce 2026-05-19 mismatch

- [ ] Call `get-journal-by-date(date=2026-05-19)` and `get-combined-night-by-date` live; save redacted JSON snippet to test fixture `src/test/resources/journal/2026-05-19-sleep-stages.json` (if allowed).
- [ ] Compare `minutes_by_stage` to SleepHQ UI (awake ~14m, REM ~240m, core ~358m, deep ~217m per dashboard).
- [ ] Document root cause in spec appendix or `context/session-decisions.md`.

### Task 9: Fix or flag

- [ ] If wrong journal row: fix `JournalLookupService` date matching (timezone/date boundary).
- [ ] If aggregation: adjust `JournalSleepStagesSummary` OR expose `minutes_by_stage_naive` vs `minutes_by_stage` and `overlap_detected` (already partial).
- [ ] Add `journal.sleep_stages_summary.ui_parity_note` when totals diverge from episode-filtered expectation.
- [ ] Playbook line: **do not claim fragmentation from journal unless `overlap_detected` is false and totals match UI order-of-magnitude.**

**Test:** Fixture expects rem > 60, awake < 30 for 2026-05-19 after fix.

### Task 10: Report guardrails

- [ ] `output-format.md` / goose recipe: sleep column must use `sleep_stages_summary`; if `overlap_detected=true`, report span + caveat.

---

## P2 — Stage anchors (rem / deep)

### Task 11: `JournalSleepStageAlignment`

- [ ] Load CPAP session window from `machine_date` usage times or BRP metadata.
- [ ] Overlap parsed segments; compute midpoint; convert to `startMinute`.
- [ ] Set `alignment_confidence` high if overlap ≥ 5 min, else medium/low.

### Task 12: Enable anchors

- [ ] `anchor=rem|deep|core|awake` in planner.
- [ ] Tests with synthetic segments + session window.

---

## P3 — Optional

- [ ] `get-contextual-waveforms` tool returning `windows[]` (defer unless user requests).

---

## Agent handoff checklist

Before marking done:

- [ ] All P0 tasks checked
- [ ] `./mvnw test` green
- [ ] Manual 2026-05-19 `anchor=auto` produces `window_selection` with leak/hypopnea evidence
- [ ] P1 either fixes 2026-05-19 stages or documents `overlap_detected` + playbook guard
- [ ] No `startMinute=0` fallback on errors
