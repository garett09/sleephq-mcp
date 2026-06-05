# Data Accuracy Fix — Option A: Surgical Fixes

**Date:** 2026-05-31  
**Branch:** oscar-mcp  
**Scope:** Fix OSCAR EDF channel gaps, add p99.5 on both statistics paths, fix journal calorie units. No structural refactoring.

**Verified against codebase:** 2026-05-31 (Cursor review of `oscar-mcp` working tree).

---

## Problem Summary

Comparing MCP tool output against OSCAR UI revealed four categories of inaccuracy:

1. **Missing channels in the OSCAR EDF path** — `OscarWaveformStatistics.mapLabelToField()` silently drops `EprPress.*` (EPAP), `MaskPress.*` (mask pressure), `Snore.*`, and `FlowLim.*` because their EDF labels don't match the existing `flow` / `press` / `leak` prefix checks. Neither statistics path handles `Ti.*` / `Te.*` (inspiratory/expiratory time — ASV/bilevel devices).

   **Not the same gap on `get-sleephq-night`:** `NightSummaryComputer.mapPldLabel()` already maps `MaskPress`, `EprPress`, `Snore`, and `FlowLim` (see `NightSummaryComputerTest` and `NightSummaryComputerRealPldTest`). Only Ti/Te and p99.5 remain for that path.

2. **No p99.5 in either path** — OSCAR UI shows min / median / 95% / 99.5% per channel. The OSCAR path (`ChannelStatistics`) only carries one configurable percentile (default p95 from `oscar.analysis.percentile`) and median. The SleepHQ night path (`NightChannelSummary`) carries p99 and p95 but not p99.5. Neither path emits p99 on OSCAR EDF stats today (`NightAnalysisSupport.channelStatsNode()` only outputs `p95` + conditional `median`).

3. **Journal calories in joules** — `JournalOverlaySupport` passes `active_energy_joules` as-is. Sample fixture value `1234000` joules ≈ 295 kcal at ÷4184 — useful only when converted.

4. **No downsampling in statistics** (confirmed non-issue) — `WaveformDownsampler` is used only from `WaveformService` (`get-waveform`). No fix needed.

---

## Current state (partial work on branch)

Already present in the working tree (do not re-implement):

| Area | Status |
|------|--------|
| `NightSummaryComputer.mapPldLabel()` | `mask_pressure`, `epap`, `snore`, `flow_limit`, leak/pressure/resp/tidal/minvent |
| `NightSummaryComputerTest` / `NightSummaryComputerRealPldTest` | Nine PLD fields on real fixture |
| `OscarChannelUnitNormalizer.conversionFor("leak_rate", …)` | L/s → L/min for SleepHQ night path |

Still required by this spec: OSCAR `mapLabelToField`, p99.5 everywhere, Ti/Te on both mappers, journal `calories_kcal`, `channelStatsNode` + record constructor ripple.

---

## Pre-existing naming note (out of scope)

| Path | Leak field name |
|------|-----------------|
| OSCAR EDF + catalog (`OscarChannelCatalog`, `OscarWaveformStatistics`) | `leak` |
| SleepHQ night (`NightSummaryComputer`, `get-sleephq-night`) | `leak_rate` |

New OSCAR EDF mappings use SleepHQ-style names (`mask_pressure`, `epap`, …) for cross-tool comparison, but **leak stays `leak`** on `night_analysis.channels` to match `OscarChannelCatalog`. Renaming to `leak_rate` on the OSCAR path is deferred (would affect summary merge keys and trend rows).

---

## Changes

### 1. `domain/ChannelStatistics.java`

Add `p995` after `percentile` (which remains p95):

```java
public record ChannelStatistics(
    String fieldName, String unit,
    double avg, double min, double max,
    double percentile,   // p95 (configurable via oscar.analysis.percentile for EDF)
    double p995,         // p99.5 — fixed, always computed from samples when available
    double median,
    String minAt, String maxAt,
    int minAtSeconds, int maxAtSeconds,
    int sampleCount
) { ... }
```

