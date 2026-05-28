# AirView-style Ventilation Reporting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an AirView-style ventilation report — Tidal Volume, Respiratory Rate, Minute Ventilation, each as Maximum/95th/Median (avg) across the span, plus per-night charts — to `physician_titration_review` and `weekly-trend`.

**Architecture:** Hybrid by data domain. Respiratory Rate aggregate is computed in `get-comparison` from SleepHQ `resp_rate_summary`; Tidal Volume + Minute Ventilation aggregates are computed in `get-oscar-trend` from OSCAR PLD waveforms (which requires adding a median/p50 stat). A shared `VentilationSummarySupport` averages per-night stats over days-used. Prompts concatenate the two structured `ventilation_summary` blocks into one table + chart group; the agent copies numbers verbatim.

**Tech Stack:** Java 21 (records, switch expressions), Spring Boot, Jackson, JUnit 5 + AssertJ, Maven (`./mvnw`).

**Spec:** `docs/superpowers/specs/2026-05-28-airview-ventilation-reporting-design.md`

**Key verified facts:**
- SleepHQ summary keys: `{av, max, med, min, upper}` where **`upper` = 95th percentile**. SleepHQ has `resp_rate_summary` but **no** tidal-volume / minute-vent summaries.
- OSCAR PLD carries `RespRate.2s`, `TidVol.2s`, `MinVent.2s`. `OscarWaveformStatistics.compute()` already produces avg/min/max/p95 but **no median**.
- `OscarTrendService.slim()` already lifts `tidal_volume`, `minute_vent`, `resp_rate` from `channels.*` into `therapy.*`, so per-night chart series already exist once median is added.

---

## File Structure

**Create:**
- `src/main/java/com/adriangarett/sleephqmcp/support/VentilationSummarySupport.java` — shared aggregator + per-source extractors.
- `src/test/java/com/adriangarett/sleephqmcp/support/VentilationSummarySupportTest.java` — unit tests for the aggregator and both extractors.

**Modify (Java):**
- `src/main/java/com/adriangarett/sleephqmcp/domain/ChannelStatistics.java` — add `median` field.
- `src/main/java/com/adriangarett/sleephqmcp/oscar/OscarWaveformStatistics.java:137` — pass real median (p50).
- `src/main/java/com/adriangarett/sleephqmcp/oscar/OscarChannelUnitNormalizer.java:78` — scale median.
- `src/main/java/com/adriangarett/sleephqmcp/oscar/OscarChannelStatistics.java:27` — pass `Double.NaN` median (summary-only).
- `src/main/java/com/adriangarett/sleephqmcp/support/NightAnalysisSupport.java:30` — emit `median` when not NaN.
- `src/main/java/com/adriangarett/sleephqmcp/service/ComparisonService.java` — attach RR `ventilation_summary`.
- `src/main/java/com/adriangarett/sleephqmcp/service/OscarTrendService.java` — attach TV/MV/RR `ventilation_summary`.

**Modify (tests touched by the record change):**
- `src/test/java/com/adriangarett/sleephqmcp/oscar/OscarChannelUnitNormalizerTest.java` — add median overload.
- `src/test/java/com/adriangarett/sleephqmcp/oscar/OscarEventCorrelatorTest.java` — add median arg to 2 constructors.

**Modify (clinical resources / prompts):**
- `src/main/resources/clinical/output-format.md` — AirView span table spec.
- `src/main/resources/clinical/autovisualiser.md` — Ventilation mechanics chart group.
- `src/main/resources/prompts/physician-titration-review.md` — two new sections.
- `src/main/resources/prompts/weekly-trend.md` — two new sections.
- `goose-recipe.yaml` — verify resource wiring.

---

## Task 1: Add `median` to `ChannelStatistics` and emit it

