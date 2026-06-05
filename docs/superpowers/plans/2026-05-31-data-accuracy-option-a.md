# Data Accuracy — Option A Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix OSCAR EDF channel mapping gaps, add p99.5 to both statistics paths, and surface calories_kcal from journal joules — making MCP output match what OSCAR's UI actually shows.

**Already on branch (do not re-implement):** `NightSummaryComputer.mapPldLabel()` already maps `mask_pressure`, `epap`, `snore`, `flow_limit`, and the nine PLD fields covered by `NightSummaryComputerRealPldTest`. Task 5 only adds Ti/Te + `epapres` alias. OSCAR EDF mapping (Task 4) is the main channel gap for `get-combined-night-by-date`.

**Architecture:** All changes are purely additive or in-place fixups within existing classes. No new services, no architectural layers. Record fields are added (triggering constructor ripple in ~9 call sites), label-mapper methods gain new `if` entries, and one JSON builder gets a derived field. Tests are updated in lockstep with each structural change to maintain a green suite throughout.

**Tech Stack:** Java 21 records, JUnit 5, AssertJ, Jackson, Maven (`./mvnw test`)

**Spec:** `docs/superpowers/specs/2026-05-31-data-accuracy-option-a-design.md`

---

## File Map

| File | Change |
|------|--------|
| `src/main/java/.../support/ChannelPercentiles.java` | Add `double pct` overload |
| `src/main/java/.../domain/ChannelStatistics.java` | Add `p995` field (structural) |
| `src/main/java/.../oscar/OscarWaveformStatistics.java` | Fix label map + compute p99.5 |
| `src/main/java/.../oscar/OscarChannelStatistics.java` | Pass NaN for p995 |
| `src/main/java/.../oscar/OscarChannelUnitNormalizer.java` | Carry p995 through `scale()` |
| `src/main/java/.../domain/NightChannelSummary.java` | Add `p995` field (structural) |
| `src/main/java/.../support/NightSummaryComputer.java` | Ti/Te labels + compute p99.5 |
| `src/main/java/.../support/NightAnalysisSupport.java` | Emit `p99_5` in JSON |
| `src/main/java/.../support/JournalOverlaySupport.java` | Add `calories_kcal` |
| `src/test/.../support/ChannelPercentilesTest.java` | Add fractional-pct test |
| `src/test/.../oscar/OscarWaveformStatisticsTest.java` | Add new mapping tests |
| `src/test/.../oscar/OscarChannelStatisticsTest.java` | Assert `p995` is NaN |
| `src/test/.../oscar/OscarChannelUnitNormalizerTest.java` | Update `stat()` helper |
| `src/test/.../oscar/OscarChannelUnitNormalizerConversionTest.java` | Assert `p995` scales on `normalize()` |
| `src/test/.../oscar/OscarEventCorrelatorTest.java` | Update 6 constructors |
| `src/test/.../support/NightSummaryComputerTest.java` | Add Ti/Te + p99_5 assertions |
| `src/test/.../domain/NightChannelSummaryTest.java` | **Modify** — update constructors + add `p99_5` tests |
| `src/test/.../support/NightAnalysisSupportChannelNodeTest.java` | **Create** — `channelStatsNode` emits `p99_5` |
| `src/test/.../support/NightSummaryComputerRealPldTest.java` | Assert `p99_5` plausible |
| `src/test/.../support/JournalOverlaySupportTest.java` | Assert `calories_kcal` |
| `docs/smoke-test-sleephq-night.md` | Require `p99_5` in channel shape |
| `src/main/java/.../tools/NightTools.java` | Mention `p99_5` in `get-sleephq-night` description |
| `docs/sleephq-openapi-gap.md` | Optional: document MCP-only `p99_5` / `calories_kcal` |

All paths use the package prefix `com.adriangarett.sleephqmcp`.

---

## Task 1 — Fractional percentile overload in `ChannelPercentiles`

**Files:**
- Modify: `src/main/java/com/adriangarett/sleephqmcp/support/ChannelPercentiles.java`
- Test: `src/test/java/com/adriangarett/sleephqmcp/support/ChannelPercentilesTest.java`

- [ ] **Step 1.1 — Write the failing test**

Add to `ChannelPercentilesTest` (after the existing `percentile_emptyList_returnsZero` test):

```java
@Test
void percentile_fractionalPct_p99_5_ceilRank() {
    // ceil(99.5/100.0 * 200) - 1 = ceil(199.0) - 1 = 198 → value 199.0
    List<Double> sorted = ChannelPercentiles.sortedClean(rangeOneTo(200));
    assertThat(ChannelPercentiles.percentile(sorted, 99.5)).isEqualTo(199.0);
}

@Test
void percentile_fractional_emptyList_returnsZero() {
    assertThat(ChannelPercentiles.percentile(List.of(), 99.5)).isEqualTo(0.0);
}
```

- [ ] **Step 1.2 — Run tests to confirm they fail**

```bash
./mvnw test -Dtest=ChannelPercentilesTest 2>&1 | tail -20
```

Expected: compilation error — method `percentile(List, double)` not found.

- [ ] **Step 1.3 — Add the `double pct` overload to `ChannelPercentiles`**

In `ChannelPercentiles.java`, add immediately after the existing `int pct` overload:

```java
/** Ceil-rank percentile supporting fractional pct (e.g. 99.5). Returns 0 for empty list. */
public static double percentile(List<Double> sorted, double pct) {
    if (sorted.isEmpty()) {
        return 0;
    }
    int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
    idx = Math.max(0, Math.min(sorted.size() - 1, idx));
    return sorted.get(idx);
}
```

- [ ] **Step 1.4 — Run tests to confirm they pass**

```bash
./mvnw test -Dtest=ChannelPercentilesTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`, 6 tests pass.

- [ ] **Step 1.5 — Commit**

```bash
git add src/main/java/com/adriangarett/sleephqmcp/support/ChannelPercentiles.java \
        src/test/java/com/adriangarett/sleephqmcp/support/ChannelPercentilesTest.java
git commit -m "feat(percentile): add fractional-pct overload for p99.5 support"
```

---

## Task 2 — Expand `ChannelStatistics` record + fix all constructor call sites

**Why no TDD here:** Adding a field to a Java record changes the constructor arity and breaks compilation across all call sites simultaneously. The structural fix and all ripple updates must be committed together; then the new field's value can be verified by tests.

**Files:**
- Modify: `src/main/java/com/adriangarett/sleephqmcp/domain/ChannelStatistics.java`
- Modify: `src/main/java/com/adriangarett/sleephqmcp/oscar/OscarWaveformStatistics.java`
- Modify: `src/main/java/com/adriangarett/sleephqmcp/oscar/OscarChannelStatistics.java`
- Modify: `src/main/java/com/adriangarett/sleephqmcp/oscar/OscarChannelUnitNormalizer.java`
- Modify: `src/test/java/com/adriangarett/sleephqmcp/oscar/OscarChannelUnitNormalizerTest.java`
- Modify: `src/test/java/com/adriangarett/sleephqmcp/oscar/OscarEventCorrelatorTest.java`

- [ ] **Step 2.1 — Add `p995` to `ChannelStatistics` record**

Replace the entire record body in `ChannelStatistics.java`:

```java
public record ChannelStatistics(
        String fieldName,
        String unit,
        double avg,
        double min,
        double max,
        double percentile,
        double p995,
        double median,
        String minAt,
        String maxAt,
        int minAtSeconds,
        int maxAtSeconds,
        int sampleCount
) {
    /** Session-relative offset is unknown (e.g. summary-only data with no sample timing). */
    public static final int OFFSET_UNKNOWN = -1;
}
```

- [ ] **Step 2.2 — Update `OscarWaveformStatistics.compute()` to compute and pass p99.5**

In `OscarWaveformStatistics.java`, replace the `compute()` method body from the lines that build `ChannelStatistics raw` onward. The full updated block (after the existing sort/avg calculation):

```java
        double avg = sorted.isEmpty() ? 0 : sum / sorted.size();
        double p = ChannelPercentiles.percentile(sorted, percentile);
        double p995 = ChannelPercentiles.percentile(sorted, 99.5);
        double medianValue = ChannelPercentiles.percentile(sorted, 50);
        String minAt = clockAt(base, sampleRate, minIdx);
        String maxAt = clockAt(base, sampleRate, maxIdx);
        int minAtSeconds = offsetSeconds(sampleRate, minIdx);
        int maxAtSeconds = offsetSeconds(sampleRate, maxIdx);
        if (min == Double.POSITIVE_INFINITY) {
            min = 0;
            max = 0;
        }
        ChannelStatistics raw = new ChannelStatistics(fieldName, unit == null ? "" : unit,
                round(avg), round(min), round(max), round(p), round(p995), round(medianValue),
                minAt, maxAt, minAtSeconds, maxAtSeconds, sorted.size());
        return OscarChannelUnitNormalizer.normalize(raw);
```

- [ ] **Step 2.3 — Update `OscarChannelStatistics.fromSummarySession()` to pass NaN for p995**

In `OscarChannelStatistics.java`, the `new ChannelStatistics(...)` call at line ~27. Replace with:

```java
            ChannelStatistics raw = new ChannelStatistics(
                    field,
                    unit,
                    round(summary.avg()),
                    round(summary.min()),
                    round(summary.max()),
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    null,
                    null,
                    ChannelStatistics.OFFSET_UNKNOWN,
                    ChannelStatistics.OFFSET_UNKNOWN,
                    0);
```

- [ ] **Step 2.4 — Update `OscarChannelUnitNormalizer.scale()` to carry p995**

In `OscarChannelUnitNormalizer.java`, replace the `scale()` method:

```java
    private static ChannelStatistics scale(ChannelStatistics stat, double factor, String unit) {
        return new ChannelStatistics(
                stat.fieldName(),
                unit,
                round(stat.avg() * factor),
                round(stat.min() * factor),
                round(stat.max() * factor),
                round(stat.percentile() * factor),
                scaleValue(stat.p995(), factor),
                scaleValue(stat.median(), factor),
                stat.minAt(),
                stat.maxAt(),
                stat.minAtSeconds(),
                stat.maxAtSeconds(),
                stat.sampleCount());
    }
```

- [ ] **Step 2.5 — Update the `stat()` helper in `OscarChannelUnitNormalizerTest`**

In `OscarChannelUnitNormalizerTest.java`, replace both `stat()` overloads:

```java
    private static ChannelStatistics stat(
            String field, String unit, double avg, double min, double max, double p95) {
        return stat(field, unit, avg, min, max, p95, Double.NaN);
    }

    private static ChannelStatistics stat(
            String field, String unit, double avg, double min, double max, double p95, double median) {
        return new ChannelStatistics(field, unit, avg, min, max, p95, Double.NaN, median,
                "00:00:00", "01:00:00", 0, 3600, 100);
    }
```

- [ ] **Step 2.6 — Update 6 `ChannelStatistics` constructors in `OscarEventCorrelatorTest`**

There are 6 direct `new ChannelStatistics(...)` calls. Each needs `Double.NaN` inserted as the 7th argument (p995, between the percentile and median args).

**Call 1 (line ~18) and Call 2 (line ~43) — identical shape:**
```java
// Before: new ChannelStatistics("leak", "L/min", 10.0, 0.0, 42.0, 30.0, 20.0, "", "22:10:00", ChannelStatistics.OFFSET_UNKNOWN, 600, 100)
// After:
new ChannelStatistics(
        "leak", "L/min", 10.0, 0.0, 42.0, 30.0, Double.NaN, 20.0,
        "", "22:10:00", ChannelStatistics.OFFSET_UNKNOWN, 600, 100)
```

**Call 3 (line ~67) — spo2:**
```java
// Before: new ChannelStatistics("spo2", "%", 97.0, 85.0, 98.0, 96.0, Double.NaN, "22:10:00", "", 600, ChannelStatistics.OFFSET_UNKNOWN, 100)
// After:
new ChannelStatistics(
        "spo2", "%", 97.0, 85.0, 98.0, 96.0, Double.NaN, Double.NaN,
        "22:10:00", "", 600, ChannelStatistics.OFFSET_UNKNOWN, 100)
```

**Call 4 (line ~70):**
```java
// Before: new ChannelStatistics("leak", "L/min", 10.0, 2.0, 42.0, 30.0, Double.NaN, "", "22:15:00", ChannelStatistics.OFFSET_UNKNOWN, 900, 100)
// After:
new ChannelStatistics(
        "leak", "L/min", 10.0, 2.0, 42.0, 30.0, Double.NaN, Double.NaN,
        "", "22:15:00", ChannelStatistics.OFFSET_UNKNOWN, 900, 100)
```

**Call 5 (line ~90):**
```java
// Before: new ChannelStatistics("leak", "L/min", 10.0, 0.0, 42.0, 30.0, Double.NaN, "", "22:08:20", ChannelStatistics.OFFSET_UNKNOWN, 500, 100)
// After:
new ChannelStatistics(
        "leak", "L/min", 10.0, 0.0, 42.0, 30.0, Double.NaN, Double.NaN,
        "", "22:08:20", ChannelStatistics.OFFSET_UNKNOWN, 500, 100)
```

**Call 6 (line ~114) — summaryOnly:**
```java
// Before: new ChannelStatistics("leak", "L/min", 10.0, 0.0, 42.0, 30.0, Double.NaN, "", "", ChannelStatistics.OFFSET_UNKNOWN, ChannelStatistics.OFFSET_UNKNOWN, 0)
// After:
new ChannelStatistics(
        "leak", "L/min", 10.0, 0.0, 42.0, 30.0, Double.NaN, Double.NaN,
        "", "", ChannelStatistics.OFFSET_UNKNOWN, ChannelStatistics.OFFSET_UNKNOWN, 0)
```

- [ ] **Step 2.7 — Verify compilation and all tests pass**

```bash
./mvnw test 2>&1 | tail -30
```

Expected: `BUILD SUCCESS`. If any test fails with a wrong-arity constructor error, you missed a call site — grep for it:
```bash
grep -rn "new ChannelStatistics" src/
```

- [ ] **Step 2.8 — Add p995 NaN assertion to `OscarChannelStatisticsTest`**

In `OscarChannelStatisticsTest.java`, add to the existing `fromSummarySession_percentileIsNaNWhenUnknown` test (or as a separate test):

```java
@Test
void fromSummarySession_p995IsNaNWhenUnknown() {
    Map<String, ChannelStatistics> stats = OscarChannelStatistics.fromSummarySession(mixedSession());
    ChannelStatistics pressure = stats.get("pressure");
    assertThat(pressure).isNotNull();
    assertThat(pressure.p995()).isNaN();
}
```

- [ ] **Step 2.9 — Run tests to confirm the new assertion passes**

```bash
./mvnw test -Dtest=OscarChannelStatisticsTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2.10 — Add `p995` scaling test in `OscarChannelUnitNormalizerConversionTest`**

Add to `OscarChannelUnitNormalizerConversionTest.java` (add imports: `ChannelStatistics`, `static org.assertj.core.api.Assertions.within`):

```java
@Test
void normalize_leakRate_scalesP995_withLitersPerSecondConversion() {
    ChannelStatistics raw = new ChannelStatistics(
            "leak_rate", "L/s", 0.2, 0.1, 0.5, 0.22, 0.48, 0.15,
            "00:00:00", "01:00:00", 0, 3600, 100);
    ChannelStatistics normalized = OscarChannelUnitNormalizer.normalize(raw);
    assertThat(normalized.p995()).isCloseTo(28.8, within(0.01)); // 0.48 L/s × 60
    assertThat(normalized.percentile()).isCloseTo(13.2, within(0.01));
}
```

Run:

```bash
./mvnw test -Dtest=OscarChannelUnitNormalizerConversionTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2.11 — Commit**

