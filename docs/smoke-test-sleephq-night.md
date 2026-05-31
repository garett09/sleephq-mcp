# `get-sleephq-night` smoke test

Prerequisites:

- `./run.sh` running (`Classpath sanity OK` in log)
- `.env`: `SLEEPHQ_MCP_API_KEY`, SleepHQ OAuth (`SLEEPHQ_CLIENT_ID` / `SECRET`)
- Optional but recommended for local-first path: `~/RESMED_DATA` (`SLEEPHQ_LOCAL_DATA_PATH`) with `DATALOG/<YYYYMMDD>/*_PLD.edf`
- Optional O2: `~/SLEEPHQ_O2_RING` (`SLEEPHQ_O2_LOCAL_PATH`)

**Quick automated check:**

```bash
./scripts/smoke-sleephq-night-mcp.sh 2026-05-28
```

Use a night you know has CPAP data in SleepHQ or your mirror. If local mirror is fresh, expect `provenance.cpap_source=local`.

---

## Goose manual smoke (paste block)

```
get-sleephq-night smoke test — PASS/FAIL only, no clinical report.

Rules:
- MCP tools only; copy numbers verbatim — do not invent channels or percentiles.
- No charts / autovisualiser.
- On tool error: FAIL + exact message, continue.
- TEST_DATE = a recent night with CPAP in SleepHQ (or your newest from list-machine-dates).

## Phase 0 — server + tool registration

1. tools/list (or rely on recipe index)
   PASS if: `get-sleephq-night` appears (37 tools total after this feature).

2. get-configured-defaults
   PASS if: returns team / machine defaults (informational).

## Phase 1 — primary tool

3. get-sleephq-night(date=TEST_DATE)
   Parse JSON root (not nested under another key).

   | Check | PASS when |
   |-------|-----------|
   | source | `source` === `"sleephq"` |
   | date | `date` === TEST_DATE |
   | coverage flags | `coverage.cpap` and `coverage.oximetry` are booleans |
   | cpap block | IF `coverage.cpap` true: `cpap.channels` exists with at least `pressure` |
   | cpap stats shape | Each channel has: `unit`, `p99`, `p99_5`, `p95`, `median`, `min`, `max`, `avg`, `count` |
   | p99 present | `pressure.p99` is a number (not only p95 from machine_date); `p99_5` also present |
   | cpap omitted when false | IF `coverage.cpap` false: no `cpap` object; `coverage.cpap_reason` present (e.g. `no_sleephq_pld`) |
   | o2 omitted when false | IF `coverage.oximetry` false: no `oximetry` object; `coverage.oximetry_reason` if no O2 |
   | provenance | `provenance` exists: `cpap_source` and/or `o2_source` in `local` \| `sleephq_api` when that side has data |
   | session identity | Each `provenance.cpap_sessions[]` entry uses `filename` (not `name`); `file_id` when source was API |
   | validation optional | IF present: entries have `our_p95`, `sleephq_p95`, `agree` — note any `agree=false` as INFO not FAIL |
   | no hallucination fields | Root has NO invented sleep-stage or AHI block (those stay on get-combined-night-by-date) |

4. get-combined-night-by-date(date=TEST_DATE)
   PASS if: still returns `data` / `journal` / `coverage` (no regression); sleep stages remain here, not on get-sleephq-night.

## Phase 2 — combine recipe (read-only)

5. Confirm recipe literacy (no tool call required if you trust deploy)
   PASS if: goose-recipe mentions `get-sleephq-night`, `### Nightly channel summary`, and source precedence (cloud → get-sleephq-night → OSCAR fallback).

## Deliverable

### Smoke test — get-sleephq-night
- TEST_DATE: …
- cpap_source / o2_source: …
- cpap_reason (if any): …
- Channel count (cpap): …
- validation agree mismatches: …
- Overall: PASS | FAIL
```

---

## What PASS looks like (sanity)

- **Local mirror hit:** `provenance.cpap_source` = `local`, optional `local_mirror_synced_at` / `local_mirror_age_hours`
- **API fallback:** `provenance.cpap_source` = `sleephq_api`, sessions include `file_id`
- **Nine PLD fields** (when device reports them): `mask_pressure`, `pressure`, `epap`, `leak_rate`, `resp_rate`, `tidal_volume`, `minute_vent`, `snore`, `flow_limit`
- **Leak unit:** `leak_rate.unit` = `L/min` (not L/s)
- **Per-channel stats fields:** `unit`, `p99`, `p99_5` (99.5th percentile, matches OSCAR UI display), `p95`, `median`, `min`, `max`, `avg`, `count`

## Source / percentile notes

- **CPAP and O2:** local `RESMED_DATA` / `SLEEPHQ_O2_RING` first; API only when that night has no local files.
- **Multi-session nights:** all PLD/O2 samples **concatenated** per channel, then one percentile pass (same as OSCAR on the full night).
- **Percentiles:** every finite sample after unit scaling — **no** therapy or large-leak exclusions. `time_above_24_l_min_*` markers only count samples &gt;24 L/min. Validate leak against `leak_95th` when present.

## Common FAIL causes

| Symptom | Likely cause |
|---------|----------------|
| `no_sleephq_data_for_date` | No PLD and no O2 for that date in mirror or API |
| `coverage.cpap_reason=no_sleephq_pld` | No `DATALOG/<date>/` locally and no team PLD files in cloud |
| Tool missing from list | Stale JVM — `./stop.sh` then `./run.sh` |
| 401 on MCP | Wrong `X-SleepHQ-MCP-Key` |