**Files:**
- Modify: `src/main/java/com/adriangarett/sleephqmcp/domain/ChannelStatistics.java`
- Modify: `src/main/java/com/adriangarett/sleephqmcp/oscar/OscarWaveformStatistics.java`
- Modify: `src/main/java/com/adriangarett/sleephqmcp/oscar/OscarChannelUnitNormalizer.java`
- Modify: `src/main/java/com/adriangarett/sleephqmcp/oscar/OscarChannelStatistics.java`
- Modify: `src/main/java/com/adriangarett/sleephqmcp/support/NightAnalysisSupport.java`
- Test: `src/test/java/com/adriangarett/sleephqmcp/oscar/OscarChannelUnitNormalizerTest.java`
- Test: `src/test/java/com/adriangarett/sleephqmcp/oscar/OscarEventCorrelatorTest.java`

- [ ] **Step 1: Add `median` field to the record**

In `ChannelStatistics.java`, insert `median` right after `percentile`:

```java
public record ChannelStatistics(
        String fieldName,
        String unit,
        double avg,
        double min,
        double max,
        double percentile,
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

- [ ] **Step 2: Compute and pass the real median in `OscarWaveformStatistics.compute()`**

In `OscarWaveformStatistics.java`, replace the `double p = percentileValue(...)` line and the
`new ChannelStatistics(...)` construction (around lines 128–139) with:

```java
        double p = percentileValue(sorted, percentile);
        double medianValue = percentileValue(sorted, 50);
        String minAt = clockAt(base, sampleRate, minIdx);
        String maxAt = clockAt(base, sampleRate, maxIdx);
        int minAtSeconds = offsetSeconds(sampleRate, minIdx);
        int maxAtSeconds = offsetSeconds(sampleRate, maxIdx);
        if (min == Double.POSITIVE_INFINITY) {
            min = 0;
            max = 0;
        }
        ChannelStatistics raw = new ChannelStatistics(fieldName, unit == null ? "" : unit,
                round(avg), round(min), round(max), round(p), round(medianValue), minAt, maxAt,
                minAtSeconds, maxAtSeconds, sorted.size());
        return OscarChannelUnitNormalizer.normalize(raw);
```

- [ ] **Step 3: Scale median in `OscarChannelUnitNormalizer.scale()`**

In `OscarChannelUnitNormalizer.java`, update the `scale()` constructor (around line 78) to scale median, inserting it after the `percentile` line:

```java
    private static ChannelStatistics scale(ChannelStatistics stat, double factor, String unit) {
        return new ChannelStatistics(
                stat.fieldName(),
                unit,
                round(stat.avg() * factor),
                round(stat.min() * factor),
                round(stat.max() * factor),
                round(stat.percentile() * factor),
                round(stat.median() * factor),
                stat.minAt(),
                stat.maxAt(),
                stat.minAtSeconds(),
                stat.maxAtSeconds(),
                stat.sampleCount());
    }
```

(`round(Double.NaN * factor)` stays `NaN` — harmless for summary-only stats from Step 4.)

- [ ] **Step 4: Pass `Double.NaN` median for summary-only sessions**

In `OscarChannelStatistics.java`, update the constructor (around line 27) to insert `Double.NaN` after the duplicated-max percentile arg:

```java
            ChannelStatistics raw = new ChannelStatistics(
                    field,
                    unit,
                    round(summary.avg()),
                    round(summary.min()),
                    round(summary.max()),
                    round(summary.max()),
                    Double.NaN,
                    null,
                    null,
                    ChannelStatistics.OFFSET_UNKNOWN,
                    ChannelStatistics.OFFSET_UNKNOWN,
                    0);
```

- [ ] **Step 5: Emit `median` in `channelStatsNode()` when present**

In `NightAnalysisSupport.java`, after the `ch.put("p95", stat.percentile());` line (line 30) add:

```java
            ch.put("p95", stat.percentile());
            if (!Double.isNaN(stat.median())) {
                ch.put("median", stat.median());
            }