```bash
git add src/main/java/com/adriangarett/sleephqmcp/domain/ChannelStatistics.java \
        src/main/java/com/adriangarett/sleephqmcp/oscar/OscarWaveformStatistics.java \
        src/main/java/com/adriangarett/sleephqmcp/oscar/OscarChannelStatistics.java \
        src/main/java/com/adriangarett/sleephqmcp/oscar/OscarChannelUnitNormalizer.java \
        src/test/java/com/adriangarett/sleephqmcp/oscar/OscarChannelUnitNormalizerTest.java \
        src/test/java/com/adriangarett/sleephqmcp/oscar/OscarEventCorrelatorTest.java \
        src/test/java/com/adriangarett/sleephqmcp/oscar/OscarChannelStatisticsTest.java \
        src/test/java/com/adriangarett/sleephqmcp/oscar/OscarChannelUnitNormalizerConversionTest.java
git commit -m "feat(stats): add p995 field to ChannelStatistics; compute p99.5 from EDF samples"
```

---

## Task 3 — Expand `NightChannelSummary` + wire p99.5 in `NightSummaryComputer`

**Files:**
- Modify: `src/main/java/com/adriangarett/sleephqmcp/domain/NightChannelSummary.java`
- Modify: `src/main/java/com/adriangarett/sleephqmcp/support/NightSummaryComputer.java`
- Modify: `src/test/java/com/adriangarett/sleephqmcp/domain/NightChannelSummaryTest.java` (file **already exists**)

**Order matters:** update `NightChannelSummaryTest` in the same commit as the record change so `./mvnw test` compiles throughout.

- [ ] **Step 3.1 — Add `p995` to `NightChannelSummary` record**

