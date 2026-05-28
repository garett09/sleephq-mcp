# goose-recipe.yaml full-audit sync — design

**Date:** 2026-05-28
**Status:** Approved (design)
**Scope:** Reconcile `goose-recipe.yaml` agent-reading instructions with the LLM-facing output the MCP server actually emits on the `oscar-mcp` branch.

## Background

The `oscar-mcp` branch added OSCAR local-backup tools, contextual waveform windows, and journal stage-alignment changes. Much of the recipe was already updated on this branch (OSCAR tool block, `anchor=auto`, tool count 30 → 36), and a manual smoke test of all 12 steps passes. This audit covers the *remaining* drift between recipe instructions and emitted fields.

The recipe is **agent-reading material**, not internal-field documentation. Edits are limited to fields/behaviors that change how the agent reads a night or selects a tool argument. Internal mechanics (schema version, coverage-presence split, channel-exclusion list, trend overlay field names, respiratory_indices coalescing) are deliberately **excluded** — they are smoke-test / debug signals the agent does not need to read a night correctly.

## Audit method

Built a canonical inventory of emitted fields by reading the output-producing sources:
- `support/JournalSleepStagesSummary.java` — sleep-stage totals + reporting decision
- `support/NightAnalysisSupport.java` — night_analysis events, counts, authority, channels, coverage
- `support/WaveformResponseSupport.java` + `support/WaveformAnchorSupport.java` + `service/WaveformWindowPlanner.java` + `tools/WaveformTools.java` — window_selection, anchor vocabulary, error codes
- `support/McpPayloadHints.java` — payload hint field names
- `config/ClinicalDefaultsSupport.java` — configured-defaults fields
- `service/OscarTrendService.java` — trend slim/full row shape
- `tools/*.java` — `@McpTool` names (confirmed 36 tools, recipe index correct)

Cross-checked field references against the recipe with `grep`.

## Changes

Four edits. Three change agent behavior; one is a guardrail.

### Edit 1 — Sleep-stage reporting field (highest value)

**Location:** recipe line 152 (Journal wellness "Sleep stages") and line 244 (Consumer sleep summary "Sleep Time & Stages").

**Problem:** Line 152 currently instructs the agent to *pick* between `minutes_by_stage_main_episode` and `minutes_by_stage` based on `journal_stage_mismatch` / `overlap_detected`. `JournalSleepStagesSummary` now bakes that decision into the server and emits a single pre-resolved field:
- `minutes_by_stage_for_reporting` — the totals the agent should report
- `reporting_source` — one of `sleephq_dashboard_naive` | `main_sleep_episode` | `merged_timeline`

The recipe instruction is now wrong: it asks the agent to redo a decision the server already made, and names a field selection that can disagree with the server's own choice.

**New rule (line 152):** Report from **`minutes_by_stage_for_reporting`** as the canonical total and cite **`reporting_source`**. Keep: report `core` as **light sleep** (`sleep_cell`); when `overlap_detected`, cite `sleep_window.span_minutes` and do not claim fragmentation from full-span totals alone. Drop the "prefer X else Y" pick instruction.

**Coherence (line 244):** Update the Consumer sleep summary "Sleep Time & Stages" reference to name `minutes_by_stage_for_reporting` (core = light sleep) so the recipe does not contradict itself. Line 150 (general `sleep_stages_summary` narration) needs no change.

**Out of scope:** lines 205 / 231 use the derived `sleep_cell` from `get-comparison` `table_display`, a different code path — leave unchanged.

### Edit 2 — Waveform anchor menu + no-fallback guarantee

**Location:** clinical_deep_dive step 5 (recipe ~line 195) and the payload-budget `get-waveform-by-date` row (~line 362). Optionally the Shared tool literacy waveform line.

**Problem:** Recipe says `anchor=auto` and "cite window_selection" but never lists the anchor vocabulary or the failure contract, so the agent cannot deliberately pick a non-auto anchor and may misread a structured error.

**New content:**
- **Anchor menu** (from `WaveformTools` description / `WaveformAnchorSupport`): `auto, eve_scan_overlap, worst_obstructive, worst_central, worst_leak, notable_moment, rem, deep, core, awake`.
- **No silent fallback:** the planner never falls back to minute 0; on no match it returns a structured error — `no_anchor_candidates` or `no_sleephq_brp` (`IllegalArgumentException` surfaced via `McpResponses.safe()`). The agent should report the error, not invent a window.
- **`worst_leak` → `start_minute=0` is valid**, not a bug, when peak leak occurs at session start (confirmed by smoke test step 3).

### Edit 3 — Event-count authority in night_analysis

**Location:** OSCAR reconcile note (recipe ~line 395, "OSCAR vs SleepHQ EVE/scan").

**Problem:** Current note ("do not merge counts without stating authority") is too vague to act on. `night_analysis` exposes specific fields the agent must use.

**New rule:**
- `counts` is **sparse** — EVE-derived events only.
- `summary_counts` is **full** — all counted `.000` channels including zeros; prefer it for event totals.
- Cite `event_count_authority`: `oscar_summary_000` when summary present, else `oscar_eve_edf`.
- Check `event_counts_agree` before reconciling OSCAR against SleepHQ `get-device-events` / `scan-apnea-events`.

This matches the canonical-event-labels contract already documented in `CLAUDE.md`.

### Edit 4 — `session_metric` opaque (guardrail one-liner)

**Location:** near the OSCAR tool literacy or night_analysis note.

**Content:** `session_metric` (channel `0x1158`) appears in night_analysis output; its semantics are unconfirmed — **do not interpret it clinically**. One line, matching the `CLAUDE.md` convention.

## Explicitly excluded (audited, intentionally dropped)

- `summary_schema_version` — smoke-test staleness signal, not clinical reading.
- `coverage.oscar_edf_pld` vs `oscar_edf_pld_stats` split — internal presence/stats mechanic.
- night_analysis `channels` event-channel exclusion (0x1000–0x1028) — internal classification.
- `get-oscar-trend` per-row `sleephq` overlay field name — already adequately summarized by "detail=summary default".
- `respiratory_indices` oscar_/sleephq_/coalesced field names — the generic reconcile note plus Edit 3 cover authority.

## Validation

- Re-run the recipe smoke test (12 steps) — should still PASS; these are doc-only edits.
- Manual read-through: confirm lines 152 and 244 name the same canonical field and do not contradict line 150.
- `grep` recipe for `minutes_by_stage_main_episode` to confirm no stale "pick between fields" instruction remains where the new rule applies.
- No Java/test changes; no server rebuild required.

## Out of scope

- No code changes to the MCP server.
- No changes to MCP prompt files or static clinical resources.
- Workflow matrix, persona blocks, and confidence/output-format sections unchanged.