```

- [ ] **Step 6: Fix `OscarChannelUnitNormalizerTest` helper (overload, no churn) and add a median test**

In `OscarChannelUnitNormalizerTest.java`, replace the single `stat(...)` helper with an overload that keeps existing 6-arg call sites working and add a median-scaling test:

```java
    @Test
    void normalizeTidalVolume_scalesMedianWithOtherStats() {
        ChannelStatistics raw = stat("tidal_volume", "L", 0.373, 0.0, 1.0, 0.5, 0.36);
        ChannelStatistics normalized = OscarChannelUnitNormalizer.normalize(raw);
        assertThat(normalized.unit()).isEqualTo("mL");
        assertThat(normalized.median()).isEqualTo(360.0);
    }

    private static ChannelStatistics stat(
            String field, String unit, double avg, double min, double max, double p95) {
        return stat(field, unit, avg, min, max, p95, Double.NaN);
    }

    private static ChannelStatistics stat(
            String field, String unit, double avg, double min, double max, double p95, double median) {
        return new ChannelStatistics(field, unit, avg, min, max, p95, median,
                "00:00:00", "01:00:00", 0, 3600, 100);
    }
```

- [ ] **Step 7: Fix `OscarEventCorrelatorTest` constructors**

In `OscarEventCorrelatorTest.java`, both `new ChannelStatistics(...)` calls (lines ~18 and ~43) need a median arg inserted after the percentile (6th) positional arg. Read each constructor and insert `Double.NaN,` (for the summary-only one) or a representative value after the percentile argument so the arg count matches the new 12-field record. Example for a leak stat with percentile `24.0`:

```java
        ChannelStatistics leak = new ChannelStatistics(
                "leak", "L/min", 5.0, 0.0, 30.0, 24.0, 4.0,
                "00:00:00", "01:00:00", 0, 3600, 100);
```

Match the existing surrounding values; only the new `median` (7th) argument is added.

- [ ] **Step 8: Run the affected tests — expect PASS**

Run: `./mvnw test -Dtest=OscarChannelUnitNormalizerTest,OscarEventCorrelatorTest,OscarWaveformStatisticsTest`
Expected: BUILD SUCCESS, all green (compilation proves every constructor site was updated).

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/adriangarett/sleephqmcp/domain/ChannelStatistics.java \
        src/main/java/com/adriangarett/sleephqmcp/oscar/OscarWaveformStatistics.java \
        src/main/java/com/adriangarett/sleephqmcp/oscar/OscarChannelUnitNormalizer.java \
        src/main/java/com/adriangarett/sleephqmcp/oscar/OscarChannelStatistics.java \
        src/main/java/com/adriangarett/sleephqmcp/support/NightAnalysisSupport.java \
        src/test/java/com/adriangarett/sleephqmcp/oscar/OscarChannelUnitNormalizerTest.java \
        src/test/java/com/adriangarett/sleephqmcp/oscar/OscarEventCorrelatorTest.java
git commit -m "feat(oscar): add median (p50) to channel statistics"
```

---

## Task 2: `VentilationSummarySupport` — shared aggregator + extractors

**Files:**
- Create: `src/main/java/com/adriangarett/sleephqmcp/support/VentilationSummarySupport.java`
- Test: `src/test/java/com/adriangarett/sleephqmcp/support/VentilationSummarySupportTest.java`

- [ ] **Step 1: Write the failing test**

Create `VentilationSummarySupportTest.java`:

```java
package com.adriangarett.sleephqmcp.support;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VentilationSummarySupportTest {

    @Test
    void metricSummary_averagesEachStatOverNightsWithData() {
        ObjectNode out = VentilationSummarySupport.metricSummary(List.of(
                new VentilationSummarySupport.NightTriple(28.0, 21.0, 17.0),
                new VentilationSummarySupport.NightTriple(26.0, 19.0, 17.0)),
                "sleephq_resp_rate_summary", 0);
        assertThat(out.path("max_avg").asDouble()).isEqualTo(27.0);
        assertThat(out.path("p95_avg").asDouble()).isEqualTo(20.0);
        assertThat(out.path("median_avg").asDouble()).isEqualTo(17.0);
        assertThat(out.path("nights_used").asInt()).isEqualTo(2);
        assertThat(out.path("source").asText()).isEqualTo("sleephq_resp_rate_summary");
    }

    @Test
    void metricSummary_excludesNaNNightsAndRoundsToDecimals() {
        ObjectNode out = VentilationSummarySupport.metricSummary(List.of(
                new VentilationSummarySupport.NightTriple(12.4, 7.8, 6.25),
                new VentilationSummarySupport.NightTriple(Double.NaN, Double.NaN, Double.NaN)),
                "oscar_pld", 1);
        assertThat(out.path("nights_used").asInt()).isEqualTo(1);
        assertThat(out.path("median_avg").asDouble()).isEqualTo(6.3);
    }

    @Test
    void metricSummary_returnsNullWhenNoUsableNights() {
        ObjectNode out = VentilationSummarySupport.metricSummary(List.of(
                new VentilationSummarySupport.NightTriple(Double.NaN, Double.NaN, Double.NaN)),
                "oscar_pld", 0);
        assertThat(out).isNull();
    }

    @Test
    void respiratoryRateFromSleepHq_readsMaxUpperMedSkippingSkippedNights() {
        ArrayNode nights = JsonApi.mapper().createArrayNode();
        ObjectNode n1 = nights.addObject();
        n1.put("date", "2026-05-27");
        n1.putObject("data").putObject("attributes").putObject("resp_rate_summary")
                .put("max", 28.4).put("upper", 21.0).put("med", 16.8);
        ObjectNode skipped = nights.addObject();
        skipped.put("date", "2026-05-21").put("skipped", true);

        ObjectNode rr = VentilationSummarySupport.respiratoryRateFromSleepHq(nights);
        assertThat(rr.path("max_avg").asDouble()).isEqualTo(28.0);
        assertThat(rr.path("nights_used").asInt()).isEqualTo(1);
        assertThat(rr.path("source").asText()).isEqualTo("sleephq_resp_rate_summary");
    }

    @Test
    void fromOscarChannels_buildsTvMvRrFromChannelsMaxP95Median() {
        ObjectNode night = JsonApi.mapper().createObjectNode();
        ObjectNode channels = night.putObject("channels");
        channels.putObject("tidal_volume").put("max", 712.0).put("p95", 466.0).put("median", 364.0);
        channels.putObject("minute_vent").put("max", 12.4).put("p95", 7.8).put("median", 6.2);
        channels.putObject("resp_rate").put("max", 27.0).put("p95", 20.0).put("median", 17.0);

        ObjectNode vent = VentilationSummarySupport.fromOscarChannels(List.of(night));
        assertThat(vent.path("tidal_volume_ml").path("median_avg").asDouble()).isEqualTo(364.0);
        assertThat(vent.path("minute_vent_l_min").path("median_avg").asDouble()).isEqualTo(6.2);
        assertThat(vent.path("respiratory_rate_per_min").path("source").asText()).isEqualTo("oscar_pld");
    }

    @Test
    void fromOscarChannels_returnsNullWhenNoVentilationChannels() {
        ObjectNode night = JsonApi.mapper().createObjectNode();
        night.putObject("channels").putObject("pressure").put("max", 11.0).put("p95", 10.8);
        assertThat(VentilationSummarySupport.fromOscarChannels(List.of(night))).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=VentilationSummarySupportTest`
Expected: FAIL — `VentilationSummarySupport` does not exist (compilation error).

- [ ] **Step 3: Write the implementation**

Create `VentilationSummarySupport.java`:

```java
package com.adriangarett.sleephqmcp.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Builds AirView-style span aggregates (Maximum / 95th / Median, each averaged over days-used)
 * for ventilation metrics. RR comes from SleepHQ {@code resp_rate_summary}; TV/MV come from OSCAR
 * PLD channel stats. Numbers are pre-rounded here so prompts can copy them verbatim.
 */
public final class VentilationSummarySupport {

    private VentilationSummarySupport() {}

    /** One night's (max, 95th, median) for one metric; any element {@code NaN} = missing that night. */
    public record NightTriple(double max, double p95, double median) {}

    /**
     * @return {@code {max_avg, p95_avg, median_avg, nights_used, source}} or {@code null} when no
     *         night carried any of the three stats.
     */
    public static ObjectNode metricSummary(List<NightTriple> nights, String source, int decimals) {
        List<Double> maxes = new ArrayList<>();
        List<Double> p95s = new ArrayList<>();
        List<Double> medians = new ArrayList<>();
        int used = 0;
        for (NightTriple t : nights) {
            boolean any = false;
            if (!Double.isNaN(t.max())) { maxes.add(t.max()); any = true; }
            if (!Double.isNaN(t.p95())) { p95s.add(t.p95()); any = true; }
            if (!Double.isNaN(t.median())) { medians.add(t.median()); any = true; }
            if (any) {
                used++;
            }
        }
        if (used == 0) {
            return null;
        }
        ObjectNode out = JsonApi.mapper().createObjectNode();
        putAvg(out, "max_avg", maxes, decimals);
        putAvg(out, "p95_avg", p95s, decimals);
        putAvg(out, "median_avg", medians, decimals);
        out.put("nights_used", used);
        out.put("source", source);
        return out;
    }

    /** RR aggregate from {@code get-comparison} {@code nights[].data.attributes.resp_rate_summary}. */
    public static ObjectNode respiratoryRateFromSleepHq(ArrayNode nights) {
        List<NightTriple> rr = new ArrayList<>();
        for (JsonNode night : nights) {
            if (night.path("skipped").asBoolean(false)) {
                continue;
            }
            JsonNode s = night.path("data").path("attributes").path("resp_rate_summary");
            if (!s.isObject() || s.isEmpty()) {
                continue;
            }
            rr.add(new NightTriple(num(s, "max"), num(s, "upper"), num(s, "med")));
        }
        return metricSummary(rr, "sleephq_resp_rate_summary", 0);
    }

    /** TV/MV/RR aggregate from OSCAR full night nodes ({@code channels.*.{max,p95,median}}). */
    public static ObjectNode fromOscarChannels(Collection<ObjectNode> fullNights) {
        List<NightTriple> tv = new ArrayList<>();
        List<NightTriple> mv = new ArrayList<>();
        List<NightTriple> rr = new ArrayList<>();
        for (ObjectNode night : fullNights) {
            JsonNode ch = night.path("channels");
            addTriple(tv, ch.path("tidal_volume"));
            addTriple(mv, ch.path("minute_vent"));
            addTriple(rr, ch.path("resp_rate"));
        }
        ObjectNode vent = JsonApi.mapper().createObjectNode();
        putIfPresent(vent, "tidal_volume_ml", metricSummary(tv, "oscar_pld", 0));
        putIfPresent(vent, "minute_vent_l_min", metricSummary(mv, "oscar_pld", 1));
        putIfPresent(vent, "respiratory_rate_per_min", metricSummary(rr, "oscar_pld", 0));
        return vent.isEmpty() ? null : vent;
    }

    private static void addTriple(List<NightTriple> list, JsonNode ch) {
        if (!ch.isObject() || ch.isEmpty()) {
            return;
        }
        list.add(new NightTriple(num(ch, "max"), num(ch, "p95"), num(ch, "median")));
    }

    private static void putAvg(ObjectNode out, String key, List<Double> values, int decimals) {
        if (values.isEmpty()) {
            return;
        }
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        double avg = sum / values.size();
        double factor = Math.pow(10, decimals);
        out.put(key, Math.round(avg * factor) / factor);
    }

    private static void putIfPresent(ObjectNode parent, String key, ObjectNode value) {
        if (value != null) {
            parent.set(key, value);
        }
    }

    private static double num(JsonNode obj, String key) {
        JsonNode n = obj.path(key);
        return n.isNumber() ? n.asDouble() : Double.NaN;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=VentilationSummarySupportTest`
Expected: PASS (6 tests green).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/adriangarett/sleephqmcp/support/VentilationSummarySupport.java \
        src/test/java/com/adriangarett/sleephqmcp/support/VentilationSummarySupportTest.java
git commit -m "feat(ventilation): shared span aggregator for TV/RR/MV"
```

---

## Task 3: Wire RR `ventilation_summary` into `get-comparison`

**Files:**
- Modify: `src/main/java/com/adriangarett/sleephqmcp/service/ComparisonService.java`

- [ ] **Step 1: Call the extractor after readiness is attached**

In `ComparisonService.compare()`, after the `attachTitrationReadiness(root);` line (line 103), add:

```java
        attachTitrationReadiness(root);
        ObjectNode ventilation = VentilationSummarySupport.respiratoryRateFromSleepHq(nights);
        if (ventilation != null) {
            root.putObject("ventilation_summary").set("respiratory_rate_per_min", ventilation);
        }