Replace the entire record in `NightChannelSummary.java`:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NightChannelSummary(
        @JsonProperty("unit") String unit,
        @JsonProperty("p99") double p99,
        @JsonProperty("p99_5") double p995,
        @JsonProperty("p95") double p95,
        @JsonProperty("median") double median,
        @JsonProperty("min") double min,
        @JsonProperty("max") double max,
        @JsonProperty("avg") double avg,
        @JsonProperty("count") int count,
        @JsonProperty("markers") Map<String, Double> markers
) {}
```

- [ ] **Step 3.2 — Update existing `NightChannelSummaryTest` constructors (compile fix)**

In `NightChannelSummaryTest.java`, insert the `p995` argument **between `p99` and `p95`** in both tests:

```java
        NightChannelSummary s = new NightChannelSummary(
                "cmH2O", 11.2, 11.8, 10.6, 8.4, 4.0, 12.0, 8.6, 14400, null);
        // ...
        assertThat(json).contains("\"p99\":11.2", "\"p99_5\":11.8", "\"p95\":10.6", "\"median\":8.4",
```

```java
        NightChannelSummary s = new NightChannelSummary(
                "L/min", 22, 24, 12, 2, 0, 38, 4.1, 14400,
                Map.of("time_above_24_l_min_seconds", 180.0));
```

Run (expect compile success; tests may fail until Step 3.3):

```bash
./mvnw test -Dtest=NightChannelSummaryTest 2>&1 | tail -20
```

- [ ] **Step 3.3 — Update `NightSummaryComputer.summarise()` to pass p99.5**

In `NightSummaryComputer.java`, replace the `return new NightChannelSummary(...)` statement in `summarise()`:

```java
        return new NightChannelSummary(
                conv.unit(),
                round(ChannelPercentiles.percentile(sorted, 99)),
                round(ChannelPercentiles.percentile(sorted, 99.5)),
                round(ChannelPercentiles.percentile(sorted, 95)),
                round(ChannelPercentiles.percentile(sorted, 50)),
                round(min),
                round(max),
                round(ChannelPercentiles.avg(sorted)),
                sorted.size(),
                markers.isEmpty() ? null : markers);
```

- [ ] **Step 3.4 — Add integration tests to `NightChannelSummaryTest`**

Append to the existing class (keep serialization tests from Step 3.2):

```java
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.NightSummaryComputer;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.within;

    @Test
    void summarise_pressureSamples_jsonContainsp99_5Key() throws Exception {
        List<Double> samples = new ArrayList<>();
        for (int i = 0; i < 90; i++) samples.add(8.0);
        for (int i = 0; i < 10; i++) samples.add(12.0);

        NightChannelSummary s = NightSummaryComputer.summarise("pressure", "cmH2O", samples, 0.5);
        assertThat(s).isNotNull();

        String json = JsonApi.mapper().writeValueAsString(s);
        JsonNode node = JsonApi.mapper().readTree(json);

        assertThat(node.has("p99_5")).isTrue();
        assertThat(node.path("p99_5").asDouble()).isCloseTo(12.0, within(0.01));
        assertThat(node.has("p99")).isTrue();
        assertThat(node.has("p95")).isTrue();
        assertThat(node.has("median")).isTrue();
    }

    @Test
    void summarise_leakRate_p99_5_isPlausible() {
        List<Double> raw = new ArrayList<>();
        for (int i = 0; i < 95; i++) raw.add(0.1);
        for (int i = 0; i < 5; i++) raw.add(0.5);

        NightChannelSummary s = NightSummaryComputer.summarise("leak_rate", "L/s", raw, 0.5);
        assertThat(s.p995()).isCloseTo(30.0, within(0.01));
        assertThat(s.p995()).isGreaterThanOrEqualTo(s.p95());
    }
```

- [ ] **Step 3.5 — Run NightSummary + NightChannelSummary tests**

```bash
./mvnw test -Dtest=NightSummaryComputerTest,NightSummaryComputerRealPldTest,NightChannelSummaryTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3.6 — Commit**

```bash
git add src/main/java/com/adriangarett/sleephqmcp/domain/NightChannelSummary.java \
        src/main/java/com/adriangarett/sleephqmcp/support/NightSummaryComputer.java \
        src/test/java/com/adriangarett/sleephqmcp/domain/NightChannelSummaryTest.java
git commit -m "feat(stats): add p99_5 to NightChannelSummary and NightSummaryComputer"
```

---

## Task 4 — Fix OSCAR EDF label mappings (6 missing channels)

**Files:**
- Modify: `src/main/java/com/adriangarett/sleephqmcp/oscar/OscarWaveformStatistics.java`
- Modify: `src/test/java/com/adriangarett/sleephqmcp/oscar/OscarWaveformStatisticsTest.java`

- [ ] **Step 4.1 — Write failing tests for new mappings**

Add to `OscarWaveformStatisticsTest.java`:

```java
@Test
void mapLabelToField_newMappings_eprPressAndMaskPress() {
    assertEquals("epap",          OscarWaveformStatistics.mapLabelToField("EprPress.2s"));
    assertEquals("epap",          OscarWaveformStatistics.mapLabelToField("EpapRes.2s"));
    assertEquals("mask_pressure", OscarWaveformStatistics.mapLabelToField("MaskPress.2s"));
}

@Test
void mapLabelToField_newMappings_snoreAndFlowLim() {
    assertEquals("snore",       OscarWaveformStatistics.mapLabelToField("Snore.2s"));
    assertEquals("flow_limit",  OscarWaveformStatistics.mapLabelToField("FlowLim.2s"));
}

@Test
void mapLabelToField_newMappings_inspAndExpTime() {
    assertEquals("insp_time", OscarWaveformStatistics.mapLabelToField("Ti.1s"));
    assertEquals("insp_time", OscarWaveformStatistics.mapLabelToField("Ti"));
    assertEquals("exp_time",  OscarWaveformStatistics.mapLabelToField("Te.1s"));
    assertEquals("exp_time",  OscarWaveformStatistics.mapLabelToField("Te"));
}

@Test
void mapLabelToField_pressStillMapsToPresure_whenNoPrefixMatch() {
    assertEquals("pressure", OscarWaveformStatistics.mapLabelToField("Press.2s"));
}

@Test
void mapLabelToField_tidVolStillWins_notInspTime() {
    // "TidVol" starts with "ti" but must remain tidal_volume, not insp_time
    assertEquals("tidal_volume", OscarWaveformStatistics.mapLabelToField("TidVol.2s"));
}

@Test
void mapLabelToField_flowLimBeforeFlowCatchAll() {
    // "FlowLim" starts with "flow" — must not fall through to "flow"
    assertEquals("flow_limit", OscarWaveformStatistics.mapLabelToField("FlowLim.2s"));
    assertEquals("flow",       OscarWaveformStatistics.mapLabelToField("FlowRate.2s"));
}
```

- [ ] **Step 4.2 — Run failing tests to confirm they fail**

```bash
./mvnw test -Dtest=OscarWaveformStatisticsTest 2>&1 | tail -20
```

Expected: multiple failures (methods return `null` or `"flow"` or `"pressure"` instead of new values).

- [ ] **Step 4.3 — Update `mapLabelToField()` in `OscarWaveformStatistics`**

Replace the entire `mapLabelToField()` method:

```java
    static String mapLabelToField(String label) {
        if (label == null) {
            return null;
        }
        String lower = label.toLowerCase(Locale.ROOT);
        if (lower.startsWith("tidvol")) {
            return "tidal_volume";
        }
        if (lower.startsWith("resprate")) {
            return "resp_rate";
        }
        if (lower.startsWith("minvent")) {
            return "minute_vent";
        }
        if (lower.startsWith("flowhires") || lower.startsWith("flowrate2") || lower.equals("flow rate (hi-res)")) {
            return "flow_rate_hi_res";
        }
        if (lower.startsWith("flowlim")) {
            return "flow_limit";
        }
        if (lower.startsWith("flow")) {
            return "flow";
        }
        if (lower.startsWith("maskpress")) {
            return "mask_pressure";
        }
        if (lower.startsWith("eprpress") || lower.startsWith("epapres")) {
            return "epap";
        }
        if (lower.startsWith("snore")) {
            return "snore";
        }
        if (lower.equals("ti") || lower.startsWith("ti.")) {
            return "insp_time";
        }
        if (lower.equals("te") || lower.startsWith("te.")) {
            return "exp_time";
        }
        if (lower.startsWith("press")) {
            return "pressure";
        }
        if (lower.startsWith("leak")) {
            return "leak";
        }
        return null;
    }
```

- [ ] **Step 4.4 — Run all tests**

```bash
./mvnw test -Dtest=OscarWaveformStatisticsTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 4.5 — Run full suite to check no regressions**

```bash
./mvnw test 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4.6 — Commit**

```bash
git add src/main/java/com/adriangarett/sleephqmcp/oscar/OscarWaveformStatistics.java \
        src/test/java/com/adriangarett/sleephqmcp/oscar/OscarWaveformStatisticsTest.java
git commit -m "fix(oscar): map epap, mask_pressure, snore, flow_limit, insp_time, exp_time from EDF labels"
```

---

## Task 5 — Ti/Te label mappings in `NightSummaryComputer` + `epapres` alias

**Files:**
- Modify: `src/main/java/com/adriangarett/sleephqmcp/support/NightSummaryComputer.java`
- Modify: `src/test/java/com/adriangarett/sleephqmcp/support/NightSummaryComputerTest.java`

- [ ] **Step 5.1 — Write failing tests for Ti/Te and epapres alias**

Add to `NightSummaryComputerTest.java`:

```java
@Test
void mapPldLabel_inspAndExpTime() {
    assertThat(NightSummaryComputer.mapPldLabel("Ti.1s")).isEqualTo("insp_time");
    assertThat(NightSummaryComputer.mapPldLabel("Ti")).isEqualTo("insp_time");
    assertThat(NightSummaryComputer.mapPldLabel("Te.1s")).isEqualTo("exp_time");
    assertThat(NightSummaryComputer.mapPldLabel("Te")).isEqualTo("exp_time");
}

@Test
void mapPldLabel_epapresAlias() {
    assertThat(NightSummaryComputer.mapPldLabel("EpapRes.2s")).isEqualTo("epap");
}
```

- [ ] **Step 5.2 — Run to confirm failure**

```bash
./mvnw test -Dtest=NightSummaryComputerTest 2>&1 | tail -20
```

Expected: `mapPldLabel_inspAndExpTime` and `mapPldLabel_epapresAlias` fail.

- [ ] **Step 5.3 — Update `mapPldLabel()` in `NightSummaryComputer`**

Replace the entire `mapPldLabel()` method:

```java
    public static String mapPldLabel(String label) {
        if (label == null) {
            return null;
        }
        String lower = label.toLowerCase(Locale.ROOT);
        if (lower.startsWith("maskpress")) return "mask_pressure";
        if (lower.startsWith("eprpress") || lower.startsWith("epapres")) return "epap";
        if (lower.startsWith("press")) return "pressure";
        if (lower.startsWith("leak")) return "leak_rate";
        if (lower.startsWith("resprate")) return "resp_rate";
        if (lower.startsWith("tidvol")) return "tidal_volume";
        if (lower.startsWith("minvent")) return "minute_vent";
        if (lower.startsWith("snore")) return "snore";
        if (lower.startsWith("flowlim")) return "flow_limit";
        if (lower.equals("ti") || lower.startsWith("ti.")) return "insp_time";
        if (lower.equals("te") || lower.startsWith("te.")) return "exp_time";
        return null;
    }
```

- [ ] **Step 5.4 — Run all tests**

```bash
./mvnw test -Dtest=NightSummaryComputerTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5.5 — Commit**

```bash
git add src/main/java/com/adriangarett/sleephqmcp/support/NightSummaryComputer.java \
        src/test/java/com/adriangarett/sleephqmcp/support/NightSummaryComputerTest.java
git commit -m "fix(night): add Ti/Te label mappings and epapres alias to NightSummaryComputer"
```

---

## Task 6 — Emit `p99_5` in `NightAnalysisSupport.channelStatsNode()`

**Files:**
- Modify: `src/main/java/com/adriangarett/sleephqmcp/support/NightAnalysisSupport.java`
- Create: `src/test/java/com/adriangarett/sleephqmcp/support/NightAnalysisSupportChannelNodeTest.java`

(`NightAnalysisSupportTest.java` already exists for respiratory indices / summary channels — keep `channelStatsNode` tests in a separate class to avoid mixing concerns.)

- [ ] **Step 6.1 — Create failing `NightAnalysisSupportChannelNodeTest`**

Create `src/test/java/com/adriangarett/sleephqmcp/support/NightAnalysisSupportChannelNodeTest.java`:

```java
package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.domain.ChannelStatistics;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NightAnalysisSupportChannelNodeTest {

    @Test
    void channelStatsNode_emitsP99_5_whenPresent() {
        ChannelStatistics stat = new ChannelStatistics(
                "pressure", "cmH2O", 10.0, 7.0, 14.0, 13.0, 13.5, 9.5,
                "02:00:00", "06:00:00", 7200, 21600, 3600);

        ObjectNode node = NightAnalysisSupport.channelStatsNode(Map.of("pressure", stat));
        ObjectNode ch = (ObjectNode) node.path("pressure");

        assertThat(ch.has("p95")).isTrue();
        assertThat(ch.path("p95").asDouble()).isEqualTo(13.0);
        assertThat(ch.has("p99_5")).isTrue();
        assertThat(ch.path("p99_5").asDouble()).isEqualTo(13.5);
        assertThat(ch.has("median")).isTrue();
    }

    @Test
    void channelStatsNode_omitsP99_5_whenNaN() {
        ChannelStatistics stat = new ChannelStatistics(
                "pressure", "cmH2O", 10.0, 7.0, 14.0, 13.0, Double.NaN, 9.5,
                null, null, ChannelStatistics.OFFSET_UNKNOWN, ChannelStatistics.OFFSET_UNKNOWN, 0);

        ObjectNode node = NightAnalysisSupport.channelStatsNode(Map.of("pressure", stat));
        ObjectNode ch = (ObjectNode) node.path("pressure");

        assertThat(ch.has("p99_5")).isFalse();
    }
}
```

- [ ] **Step 6.2 — Run to confirm the test fails (p99_5 not yet emitted)**

```bash
./mvnw test -Dtest=NightAnalysisSupportChannelNodeTest 2>&1 | tail -20
```

Expected: class not found OR test fails with `has("p99_5")` being false.

- [ ] **Step 6.3 — Update `channelStatsNode()` in `NightAnalysisSupport`**

Find the `channelStatsNode()` method. After the `ch.put("p95", stat.percentile())` line, add:

```java
            if (!Double.isNaN(stat.p995())) {
                ch.put("p99_5", stat.p995());
            }
```

The full block should read:

```java
        stats.forEach((key, stat) -> {
            ObjectNode ch = channels.putObject(key);
            ch.put("avg", stat.avg());
            ch.put("min", stat.min());
            ch.put("max", stat.max());
            ch.put("p95", stat.percentile());
            if (!Double.isNaN(stat.p995())) {
                ch.put("p99_5", stat.p995());
            }
            if (!Double.isNaN(stat.median())) {
                ch.put("median", stat.median());
            }
            // (remaining fields unchanged)
```

- [ ] **Step 6.4 — Run tests**

```bash
./mvnw test -Dtest=NightAnalysisSupportChannelNodeTest,NightSummaryComputerTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6.5 — Commit**

```bash
git add src/main/java/com/adriangarett/sleephqmcp/support/NightAnalysisSupport.java \
        src/test/java/com/adriangarett/sleephqmcp/support/NightAnalysisSupportChannelNodeTest.java
git commit -m "feat(oscar): emit p99_5 in channelStatsNode JSON output"
```

---

## Task 7 — Journal `calories_kcal` conversion

**Files:**
- Modify: `src/main/java/com/adriangarett/sleephqmcp/support/JournalOverlaySupport.java`
- Modify: `src/test/java/com/adriangarett/sleephqmcp/support/JournalOverlaySupportTest.java`

- [ ] **Step 7.1 — Write the failing test**

Add to `JournalOverlaySupportTest.java`:

```java
@Test
void buildWellnessObject_derivesCaloriesKcal_fromActiveEnergyJoules() throws Exception {
    String journals = new String(getClass().getResourceAsStream("/journal/list-journals-sample.json").readAllBytes(),
            StandardCharsets.UTF_8);
    var attrs = JsonApi.parse(journals).path("data").get(0).path("attributes");
    // fixture has active_energy_joules = 1234000

    ObjectNode wellness = JournalOverlaySupport.buildWellnessObject(attrs);

    assertThat(wellness.path("active_energy_joules").asLong()).isEqualTo(1234000L);
    // 1234000 / 4184 = 294.97... → rounded to 1 decimal = 295.0
    assertThat(wellness.path("calories_kcal").asDouble()).isEqualTo(295.0);
}

@Test
void buildWellnessObject_noCaloriesKcal_whenActiveEnergyAbsent() {
    var attrs = JsonApi.mapper().createObjectNode();
    attrs.put("date", "2026-05-24");
    attrs.put("step_count", 100);

    ObjectNode wellness = JournalOverlaySupport.buildWellnessObject(attrs);

    assertThat(wellness.path("calories_kcal").isMissingNode()).isTrue();
}
```

- [ ] **Step 7.2 — Run to confirm failure**

```bash
./mvnw test -Dtest=JournalOverlaySupportTest 2>&1 | tail -20
```

Expected: `buildWellnessObject_derivesCaloriesKcal_fromActiveEnergyJoules` fails (field missing).

- [ ] **Step 7.3 — Add `calories_kcal` derivation to `JournalOverlaySupport.buildWellnessObject()`**

In `JournalOverlaySupport.java`, after the existing `WELLNESS_KEYS` copy loop and the `feeling_label` block (and before the `sleep_stages` block), add:

```java
        JsonNode joules = journalAttributes.get("active_energy_joules");
        if (joules != null && joules.isNumber()) {
            double kcal = Math.round(joules.doubleValue() / 4184.0 * 10.0) / 10.0;
            out.put("calories_kcal", kcal);
        }
```

- [ ] **Step 7.4 — Run tests**

```bash
./mvnw test -Dtest=JournalOverlaySupportTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7.5 — Commit**

```bash
git add src/main/java/com/adriangarett/sleephqmcp/support/JournalOverlaySupport.java \
        src/test/java/com/adriangarett/sleephqmcp/support/JournalOverlaySupportTest.java
git commit -m "feat(journal): derive calories_kcal from active_energy_joules (÷4184)"
```

---

## Task 8 — Tighten real-PLD test + run full suite

**Files:**
- Modify: `src/test/java/com/adriangarett/sleephqmcp/support/NightSummaryComputerRealPldTest.java`

- [ ] **Step 8.1 — Add p99_5 assertions to the real PLD test**

In `NightSummaryComputerRealPldTest.java`, after the existing `assertThat(channels).containsKeys(...)` assertion, add:

```java
        // p99_5 must be present and >= p95 on every channel
        for (Map.Entry<String, NightChannelSummary> e : channels.entrySet()) {
            NightChannelSummary ch = e.getValue();
            assertThat(ch.p995())
                    .as("p99_5 should be >= p95 for channel %s", e.getKey())
                    .isGreaterThanOrEqualTo(ch.p95());
        }
        assertThat(channels.get("pressure").p995()).isBetween(4.0, 30.0);
        assertThat(channels.get("leak_rate").p995()).isBetween(0.0, 120.0);
```

- [ ] **Step 8.2 — Run the real PLD test**

```bash
./mvnw test -Dtest=NightSummaryComputerRealPldTest 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8.3 — Run the full test suite**

```bash
./mvnw test 2>&1 | tail -30
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 8.4 — Commit**

```bash
git add src/test/java/com/adriangarett/sleephqmcp/support/NightSummaryComputerRealPldTest.java
git commit -m "test: assert p99_5 present and plausible on real PLD fixture"
```

---

## Task 9 — Documentation updates

**Files:**
- Modify: `docs/smoke-test-sleephq-night.md`
- Modify: `src/main/java/com/adriangarett/sleephqmcp/tools/NightTools.java`

- [ ] **Step 9.1 — Update smoke test channel shape**

In `docs/smoke-test-sleephq-night.md`:

- Table row **cpap stats shape**: add `p99_5` to the required field list (with `p99`, `p95`, `median`, …).
- **Source / percentile notes** (or equivalent): document that `p99_5` matches OSCAR UI 99.5% display.

Example shape reference:

```
p99      – 99th percentile
p99_5    – 99.5th percentile (matches OSCAR UI)
p95      – 95th percentile
median   – 50th percentile
```

- [ ] **Step 9.2 — Update `get-sleephq-night` tool description**

In `NightTools.java`, extend the `@McpTool` description for `get-sleephq-night` to mention `p99_5` alongside `p99/p95/median`.

- [ ] **Step 9.3 — (Optional) `docs/sleephq-openapi-gap.md`**

Add a short note that `p99_5` and `calories_kcal` are MCP-derived fields not in upstream SleepHQ Swagger.

- [ ] **Step 9.4 — Commit**

```bash
git add docs/smoke-test-sleephq-night.md \
        src/main/java/com/adriangarett/sleephqmcp/tools/NightTools.java
# optional:
# git add docs/sleephq-openapi-gap.md
git commit -m "docs: document p99_5 channel shape and calories_kcal"
```

---

## Task 10 — Final verification (acceptance)

- [ ] **Step 10.1 — Full test suite**

```bash
./mvnw test 2>&1 | tail -30
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 10.2 — Spec acceptance checklist** (manual smoke when credentials available)

| Criterion | Verify |
|-----------|--------|
| `get-combined-night-by-date` PLD nights | `night_analysis.channels` has `epap`, `mask_pressure`, `snore`, `flow_limit` when in EDF; each has `p95` + `p99_5` (not `p99`) |
| Ti/Te on combined night | `insp_time` / `exp_time` when device PLD includes Ti/Te |
| `get-sleephq-night` | Every summarised channel has `p99_5` |
| Journal overlay | `calories_kcal` when `active_energy_joules` present |
| `leak` vs `leak_rate` | Combined night uses `leak`; sleephq-night uses `leak_rate` (unchanged) |

---

## Self-Review Checklist

Mapped to `docs/superpowers/specs/2026-05-31-data-accuracy-option-a-design.md`:

| Spec requirement | Task |
|---|---|
| `ChannelStatistics` add p995 | Task 2 |
| `NightChannelSummary` add p99_5 | Task 3 |
| `ChannelPercentiles` double overload | Task 1 |
| OSCAR EDF: epap, mask_pressure, snore, flow_limit, insp_time, exp_time | Task 4 |
| OSCAR `compute()` computes p99.5 | Task 2.2 |
| `OscarChannelUnitNormalizer.scale()` carries p995 | Task 2.4 |
| `OscarChannelStatistics` NaN for p995 | Task 2.3 |
| `NightSummaryComputer` Ti/Te + epapres alias | Task 5 |
| `NightSummaryComputer` p99.5 in summarise() | Task 3.3 |
| `NightAnalysisSupport` emit p99_5 (not p99) | Task 6 |
| `JournalOverlaySupport` calories_kcal | Task 7 |
| Tests: ChannelPercentilesTest | Task 1 |
| Tests: OscarWaveformStatisticsTest | Task 4 |
| Tests: OscarChannelStatisticsTest p995 NaN | Task 2.8 |
| Tests: OscarChannelUnitNormalizerTest stat() helper | Task 2.5 |
| Tests: OscarChannelUnitNormalizerConversionTest p995 scale | Task 2.10 |
| Tests: OscarEventCorrelatorTest 6 constructors | Task 2.6 |
| Tests: NightSummaryComputerTest Ti/Te | Task 5 |
| Tests: NightChannelSummaryTest modify + summarise | Task 3.2, 3.4 |
| Tests: NightSummaryComputerRealPldTest p99_5 | Task 8 |
| Tests: NightAnalysisSupportChannelNodeTest | Task 6 |
| Tests: JournalOverlaySupportTest calories_kcal | Task 7 |
| Docs: smoke-test + NightTools | Task 9 |
| Optional: sleephq-openapi-gap.md | Task 9.3 |
| Acceptance / full suite | Task 10 |
