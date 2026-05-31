# Data Accuracy Fix — Option A: Surgical Fixes

**Date:** 2026-05-31  
**Branch:** oscar-mcp  
**Scope:** Fix OSCAR channel gaps, add p99.5, fix journal calorie units. No structural refactoring.

---

## Problem Summary

Comparing MCP tool output against OSCAR UI revealed four categories of inaccuracy:

1. **Missing channels in the OSCAR EDF path** — `OscarWaveformStatistics.mapLabelToField()` silently drops `EprPress.*` (EPAP), `MaskPress.*` (mask pressure), `Snore.*`, and `FlowLim.*` because their EDF labels don't match the existing prefix checks. Neither path handles `Ti.*` / `Te.*` (inspiratory/expiratory time — ASV/bilevel devices).

2. **No p99.5 in either path** — OSCAR UI shows min / median / 95% / 99.5% per channel. The OSCAR path (`ChannelStatistics`) only carries one configurable percentile (default p95) and median. The SleepHQ night path (`NightChannelSummary`) carries p99 and p95 but not p99.5.

3. **Journal calories in joules** — `JournalOverlaySupport` passes `active_energy_joules` as-is. The raw value is in joules (~4184× larger than kcal), which is not useful for a "calories" field.

4. **No downsampling in statistics** (confirmed non-issue) — `WaveformDownsampler` only affects the raw waveform visualization tool (`get-waveform`), not any statistics path. No fix needed here.

---

## Changes

### 1. `domain/ChannelStatistics.java`

Add `p995` field after `percentile` (which remains p95):

```java
public record ChannelStatistics(
    String fieldName, String unit,
    double avg, double min, double max,
    double percentile,   // p95
    double p995,         // p99.5
    double median,
    String minAt, String maxAt,
    int minAtSeconds, int maxAtSeconds,
    int sampleCount
) { ... }
```

### 2. `domain/NightChannelSummary.java`

Add `p99_5` between `p99` and `p95` in the JSON output:

```java
@JsonProperty("p99_5") double p995,
```

### 3. `support/ChannelPercentiles.java`

Add a `double pct` overload alongside the existing `int pct` method:

```java
public static double percentile(List<Double> sorted, double pct) {
    if (sorted.isEmpty()) return 0;
    int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
    idx = Math.max(0, Math.min(sorted.size() - 1, idx));
    return sorted.get(idx);
}
```

Same ceil-rank formula as the integer overload.

### 4. `oscar/OscarWaveformStatistics.java`

**`mapLabelToField()` additions** (all checked before the `press` catch-all):

| EDF label prefix | Maps to field |
|---|---|
| `maskpress` | `mask_pressure` |
| `eprpress` or `epapres` | `epap` |
| `snore` | `snore` |
| `flowlim` | `flow_limit` |
| `ti.` or exactly `ti` | `insp_time` |
| `te.` or exactly `te` | `exp_time` |

Order matters: `maskpress` and `eprpress` checks must appear before the `press` catch-all. `tidvol` check already precedes any new `ti` check — no conflict.

**`compute()` change:** compute `p99.5` alongside `p95`, pass both to the `ChannelStatistics` constructor.

### 5. `support/NightSummaryComputer.java`

**`mapPldLabel()` additions:**

| EDF label prefix | Maps to field |
|---|---|
| `ti.` or exactly `ti` | `insp_time` |
| `te.` or exactly `te` | `exp_time` |

**`summarise()` change:** compute `p99.5` via `ChannelPercentiles.percentile(sorted, 99.5)` and pass it as `p995` to the `NightChannelSummary` constructor.

### 6. `oscar/OscarChannelUnitNormalizer.java`

**`scale()` change:** carry `p995` through the scaling factor, same as `percentile` and `median`:

```java
round(stat.p995() * factor)  // when p995 is not NaN
```

Use the same `scaleValue()` helper used for median to preserve NaN for summary-only data.

### 7. `oscar/OscarChannelStatistics.java`

**`fromSummarySession()` change:** pass `Double.NaN` for `p995` (`.000` summary data has no sample distribution, so no percentile is available — consistent with how `percentile` and `median` are already NaN there).

### 8. `support/NightAnalysisSupport.java`

**`channelStatsNode()` change:** emit `p99_5` alongside existing `p95` and `median`:

```java
if (!Double.isNaN(stat.p995())) {
    ch.put("p99_5", stat.p995());
}
```

### 9. `support/JournalOverlaySupport.java`

Add `calories_kcal` as a derived field when `active_energy_joules` is present:

```java
JsonNode joules = journalAttributes.get("active_energy_joules");
if (joules != null && joules.isNumber()) {
    double kcal = Math.round(joules.doubleValue() / 4184.0 * 10.0) / 10.0;
    out.put("calories_kcal", kcal);
}
```

The raw `active_energy_joules` field is preserved unchanged. `calories_kcal` is additive.

---

## Tests

| Test file | Change |
|---|---|
| `ChannelPercentilesTest` | Add test for `percentile(sorted, 99.5)` — fractional pct |
| `OscarWaveformStatisticsTest` | Add label mapping assertions for the 6 new entries |
| `NightSummaryComputerRealPldTest` | Assert `p99_5` is present and in plausible range for pressure, leak_rate |
| `OscarChannelUnitNormalizerConversionTest` | Assert `p995` is scaled correctly alongside `percentile` |

---

## What is NOT changing

- No field renames on existing channels (no breaking JSON changes)
- `WaveformDownsampler` untouched (not a statistics path)
- `JournalLookupService` pagination unchanged (separate concern)
- No structural refactoring of the two label mappers into a shared registry (deferred)

---

## Acceptance Criteria

- [ ] `get-combined-night-by-date` for a night with PLD EDF includes `epap`, `mask_pressure`, `snore`, `flow_limit` channels with p95 and p99_5
- [ ] `get-sleephq-night` channels include `p99_5` for all channels
- [ ] Journal overlay includes `calories_kcal` when `active_energy_joules` is present
- [ ] All existing tests pass
- [ ] New tests pass