```

- [ ] **Step 2: Add the import**

In `ComparisonService.java`, add to the import block:

```java
import com.adriangarett.sleephqmcp.support.VentilationSummarySupport;
```

- [ ] **Step 3: Run the comparison tests — expect PASS**

Run: `./mvnw test -Dtest=ComparisonServiceTest,ComparisonTableDisplayTest`
Expected: BUILD SUCCESS (compilation confirms wiring; existing assertions unaffected).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/adriangarett/sleephqmcp/service/ComparisonService.java
git commit -m "feat(comparison): attach RR ventilation_summary from SleepHQ"
```

---

## Task 4: Wire TV/MV/RR `ventilation_summary` into `get-oscar-trend`

**Files:**
- Modify: `src/main/java/com/adriangarett/sleephqmcp/service/OscarTrendService.java`

- [ ] **Step 1: Compute the aggregate from full night nodes before slimming**

In `OscarTrendService.serializeTrend()`, replace the block that adds nights (lines 88–93) with:

```java
        ObjectNode ventilation = VentilationSummarySupport.fromOscarChannels(bySessionId.values());
        if (ventilation != null) {
            root.set("ventilation_summary", ventilation);
        }
        bySessionId.values().forEach(node -> nights.add("full".equals(mode) ? node : slim(node)));
        try {
            return JsonApi.mapper().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize trend", e);
        }
```

(The aggregate reads `channels.*` from the full nodes, which is correct whether `mode` is summary or full.)

- [ ] **Step 2: Add the import**

In `OscarTrendService.java`, add to the import block:

```java
import com.adriangarett.sleephqmcp.support.VentilationSummarySupport;
```

- [ ] **Step 3: Run the trend tests — expect PASS**

Run: `./mvnw test -Dtest=OscarTrendServiceTest`
Expected: BUILD SUCCESS. (If no `OscarTrendServiceTest` exists, run `./mvnw test -Dtest='Oscar*'` and confirm green.)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/adriangarett/sleephqmcp/service/OscarTrendService.java
git commit -m "feat(oscar-trend): attach TV/MV/RR ventilation_summary from PLD"
```

---

## Task 5: Full build + manual data smoke

**Files:** none (verification only)

- [ ] **Step 1: Run the whole suite**

Run: `./mvnw test`
Expected: BUILD SUCCESS, no failures.

- [ ] **Step 2: Rebuild and start the server**

Run: `./run.sh`
Wait for `Classpath sanity OK` in the log.

- [ ] **Step 3: Confirm RR block from SleepHQ**

Call `get-comparison(fromDate=2026-05-18, toDate=2026-05-28)` (via the MCP client / goose) and confirm the response root contains:

```json
"ventilation_summary": { "respiratory_rate_per_min": { "max_avg": ..., "p95_avg": ..., "median_avg": ..., "nights_used": ..., "source": "sleephq_resp_rate_summary" } }
```

Cross-check `respiratory_rate_per_min` against AirView page 3 (max-avg ≈ 27, 95th-avg ≈ 20, median-avg ≈ 17). Small differences in `nights_used` vs AirView's 10 are acceptable (days-used).

- [ ] **Step 4: Confirm TV/MV/RR block from OSCAR**

Call `get-oscar-trend(detail=summary)` over the same span and confirm the root contains `ventilation_summary.tidal_volume_ml`, `.minute_vent_l_min`, `.respiratory_rate_per_min`, each with `max_avg / p95_avg / median_avg / nights_used / source: "oscar_pld"`. Cross-check TV (≈ 712 / 466 / 364) and MV (≈ 12.4 / 7.8 / 6.2) against AirView page 3.

- [ ] **Step 5: Stop the server**

Run: `./stop.sh`

- [ ] **Step 6: Commit (only if any code fix was needed in this task)**

```bash
git add -A && git commit -m "fix(ventilation): smoke-test corrections"
```

(Skip if smoke passed with no edits.)

---

## Task 6: Output format — AirView span table spec

**Files:**
- Modify: `src/main/resources/clinical/output-format.md`

- [ ] **Step 1: Add the ventilation summary table section**

In `output-format.md`, after the "Inline charts (Goose autovisualiser)" section (ends ~line 17), insert:

```markdown
## Ventilation summary (AirView-style span table)

