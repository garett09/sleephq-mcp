# OSCAR AHI scan and event-count authority

## Problem

- `night_analysis.respiratory_indices.ahi_per_hr` was `"see_channels.ahi"` when `.000` structured parse failed.
- Summary-only nights (e.g. 2026-05-18) had channel metadata but no numeric therapy stats.
- EVE with only session markers (e.g. Recording Start) under-reported `total` vs OSCAR dashboard `summary_counts`.

## Solution

1. **`OscarSummaryChannelStats`** — offset-scan `m_avg` (same strategy as `OscarSummaryEventCounts`), merge into `OscarRepository.loadSession` when structured parse returns empty channels.
2. **`respiratory_indices`** — explicit `oscar_ahi_per_hr`, `sleephq_ahi_per_hr`; coalesced `ahi_per_hr` prefers SleepHQ then OSCAR; no string placeholders; omit keys when unknown.
3. **`OscarEventSummaryBuilder`** — filter non-therapy EVE labels; `total` uses `summary_total` when `summary_counts` present; `event_count_authority` documents source.

## Anti-hallucination

- No derived AHI from event counts / duration.
- No placeholder strings.
- Numeric fields only from parsed `.000` or SleepHQ `ahi_summary.av`.
