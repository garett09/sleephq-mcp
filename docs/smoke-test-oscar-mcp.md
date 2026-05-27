# OSCAR MCP smoke test (Goose)

Prerequisites: `./run.sh` running (`Classpath sanity OK`), Goose connected to `http://localhost:8080/mcp` with `X-SleepHQ-MCP-Key`.

**Quick automated check (Phase 1 core):** `./scripts/smoke-oscar-mcp.sh 2026-05-21`

Paste the block below into a new Goose chat.

---

```
OSCAR output cleanup — smoke test only (branch oscar-mcp).

Rules:
- Call MCP tools only; do not invent numbers.
- No clinical interpretation — PASS/FAIL checklist only.
- If a tool errors, record FAIL + the error text and continue.
- Use `last_session_date` from get-oscar-status as TEST_DATE when reachable; else try 2026-05-18, then the newest date you can find from status.

## Phase 0 — connectivity

1. get-oscar-status
   PASS if: configured=true AND reachable=true AND session_count>0

## Phase 1 — single night (full night_analysis shape)

2. get-night-analysis(date=TEST_DATE)
   Inspect the root JSON (this IS night_analysis).

   PASS/FAIL table — report each row:

   | Check | PASS when |
   |-------|-----------|
   | no calendar_date | root has no field `calendar_date` |
   | coverage split | `coverage.oscar_edf_pld` and `coverage.oscar_edf_pld_stats` exist |
   | event channels excluded | `channels` has NO keys among: obstructive, clear_airway, hypopnea, rera, central_apnea, obstructive_apnea (event IDs must not appear as channel stats) |
   | flow_rate_hi_res | IF any flow channel exists, note `channels.flow_rate_hi_res` OR legacy flow keys — informational only |
   | canonical event keys (subset) | Every key in `events.counts` exists in `events.summary_counts` with the **same integer value**; extra keys only in `summary_counts` are OK (typically zero-count `.000` dashboard slots) |
   | no legacy count keys | `events.counts` has NO keys like `obstructive_apnea`, `central_apnea` |
   | event_counts_agree | `events.event_counts_agree` === true when `summary_counts` present |
   | authority | When `summary_counts` present: `events.event_count_authority` === `"oscar_summary_000"`; when EVE-only: `"oscar_eve_edf"` |
   | no recording markers | `events.timed_sample` absent OR no label contains "Recording" (case-insensitive) |
   | session_metric opaque | IF `channels.session_metric` exists, one line: "present — do not interpret clinically" |

3. get-oscar-events(date=TEST_DATE, detail=summary)
   PASS if: has counts; default summary shape (no huge event list); timed_sample cap reasonable if present

4. get-combined-night-by-date(date=TEST_DATE)
   PASS if: top-level `night_analysis` present when OSCAR ok; repeat Phase 1 checks on `night_analysis` subtree only
   Also note: SleepHQ `data` / `journal` / `coverage` still present (no regression)

## Phase 2 — trend slim vs full

5. get-oscar-trend(days=7)   # default detail=summary
   PASS/FAIL:

   | Check | PASS when |
   |-------|-----------|
   | row count | `nights` array length >= 1 |
   | slim shape | each row has NO: channels, timed_sample, notable_moments, data_sources, coverage, calendar_date |
   | slim has | each row has: date, session, respiratory_indices, events (subset), therapy |
   | sleephq overlay | at least one row has `sleephq_ahi_per_hr` OR `sleephq` object (if none, note "SKIP overlay: no machine_date") |
   | windowing note | one line: dates in `nights[].date` are session nights only (gaps OK) |

6. get-oscar-trend(days=7, detail=full)
   PASS if: same night count as step 5; first row includes `channels` and `coverage` (full shape)

7. Compare payload size (qualitative)
   Report: approximate top-level key count for nights[0] in step 5 vs step 6

## Phase 3 — mechanics slice

8. get-mechanics(date=TEST_DATE)
   PASS if: returns channels and respiratory_indices with no event-channel keys in channels.*

## Deliverable

Markdown summary:

### Smoke test — oscar-mcp
- TEST_DATE used: …
- Overall: PASS / FAIL
- Table of all checks (Check | PASS/FAIL | Notes)
- If any FAIL: paste the smallest JSON fragment that proves the failure

Do not recommend therapy changes. End after the table.
```

## Event counts semantics (for reviewers)

| Field | Source | Key shape |
|-------|--------|-----------|
| `events.counts` | EVE.edf timed annotations | **Sparse** — only keys with ≥1 EVE event |
| `events.summary_counts` | Summaries/*.000 `m_cnt` hash | **Full** — all counted dashboard channels, including zeros |
| `events.event_counts_agree` | Builder | `eve_total == summary_total` (totals, not per-key equality) |
| `events.event_count_authority` | Builder | `oscar_summary_000` when summary present; else `oscar_eve_edf` |

See [`context/cross-validation-findings.md`](../context/cross-validation-findings.md).