When `ventilation_summary` is present (`get-comparison` for RR; `get-oscar-trend` for TV/MV), render one span table. Copy numbers **verbatim** from the blocks — never compute.

| Metric | Maximum (avg) | 95th % (avg) | Median (avg) |
| :--- | ---: | ---: | ---: |
| Tidal Volume (mL)          | `tidal_volume_ml.max_avg`      | `tidal_volume_ml.p95_avg`      | `tidal_volume_ml.median_avg`      |
| Respiratory Rate (br/min)  | `respiratory_rate_per_min.max_avg` | `respiratory_rate_per_min.p95_avg` | `respiratory_rate_per_min.median_avg` |
| Minute Ventilation (L/min) | `minute_vent_l_min.max_avg`    | `minute_vent_l_min.p95_avg`    | `minute_vent_l_min.median_avg`    |

Rules:
- **RR** row from `get-comparison` `ventilation_summary.respiratory_rate_per_min`. **TV** and **MV** rows from `get-oscar-trend` `ventilation_summary`.
- Numbers are pre-rounded in JSON (TV/RR integer, MV 1 decimal) — do not re-round or recompute.
- When the OSCAR block is absent (OSCAR off/unreachable), render TV and MV cells as `—` and add a note: *"TV & MV unavailable — OSCAR not reachable this session."* RR still renders.
- The two sources may report different `nights_used`. Caption the table with the SleepHQ/RR night count; if the OSCAR `nights_used` differs, note it on the source line (e.g. *"TV & MV from OSCAR PLD, 8 nights"*). Do not imply one shared night count.
- Source line under the table: *"RR: SleepHQ `resp_rate_summary`; TV & MV: OSCAR PLD waveforms."*
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/clinical/output-format.md
git commit -m "docs(output-format): AirView-style ventilation summary table"
```

---

## Task 7: Autovisualiser — Ventilation mechanics chart group

**Files:**
- Modify: `src/main/resources/clinical/autovisualiser.md`

- [ ] **Step 1: Add the chart group section**

In `autovisualiser.md`, after the "physician_titration_review chart pack (up to 5)" section (ends ~line 107), insert:

```markdown
## Ventilation mechanics chart group (separate from the 5-chart pack)

**When:** `physician_titration_review` and `weekly-trend`, after the Ventilation summary table. This group is **independent** of the 5-chart titration pack cap — render up to 3 additional charts.

**Section heading:** `### Ventilation mechanics (charts)`.

| # | Chart | Type | Data (per-night series, median value) |
|---|-------|------|----------------------------------------|
| 1 | Tidal Volume by night | line | `get-oscar-trend` `nights[].therapy.tidal_volume.median` × `nights[].date` |
| 2 | Respiratory Rate by night | line | `get-comparison` `nights[].data.attributes.resp_rate_summary.med` × `nights[].date` |
| 3 | Minute Ventilation by night | line | `get-oscar-trend` `nights[].therapy.minute_vent.median` × `nights[].date` |

**Rules:**
- Skip any chart whose series is empty (no OSCAR → skip TV and MV; still draw RR from SleepHQ). Never invent points.
- Y-axis units: TV mL, RR br/min, MV L/min.
- Caption each: tool name + date span.
- Smoke / PASS-FAIL checklists: still **zero** charts.
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/clinical/autovisualiser.md
git commit -m "docs(autovisualiser): ventilation mechanics chart group"
```

---

## Task 8: physician-titration-review prompt sections

**Files:**
- Modify: `src/main/resources/prompts/physician-titration-review.md`

- [ ] **Step 1: Add the two sections to the mandatory section list**

In `physician-titration-review.md`, in "Mandatory visible sections (do not skip)", insert a new item between item 4 (`### Span trends (charts)`) and item 5 (`### Data completeness`):