**Constructor ripple:** update every `new ChannelStatistics(...)` call site:

- `oscar/OscarWaveformStatistics.java` (`compute`)
- `oscar/OscarChannelStatistics.java` (`fromSummarySession`)
- `oscar/OscarChannelUnitNormalizer.java` (`scale`)
- `test/.../OscarEventCorrelatorTest.java`
- `test/.../OscarChannelUnitNormalizerTest.java` (helper factory, if present)

### 2. `domain/NightChannelSummary.java`

Add `p99_5` between `p99` and `p95` in the record (JSON key order follows record field order):

```java
@JsonProperty("unit") String unit,
@JsonProperty("p99") double p99,
@JsonProperty("p99_5") double p995,
@JsonProperty("p95") double p95,
@JsonProperty("median") double median,
// min, max, avg, count, markers unchanged
```

### 3. `support/ChannelPercentiles.java`

Add a `double pct` overload alongside the existing `int pct` method (same ceil-rank formula):

```java
public static double percentile(List<Double> sorted, double pct) {
    if (sorted.isEmpty()) return 0;
    int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
    idx = Math.max(0, Math.min(sorted.size() - 1, idx));
    return sorted.get(idx);
}
```

Optional: delegate `percentile(sorted, int pct)` to the double overload to avoid drift.

### 4. `oscar/OscarWaveformStatistics.java`

**`mapLabelToField()` additions** (all checked before the `press` catch-all; mirror order in `NightSummaryComputer` where applicable):

| EDF label prefix | Maps to field |
|---|---|
| `maskpress` | `mask_pressure` |
| `eprpress` or `epapres` | `epap` |
| `snore` | `snore` |
| `flowlim` | `flow_limit` |
| `ti.` or exactly `ti` | `insp_time` |
| `te.` or exactly `te` | `exp_time` |

Order matters:

- `maskpress` / `eprpress` / `epapres` before `press`.
- `tidvol` already precedes `ti` — no conflict.
- `ti` / `te` before `press` (avoid `Press` false positives).

**`compute()` change:** compute p99.5 via `ChannelPercentiles.percentile(sorted, 99.5)` alongside existing configurable p95; pass both into `ChannelStatistics`. Continue calling `OscarChannelUnitNormalizer.normalize(raw)` on the result.

New waveform fields (`mask_pressure`, `epap`, etc.) use `normalizeWithCatalogDefault()` unless unit rules require otherwise. No change to the `leak` / `flow` L/min switch unless leak is renamed (deferred).

### 5. `support/NightSummaryComputer.java`

**Already implemented:** `maskpress`, `eprpress`, `press`, `leak`, `snore`, `flowlim`, etc.

**`mapPldLabel()` additions only:**

| EDF label prefix | Maps to field |
|---|---|
| `ti.` or exactly `ti` | `insp_time` |
| `te.` or exactly `te` | `exp_time` |

**Optional parity:** add `epapres` alongside existing `eprpress` (OSCAR mapper should use the same aliases).

**`summarise()` change:** compute p99.5 via `ChannelPercentiles.percentile(sorted, 99.5)` and pass as `p995` in the `NightChannelSummary` constructor (between `p99` and `p95` arguments).

### 6. `oscar/OscarChannelUnitNormalizer.java`

**`scale()` change:** carry `p995` through the scaling factor using `scaleValue(stat.p995(), factor)` (same NaN preservation as `median`):

```java
scaleValue(stat.p995(), factor),
```

Do not use bare `round(stat.p995() * factor)` — summary-only rows keep NaN percentiles.

### 7. `oscar/OscarChannelStatistics.java`

**`fromSummarySession()` change:** pass `Double.NaN` for `p995` (`.000` summary has no sample distribution — consistent with `percentile` and `median` already NaN there).

### 8. `support/NightAnalysisSupport.java`

**`channelStatsNode()` change:** emit `p99_5` when present (do not add `p99` here — OSCAR EDF path does not compute it):

```java
ch.put("p95", stat.percentile());
if (!Double.isNaN(stat.p995())) {
    ch.put("p99_5", stat.p995());
}
if (!Double.isNaN(stat.median())) {
    ch.put("median", stat.median());
}
```

### 9. `support/JournalOverlaySupport.java`

After the `WELLNESS_KEYS` copy loop in `buildWellnessObject(...)`, derive `calories_kcal` when `active_energy_joules` is present:

```java
JsonNode joules = journalAttributes.get("active_energy_joules");
if (joules != null && joules.isNumber()) {
    double kcal = Math.round(joules.doubleValue() / 4184.0 * 10.0) / 10.0;
    out.put("calories_kcal", kcal);
}
```

- Raw `active_energy_joules` unchanged (still copied via `WELLNESS_KEYS`).
- **Do not** add `calories_kcal` to `WELLNESS_KEYS` — it is derived, not an upstream journal attribute.

---

## Tests

| Test file | Change |
|---|---|
| `ChannelPercentilesTest` | `percentile(sorted, 99.5)` — fractional pct; same ceil-rank index as p95 on fixture |
| `OscarWaveformStatisticsTest` | Label mapping for all 6 new OSCAR entries (mask, epap, snore, flow_limit, insp_time, exp_time) |
| `NightSummaryComputerTest` | Ti/Te mapping; optional `epapres` alias |
| `NightSummaryComputerRealPldTest` | Assert `p99_5` present and plausible on `pressure`, `leak_rate` (fixture may lack Ti/Te) |
| `NightChannelSummaryTest` | Constructor arity + JSON contains `"p99_5"` |
| `OscarChannelUnitNormalizerConversionTest` | `p995` scaled with leak L/s→L/min (use `normalize()` on a stat with non-NaN p995) |
| `OscarChannelStatisticsTest` | `fromSummarySession` → `p995` is NaN |
| `OscarEventCorrelatorTest` | Update all `ChannelStatistics` constructors (extra `p995` arg) |
| `OscarChannelUnitNormalizerTest` | Update test helper factory if it builds `ChannelStatistics` |
| `JournalOverlaySupportTest` | `calories_kcal` ≈ 295.0 when `active_energy_joules` = 1234000 |

Run: `./mvnw test`

---

## Documentation (same PR or immediate follow-up)

| File | Update |
|------|--------|
| `docs/smoke-test-sleephq-night.md` | cpap stats shape: require `p99_5` on each channel |
| `src/main/java/.../tools/NightTools.java` | `get-sleephq-night` description: mention `p99_5` |
| `docs/sleephq-openapi-gap.md` | Optional: document MCP-only `p99_5` / `calories_kcal` |

---

## What is NOT changing

- No renames on **existing** channel keys (`leak` vs `leak_rate` stays as today)
- No breaking JSON removals; `p99_5` and `calories_kcal` are additive
- `WaveformDownsampler` untouched
- `JournalLookupService` pagination untouched
- `NightSummaryValidation` still compares p95/median only (p99.5 validation deferred)
- No shared label-registry refactor (deferred)

---

## Acceptance Criteria

- [ ] `get-combined-night-by-date` (`night_analysis.channels`) for a night with PLD EDF includes `epap`, `mask_pressure`, `snore`, `flow_limit` (when present in EDF), each with `p95` and `p99_5` (not `p99`)
- [ ] Same tool includes `insp_time` / `exp_time` when Ti/Te channels exist in PLD (device-dependent)
- [ ] `get-sleephq-night` `cpap.channels.*` and `oximetry.channels.*` include `p99_5` on every summarised channel (alongside existing `p99`, `p95`, `median`)
- [ ] Journal overlay on `get-night` / `get-combined-night-by-date` / comparison includes `calories_kcal` when `active_energy_joules` is present
- [ ] `./mvnw test` — all existing and new tests pass