```markdown
4b. **`### Ventilation summary (span)`** + **`### Ventilation mechanics (charts)`** — from **`get-comparison`** `ventilation_summary` (RR) and **`get-oscar-trend(detail=summary)`** `ventilation_summary` (TV/MV). Render the AirView-style table (`sleephq://playbook/output-format` → "Ventilation summary") then the 3-chart group (`sleephq://playbook/autovisualiser` → "Ventilation mechanics chart group"). If OSCAR is not reachable, show RR only and note TV/MV unavailable — do not block.
```

- [ ] **Step 2: Reference get-oscar-trend in the resources/phase notes**

In `physician-titration-review.md`, in the "Phase 1b — OSCAR cross-check" section, append a bullet:

```markdown
4. If OSCAR is reachable, also call `get-oscar-trend(detail=summary)` over the span to obtain `ventilation_summary` (tidal_volume_ml, minute_vent_l_min) for the Ventilation summary table — TV/MV are OSCAR-only (absent from SleepHQ machine_date). This is comfort/mechanics context, not a pressure-decision authority.
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/prompts/physician-titration-review.md
git commit -m "docs(titration): mandatory ventilation summary + charts sections"
```

---

## Task 9: weekly-trend prompt sections

**Files:**
- Modify: `src/main/resources/prompts/weekly-trend.md`

- [ ] **Step 1: Read the file to find the output/sections area**

Run: `sed -n '1,200p' src/main/resources/prompts/weekly-trend.md` (via Read tool) to locate where per-night tables / charts are described.

- [ ] **Step 2: Add a ventilation section**

Append (or insert near the charts/trend output area) this section, adapting heading depth to the file's existing style:

```markdown
## Ventilation summary (span)

After the trend table, render the AirView-style ventilation summary + chart group:
- Table from `get-comparison` `ventilation_summary.respiratory_rate_per_min` (RR) and, when OSCAR is reachable, `get-oscar-trend(detail=summary)` `ventilation_summary` (tidal_volume_ml, minute_vent_l_min). Copy numbers verbatim — see `sleephq://playbook/output-format` → "Ventilation summary".
- Charts: `sleephq://playbook/autovisualiser` → "Ventilation mechanics chart group" (TV, RR, MV by night). Skip TV/MV when OSCAR is unavailable; RR still renders.
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/prompts/weekly-trend.md
git commit -m "docs(weekly-trend): ventilation summary + charts section"
```

---

## Task 10: Verify goose-recipe wiring + final review

**Files:**
- Modify (only if needed): `goose-recipe.yaml`

- [ ] **Step 1: Confirm the playbooks are already exposed as resources**

Run: `grep -n "output-format\|autovisualiser\|physician-titration-review\|weekly-trend" goose-recipe.yaml`
Expected: the recipe references the autovisualiser/output-format playbooks and the two prompts. No new resources are introduced by this feature (the `ventilation_summary` is a field on existing tool responses, and the prompts/playbooks are existing files), so **no edit is expected**.

- [ ] **Step 2: If a referenced playbook/prompt is missing from the recipe, add it**

Only if Step 1 shows a gap, add the missing resource entry following the existing pattern in `goose-recipe.yaml`. Otherwise skip.

- [ ] **Step 3: Final full build**

Run: `./mvnw test`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit (only if goose-recipe.yaml changed)**

```bash
git add goose-recipe.yaml
git commit -m "chore(goose): wire ventilation playbook references"
```

---

## Self-Review notes

- **Spec coverage:** OSCAR median → Task 1; RR aggregate in get-comparison → Tasks 2–3; TV/MV aggregate in get-oscar-trend → Tasks 2,4; AirView table → Task 6; chart group → Task 7; prompts → Tasks 8–9; goose-recipe → Task 10; days-used averaging → Task 2 (`metricSummary` counts only non-NaN nights); graceful OSCAR fallback → Tasks 4,6,7,8; testing → Tasks 1,2 + smoke Task 5.
- **No new per-night table columns** (spec "out of scope") — honored; `resp_rate_cell` untouched.
- **Type consistency:** `ChannelStatistics.median()` (Task 1) is read in `VentilationSummarySupport.fromOscarChannels` via the JSON `median` key emitted by `channelStatsNode` (Task 1 Step 5) — consistent. `respiratoryRateFromSleepHq` and `fromOscarChannels` signatures match their call sites in Tasks 3–4.
