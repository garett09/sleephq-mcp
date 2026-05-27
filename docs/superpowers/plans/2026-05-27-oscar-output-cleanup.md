# OSCAR night/trend output cleanup — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tighten `get-combined-night-by-date`, `get-oscar-events`, and `get-oscar-trend` JSON output so LLM consumers see canonical event labels, no event-channel statistics in `channels`, an honest `coverage` block, and a payload-slim trend.

**Architecture:** Surgical edits to OSCAR layer only — no new services. Three central choke points:
1. `OscarChannelStatistics.fromSummarySession` / `NightAnalysisSupport.summaryChannelNode` — filter event channels out of `channels.*`.
2. `OscarEventSummaryBuilder` — canonical EVE-label map, recording-marker filter in `timed_sample`, agree-mode `event_count_authority`.
3. `OscarTrendService` — new `detail=summary|full` mode that emits a slim per-night row.

Supporting catalog / coverage / waveform tweaks live in `OscarChannelCatalog`, `OscarWaveformStatistics`, `NightAnalysisSupport.coverageNode`.

**Tech Stack:** Java 21, Spring Boot 3, Jackson, JUnit 5, Maven (`./mvnw test`).

---

## File map

**Create:**
- `src/main/java/com/adriangarett/sleephqmcp/oscar/OscarEventLabelCanonicalizer.java` — central EVE-label → canonical field-name map.
- `src/test/java/com/adriangarett/sleephqmcp/oscar/OscarEventLabelCanonicalizerTest.java`

**Modify:**
- `src/main/java/com/adriangarett/sleephqmcp/oscar/OscarChannelIds.java` — declare 0x110A/0x110B field constants used by catalog.
- `src/main/java/com/adriangarett/sleephqmcp/oscar/OscarChannelCatalog.java` — add 0x110A `expiratory_time_wave`, 0x110B `inspiratory_time` entries.
- `src/main/java/com/adriangarett/sleephqmcp/oscar/OscarChannelStatistics.java` — skip event channels in `fromSummarySession`.
- `src/main/java/com/adriangarett/sleephqmcp/oscar/OscarEventSummaryBuilder.java` — canonical labels, skip recording markers in `timed_sample`, agree-mode authority, `event_counts_agree` flag.
- `src/main/java/com/adriangarett/sleephqmcp/oscar/OscarWaveformStatistics.java` — map EDF label `FlowHiRes`/`FlowRate2` → `flow_rate_hi_res`.
- `src/main/java/com/adriangarett/sleephqmcp/support/NightAnalysisSupport.java` — filter event channels in `summaryChannelNode`; split coverage into `oscar_edf_pld_stats`; document trend `date`/`calendar_date` policy.
- `src/main/java/com/adriangarett/sleephqmcp/service/OscarTrendService.java` — add `detail` param and slim per-night projector.
- `src/main/java/com/adriangarett/sleephqmcp/service/UnifiedNightAnalysisService.java` — wire `pldStats` boolean (sample_count > 0) into `coverageNode`; surface `calendar_date` as deprecated alias only when needed.
- `src/main/java/com/adriangarett/sleephqmcp/tools/OscarTools.java` — expose `detail` arg on `get-oscar-trend`.

**Test (modify or add):**
- `src/test/java/com/adriangarett/sleephqmcp/oscar/OscarChannelStatisticsTest.java` (likely new)
- `src/test/java/com/adriangarett/sleephqmcp/oscar/OscarEventSummaryBuilderTest.java` (exists/expand)
- `src/test/java/com/adriangarett/sleephqmcp/service/OscarTrendServiceTest.java` (exists; expand)
- `src/test/java/com/adriangarett/sleephqmcp/service/CombinedNightServiceTest.java` (exists; add cases)
- `src/test/java/com/adriangarett/sleephqmcp/support/NightAnalysisSupportTest.java` (exists; expand)

**Docs:**
- `docs/sleephq-openapi-gap.md` — add trend `detail=summary|full` and canonical event-label policy.
- `CLAUDE.md` — note canonical event labels and trend slim mode.

---

## Implementation order

P0 first (items #1, #2, #3) — bundled because they share the channel/event boundary. Then P1 (#4, #5, #6, #7), then P2 (#8, #9, #10, #11), then P3 (#12, #14 docs).

---

### Task 1: Add catalog entries for 0x110A / 0x110B (P0 #3)

**Files:**
- Modify: `src/main/java/com/adriangarett/sleephqmcp/oscar/OscarChannelCatalog.java:34-71`

- [ ] **Step 1: Write failing test**

Add to `src/test/java/com/adriangarett/sleephqmcp/oscar/OscarChannelCatalogTest.java` (create if missing):

```java
package com.adriangarett.sleephqmcp.oscar;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OscarChannelCatalogTest {
    @Test
    void mapsExpiratoryAndInspiratoryTimeWaveforms() {
        assertEquals("expiratory_time_wave",
                OscarChannelCatalog.fieldName(OscarChannelIds.CPAP_ExpiratoryTimeWave));
        assertEquals("inspiratory_time",
                OscarChannelCatalog.fieldName(OscarChannelIds.CPAP_InspiratoryTime));
        assertEquals("s",
                OscarChannelCatalog.find(OscarChannelIds.CPAP_InspiratoryTime).orElseThrow().unit());
    }
}
```

- [ ] **Step 2: Run test, verify it fails**

Run: `./mvnw test -Dtest=OscarChannelCatalogTest`
Expected: FAIL — `field name returns "ch_110a"`.

- [ ] **Step 3: Add catalog entries**

In `OscarChannelCatalog.buildIndex()`, after the `CPAP_EPAP` entry add:

```java
        m.put(OscarChannelIds.CPAP_ExpiratoryTimeWave, meta("expiratory_time_wave", "Expiratory time (waveform)", "s"));
        m.put(OscarChannelIds.CPAP_InspiratoryTime, meta("inspiratory_time", "Inspiratory time", "s"));
```

- [ ] **Step 4: Run test, verify pass**

Run: `./mvnw test -Dtest=OscarChannelCatalogTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/adriangarett/sleephqmcp/oscar/OscarChannelCatalog.java \
        src/test/java/com/adriangarett/sleephqmcp/oscar/OscarChannelCatalogTest.java
git commit -m "feat(oscar): catalog 0x110A/0x110B as expiratory/inspiratory time"
```

---

### Task 2: Build canonical event-label map (P0 #2, P2 #11)

**Files:**
- Create: `src/main/java/com/adriangarett/sleephqmcp/oscar/OscarEventLabelCanonicalizer.java`
- Create: `src/test/java/com/adriangarett/sleephqmcp/oscar/OscarEventLabelCanonicalizerTest.java`

EVE.edf annotation labels (raw from device) map to summary-channel field names (from `OscarChannelCatalog`). Truth source: `EdfAnnotationParser.normalizeCode` — already shows the synonyms ("obstructive apnea" → "OA", "central apnea"/"clear airway" → "CA", "hypopnea" → "H", "flow limitation"/"flow limit" → "FL", "large leak" → "LL", "rera"/"arousal" → "RE", "pressure pulse" → "PP", "cheyne" → "CSR", bare "apnea" → "A").

Canonical field names (summary side) from catalog: `clear_airway`, `obstructive`, `hypopnea`, `apnea`, `flow_limit_events`, `rera`, `vibratory_snore`, `large_leak`, `nri`, `expiratory_time`, `sens_awake`, `all_apnea`, `pressure_pulse`.

- [ ] **Step 1: Write failing test**

```java
package com.adriangarett.sleephqmcp.oscar;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OscarEventLabelCanonicalizerTest {
    @Test
    void mapsKnownSynonymsToSummaryFieldNames() {
        assertEquals("obstructive", OscarEventLabelCanonicalizer.canonical("Obstructive Apnea"));
        assertEquals("clear_airway", OscarEventLabelCanonicalizer.canonical("Central Apnea"));
        assertEquals("clear_airway", OscarEventLabelCanonicalizer.canonical("Clear Airway"));
        assertEquals("hypopnea", OscarEventLabelCanonicalizer.canonical("Hypopnea"));
        assertEquals("rera", OscarEventLabelCanonicalizer.canonical("RERA"));
        assertEquals("rera", OscarEventLabelCanonicalizer.canonical("Arousal"));
        assertEquals("flow_limit_events", OscarEventLabelCanonicalizer.canonical("Flow Limitation"));
        assertEquals("large_leak", OscarEventLabelCanonicalizer.canonical("Large Leak"));
        assertEquals("pressure_pulse", OscarEventLabelCanonicalizer.canonical("Pressure Pulse"));
        assertEquals("vibratory_snore", OscarEventLabelCanonicalizer.canonical("Vibratory Snore"));
    }

    @Test
    void returnsNullForRecordingMarkers() {
        assertNull(OscarEventLabelCanonicalizer.canonical("Recording starts"));
        assertNull(OscarEventLabelCanonicalizer.canonical("recording_end"));
    }

    @Test
    void unknownLabelsFallBackToSnakeCase() {
        // unknown label should still be usable but obviously non-canonical
        assertEquals("unknown_label_x", OscarEventLabelCanonicalizer.canonical("Unknown Label X"));
    }
}
```

- [ ] **Step 2: Run test, verify it fails (class missing)**

Run: `./mvnw test -Dtest=OscarEventLabelCanonicalizerTest`
Expected: FAIL — class not found / does not compile.

- [ ] **Step 3: Implement canonicalizer**

```java
package com.adriangarett.sleephqmcp.oscar;

import java.util.Locale;
import java.util.Set;

/**
 * Maps raw EVE.edf annotation labels to the same canonical field names used by
 * {@link OscarChannelCatalog} (i.e. summary-counts keys). Keeps EVE-derived counts
 * and Summaries/000-derived counts under matching keys so consumers can compare directly.
 *
 * Returns {@code null} for non-therapy markers (recording start/stop/unknown).
 */
public final class OscarEventLabelCanonicalizer {

    private static final Set<String> RECORDING_MARKERS = Set.of(
            "recording_start", "recording_starts", "recording_end", "recording_stop",
            "starting", "stopping", "start", "stop", "unknown");

    private OscarEventLabelCanonicalizer() {}

    public static String canonical(String rawLabel) {
        if (rawLabel == null || rawLabel.isBlank()) {
            return null;
        }
        String norm = rawLabel.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (RECORDING_MARKERS.contains(norm) || norm.startsWith("recording")) {
            return null;
        }
        if (norm.contains("obstructive") && norm.contains("apnea")) {
            return "obstructive";
        }
        if (norm.contains("central") || norm.contains("clear_airway")) {
            return "clear_airway";
        }
        if (norm.contains("hypopnea")) {
            return "hypopnea";
        }
        if (norm.contains("flow_limit") || norm.contains("flow_limitation")) {
            return "flow_limit_events";
        }
        if (norm.contains("large_leak")) {
            return "large_leak";
        }
        if (norm.contains("rera") || norm.contains("arousal")) {
            return "rera";
        }
        if (norm.contains("vibratory") || norm.contains("snore")) {
            return "vibratory_snore";
        }
        if (norm.contains("pressure_pulse")) {
            return "pressure_pulse";
        }
        if (norm.contains("cheyne")) {
            return "csr";
        }
        if (norm.equals("apnea")) {
            return "apnea";
        }
        return norm;
    }

    /** Convenience predicate for filtering. */
    public static boolean isNonTherapy(String rawLabel) {
        return canonical(rawLabel) == null;
    }
}
```

- [ ] **Step 4: Run test, verify pass**

Run: `./mvnw test -Dtest=OscarEventLabelCanonicalizerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/adriangarett/sleephqmcp/oscar/OscarEventLabelCanonicalizer.java \
        src/test/java/com/adriangarett/sleephqmcp/oscar/OscarEventLabelCanonicalizerTest.java
git commit -m "feat(oscar): canonical event-label map aligning EVE and summary counts"
```

---

### Task 3: Use canonicalizer in event summary + drop recording markers from timed_sample (P0 #2, P1 #5, P1 #4)

**Files:**
- Modify: `src/main/java/com/adriangarett/sleephqmcp/oscar/OscarEventSummaryBuilder.java:30-115`

- [ ] **Step 1: Add failing tests**

Expand `src/test/java/com/adriangarett/sleephqmcp/oscar/OscarEventSummaryBuilderTest.java` (create or extend) with:

```java
package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.DeviceEvent;
import com.adriangarett.sleephqmcp.domain.DeviceEventResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OscarEventSummaryBuilderTest {

    @Test
    void eveLabelsAreCanonicalizedAndMatchSummaryKeys() {
        DeviceEventResult result = result(List.of(
                event("Obstructive Apnea"),
                event("Central Apnea"),
                event("Hypopnea"),
                event("RERA")));
        JsonNode node = OscarEventSummaryBuilder.buildSummary(result, 10,
                Optional.of(Map.of("obstructive", 1, "clear_airway", 1, "hypopnea", 1, "rera", 1)));
        JsonNode counts = node.get("counts");
        assertEquals(1, counts.get("obstructive").asInt());
        assertEquals(1, counts.get("clear_airway").asInt());
        assertFalse(counts.has("obstructive_apnea"));
        assertFalse(counts.has("central_apnea"));
    }

    @Test
    void timedSampleSkipsRecordingMarkers() {
        DeviceEventResult result = result(List.of(
                event("Recording starts"),
                event("Hypopnea"),
                event("Recording ends")));
        JsonNode node = OscarEventSummaryBuilder.buildSummary(result, 10, Optional.empty());
        JsonNode timed = node.get("timed_sample");
        assertEquals(1, timed.size());
        assertEquals("Hypopnea", timed.get(0).get("label").asText());
    }

    @Test
    void authorityIsSummaryAndAgreeFlagSetWhenTotalsMatch() {
        DeviceEventResult result = result(List.of(
                event("Obstructive Apnea"),
                event("Hypopnea")));
        JsonNode node = OscarEventSummaryBuilder.buildSummary(result, 10,
                Optional.of(Map.of("obstructive", 1, "hypopnea", 1)));
        assertEquals("oscar_summary_000", node.get("event_count_authority").asText());
        assertTrue(node.get("event_counts_agree").asBoolean());
    }

    private static DeviceEvent event(String label) {
        return new DeviceEvent("00:00:00", 0.0, 0.0, "2026-05-18T22:00:00", label, null);
    }

    private static DeviceEventResult result(List<DeviceEvent> events) {
        return new DeviceEventResult("EVE.edf", "2026-05-18T22:00:00", 28800, "device_eve", events);
    }
}
```

- [ ] **Step 2: Run, verify failure**

Run: `./mvnw test -Dtest=OscarEventSummaryBuilderTest`
Expected: FAIL on all three new assertions (raw `obstructive_apnea` key, recording markers leak into timed_sample, authority is `oscar_eve_edf` when totals equal).

- [ ] **Step 3: Update `OscarEventSummaryBuilder`**

Replace `normalizeLabel` usage with the canonicalizer for `counts`, filter timed_sample, and update authority logic. Final body:

```java
    public static ObjectNode buildSummary(
            DeviceEventResult result,
            int maxTimedEvents,
            Optional<Map<String, Integer>> summaryCounts) {
        ObjectNode root = com.adriangarett.sleephqmcp.support.JsonApi.mapper().createObjectNode();
        Map<String, Integer> counts = new LinkedHashMap<>();
        int therapyEvents = 0;
        List<DeviceEvent> therapyOnly = new ArrayList<>();
        for (DeviceEvent event : result.events()) {
            String canonical = OscarEventLabelCanonicalizer.canonical(event.label());
            if (canonical == null) {
                continue;
            }
            counts.merge(canonical, 1, Integer::sum);
            therapyEvents++;
            therapyOnly.add(event);
        }
        ObjectNode countsNode = root.putObject("counts");
        counts.forEach(countsNode::put);
        root.put("eve_total", therapyEvents);

        int summaryTotal = 0;
        boolean hasSummaryCounts = summaryCounts.isPresent() && !summaryCounts.get().isEmpty();
        if (hasSummaryCounts) {
            ObjectNode summaryNode = root.putObject("summary_counts");
            summaryCounts.get().forEach(summaryNode::put);
            root.put("summary_counts_source", "oscar_summary_000");
            summaryTotal = summaryCounts.get().values().stream().mapToInt(Integer::intValue).sum();
            root.put("summary_total", summaryTotal);
        }
        root.put("total", hasSummaryCounts ? summaryTotal : therapyEvents);

        if (hasSummaryCounts) {
            boolean agree = therapyEvents == summaryTotal;
            root.put("event_counts_agree", agree);
            root.put("event_count_authority", "oscar_summary_000");
        } else if (therapyEvents > 0) {
            root.put("event_count_authority", "oscar_eve_edf");
        }

        ArrayNode timed = root.putArray("timed_sample");
        int limit = Math.min(maxTimedEvents, therapyOnly.size());
        for (int i = 0; i < limit; i++) {
            DeviceEvent event = therapyOnly.get(i);
            ObjectNode row = timed.addObject();
            row.put("timestamp", event.timestamp());
            row.put("label", event.label());
            row.put("code", event.code());
            row.put("duration_seconds", event.durationSeconds());
        }
        if (therapyOnly.size() > limit) {
            root.put("timed_sample_truncated", true);
            root.put("timed_sample_total", therapyOnly.size());
        }
        return root;
    }
```

Add `import java.util.ArrayList;` and remove the now-unused private `normalizeLabel`/`isNonTherapyEvent`/`NON_THERAPY_EVENT_KEYS` (the canonicalizer subsumes them) — keep `buildSummaryOnly` as-is but make sure its `event_count_authority` already reads `oscar_summary_000` (it does).

- [ ] **Step 4: Run tests, verify pass**

Run: `./mvnw test -Dtest=OscarEventSummaryBuilderTest`
Expected: PASS.

Run the broader suite to catch regressions:

Run: `./mvnw test -Dtest='Oscar*Test,*NightAnalysis*Test,CombinedNightServiceTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/adriangarett/sleephqmcp/oscar/OscarEventSummaryBuilder.java \
        src/test/java/com/adriangarett/sleephqmcp/oscar/OscarEventSummaryBuilderTest.java
git commit -m "feat(oscar): canonical event keys, drop recording markers, set agree flag"
```

---

### Task 4: Drop event channels from `channels.*` in night analysis (P0 #1)

**Files:**
- Modify: `src/main/java/com/adriangarett/sleephqmcp/oscar/OscarChannelStatistics.java:14-37`
- Modify: `src/main/java/com/adriangarett/sleephqmcp/support/NightAnalysisSupport.java:46-64`

- [ ] **Step 1: Write failing tests**

Create `src/test/java/com/adriangarett/sleephqmcp/oscar/OscarChannelStatisticsTest.java`:

```java
package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.domain.ChannelStatistics;
import com.adriangarett.sleephqmcp.domain.ChannelSummary;
import com.adriangarett.sleephqmcp.domain.OscarSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OscarChannelStatisticsTest {

    @Test
    void omitsEventChannelsFromChannelStats() {
        Map<Integer, ChannelSummary> ch = new LinkedHashMap<>();
        ch.put(OscarChannelIds.CPAP_Pressure, new ChannelSummary(8.5, 6.0, 12.0, null, null, null));
        ch.put(OscarChannelIds.CPAP_ClearAirway, new ChannelSummary(13.0, null, null, null, null, null));
        ch.put(OscarChannelIds.CPAP_Obstructive, new ChannelSummary(10.0, null, null, null, null, null));
        OscarSession session = new OscarSession(
                0xdeadbeefL, Instant.parse("2026-05-18T22:00:00Z"), Instant.parse("2026-05-19T06:00:00Z"),
                List.of(OscarChannelIds.CPAP_Pressure, OscarChannelIds.CPAP_ClearAirway, OscarChannelIds.CPAP_Obstructive),
                ch, Map.of(), Map.of());
        Map<String, ChannelStatistics> stats = OscarChannelStatistics.fromSummarySession(session);
        assertTrue(stats.containsKey("pressure"));
        assertFalse(stats.containsKey("clear_airway"));
        assertFalse(stats.containsKey("obstructive"));
    }
}
```

(If the `OscarSession` constructor signature differs, adjust call to match. Read `OscarSession.java` first to confirm.)

Also add to `NightAnalysisSupportTest`:

```java
    @Test
    void summaryChannelNodeOmitsEventChannels() {
        // Build OscarSession with one event channel id only; expect summaryChannelNode to be empty (or not contain event keys).
        // See existing test in this file for OscarSession construction helper.
    }
```

(Fill the body using whatever helper already exists in `NightAnalysisSupportTest`; if none, construct an `OscarSession` inline using its actual constructor.)

- [ ] **Step 2: Run, verify failure**

Run: `./mvnw test -Dtest=OscarChannelStatisticsTest,NightAnalysisSupportTest`
Expected: FAIL — `clear_airway` and `obstructive` present.

- [ ] **Step 3: Filter in `OscarChannelStatistics`**

In `fromSummarySession`, add an early continue:

```java
        for (Map.Entry<Integer, ChannelSummary> entry : session.channels().entrySet()) {
            int id = entry.getKey();
            if (OscarChannelIdClassification.isEventChannel(id)) {
                continue;
            }
            ChannelSummary summary = entry.getValue();
            ...
```

Create a small helper to avoid duplicating the event-channel id list:

`src/main/java/com/adriangarett/sleephqmcp/oscar/OscarChannelIdClassification.java`

```java
package com.adriangarett.sleephqmcp.oscar;

import java.util.Set;

public final class OscarChannelIdClassification {

    private static final Set<Integer> EVENT_CHANNEL_IDS = Set.of(
            OscarChannelIds.CPAP_UsageFlag,
            OscarChannelIds.CPAP_ClearAirway,
            OscarChannelIds.CPAP_Obstructive,
            OscarChannelIds.CPAP_Hypopnea,
            OscarChannelIds.CPAP_Apnea,
            OscarChannelIds.CPAP_FlowLimit,
            OscarChannelIds.CPAP_RERA,
            OscarChannelIds.CPAP_VibratorySnore,
            OscarChannelIds.CPAP_LargeLeak,
            OscarChannelIds.CPAP_NRI,
            OscarChannelIds.CPAP_ExpiratoryTime,
            OscarChannelIds.CPAP_SensAwake,
            OscarChannelIds.CPAP_AllApnea,
            OscarChannelIds.CPAP_PressurePulse);

    private OscarChannelIdClassification() {}

    public static boolean isEventChannel(int channelId) {
        return EVENT_CHANNEL_IDS.contains(channelId);
    }
}
```

- [ ] **Step 4: Filter in `NightAnalysisSupport.summaryChannelNode`**

```java
    public static ObjectNode summaryChannelNode(OscarSession session) {
        ObjectNode channels = JsonApi.mapper().createObjectNode();
        for (int channelId : session.availableChannelIds()) {
            if (OscarChannelIdClassification.isEventChannel(channelId)) {
                continue;
            }
            // ...existing body...
        }
        return channels;
    }
```

Add `import com.adriangarett.sleephqmcp.oscar.OscarChannelIdClassification;`.

- [ ] **Step 5: Run tests, verify pass**

Run: `./mvnw test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/adriangarett/sleephqmcp/oscar/OscarChannelStatistics.java \
        src/main/java/com/adriangarett/sleephqmcp/oscar/OscarChannelIdClassification.java \
        src/main/java/com/adriangarett/sleephqmcp/support/NightAnalysisSupport.java \
        src/test/java/com/adriangarett/sleephqmcp/oscar/OscarChannelStatisticsTest.java \
        src/test/java/com/adriangarett/sleephqmcp/support/NightAnalysisSupportTest.java
git commit -m "fix(oscar): omit event-channel ids from channel statistics output"
```

---

### Task 5: Map `flow_rate_hi_res` waveform label (P2 #10)

**Files:**
- Modify: `src/main/java/com/adriangarett/sleephqmcp/oscar/OscarWaveformStatistics.java:54-78`

- [ ] **Step 1: Write failing test**

Create or extend `src/test/java/com/adriangarett/sleephqmcp/oscar/OscarWaveformStatisticsTest.java`:

```java
    @Test
    void mapsFlowHiResLabel() throws Exception {
        // Use the existing test PLD fixture or build a minimal EDF in-memory with a "FlowHiRes" channel.
        // Assert resulting stats key includes "flow_rate_hi_res".
    }
```

(Use whatever EDF fixture pattern existing OSCAR tests use; if none, skip the unit test and rely on the catalog test — but at minimum, edit and add the mapper.)

- [ ] **Step 2: Update `mapLabelToField`**

Insert before the existing `flow` branch:

```java
        if (lower.startsWith("flowhires") || lower.startsWith("flowrate2") || lower.equals("flow rate (hi-res)")) {
            return "flow_rate_hi_res";
        }
```

- [ ] **Step 3: Run tests, verify pass**

Run: `./mvnw test -Dtest=OscarWaveformStatisticsTest`
Expected: PASS (or N/A if no fixture; manual smoke test will catch).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/adriangarett/sleephqmcp/oscar/OscarWaveformStatistics.java \
        src/test/java/com/adriangarett/sleephqmcp/oscar/OscarWaveformStatisticsTest.java
git commit -m "feat(oscar): map flow_rate_hi_res EDF label to channel field"
```

---

### Task 6: Split coverage — `oscar_edf_pld` (present) vs `oscar_edf_pld_stats` (has samples) (P2 #8)

**Files:**
- Modify: `src/main/java/com/adriangarett/sleephqmcp/support/NightAnalysisSupport.java:89-104`
- Modify: `src/main/java/com/adriangarett/sleephqmcp/service/UnifiedNightAnalysisService.java:132-138`

- [ ] **Step 1: Write failing test**

In `NightAnalysisSupportTest`:

```java
    @Test
    void coverageDistinguishesPldPresenceFromPldStats() {
        ObjectNode node = NightAnalysisSupport.coverageNode(
                true, true, true, true, false, 5, true);   // pldStats=true
        assertTrue(node.get("oscar_edf_pld").asBoolean());
        assertTrue(node.get("oscar_edf_pld_stats").asBoolean());

        ObjectNode noStats = NightAnalysisSupport.coverageNode(
                true, true, true, true, false, 0, false);
        assertTrue(noStats.get("oscar_edf_pld").asBoolean());
        assertFalse(noStats.get("oscar_edf_pld_stats").asBoolean());
    }
```

- [ ] **Step 2: Run, verify fail (method signature mismatch)**

Run: `./mvnw test -Dtest=NightAnalysisSupportTest`
Expected: FAIL — compilation error.

- [ ] **Step 3: Extend `coverageNode` signature**

```java
    public static ObjectNode coverageNode(
            boolean sleephqCpap,
            boolean summary,
            boolean pldPresent,
            boolean eve,
            boolean brp,
            int channelCount,
            boolean pldHasStats) {
        ObjectNode coverage = JsonApi.mapper().createObjectNode();
        coverage.put("sleephq_cpap", sleephqCpap);
        coverage.put("oscar_summary", summary);
        coverage.put("oscar_edf_pld", pldPresent);
        coverage.put("oscar_edf_pld_stats", pldHasStats);
        coverage.put("oscar_edf_eve", eve);
        coverage.put("oscar_edf_brp", brp);
        coverage.put("channels_reported", channelCount);
        return coverage;
    }
```

- [ ] **Step 4: Wire from `UnifiedNightAnalysisService`**

Track `pldHasStats` from PLD load:

```java
        boolean pldHasStats = false;
        if (paths.pld().isPresent()) {
            hasPld = true;
            Map<String, ChannelStatistics> pldStats = loadPld(paths.pld().get(), analysis.percentile());
            pldHasStats = pldStats.values().stream().anyMatch(s -> s.sampleCount() > 0);
            mergeStats(channelStats, pldStats);
        }
```

And update the `coverageNode` call:

```java
        nightAnalysis.set("coverage", NightAnalysisSupport.coverageNode(
                machineDateAttrs != null,
                oscarSession.isPresent(),
                hasPld,
                hasEve,
                hasBrp,
                channelsNode.size(),
                pldHasStats));
```

- [ ] **Step 5: Run, verify pass**

Run: `./mvnw test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/adriangarett/sleephqmcp/support/NightAnalysisSupport.java \
        src/main/java/com/adriangarett/sleephqmcp/service/UnifiedNightAnalysisService.java \
        src/test/java/com/adriangarett/sleephqmcp/support/NightAnalysisSupportTest.java
git commit -m "fix(oscar): split coverage.oscar_edf_pld into presence vs stats"
```

---

### Task 7: Slim trend output with `detail=summary` (P1 #6, P3 #12)

**Files:**
- Modify: `src/main/java/com/adriangarett/sleephqmcp/service/OscarTrendService.java`
- Modify: `src/main/java/com/adriangarett/sleephqmcp/tools/OscarTools.java`
- Modify: `src/test/java/com/adriangarett/sleephqmcp/service/OscarTrendServiceTest.java`

Slim row = `{ date, session, respiratory_indices, events: { counts, summary_counts, eve_total, summary_total, total, event_count_authority, event_counts_agree }, therapy: { pressure, leak, leak_total, leak_median, leak_max, ahi, rdi } }`. Drop `flow_brp`, `pressure_brp`, full `channels`, `timed_sample`, `notable_moments`, `data_sources`, `coverage`, `calendar_date` duplicate.

- [ ] **Step 1: Write failing test**

```java
    @Test
    void summaryDetailEmitsSlimRows() {
        OscarTrendService service = ...; // existing setup
        JsonNode root = JsonApi.mapper().readTree(service.trend("2026-05-21", 7, "summary"));
        JsonNode night = root.path("nights").get(0);
        assertTrue(night.has("date"));
        assertFalse(night.has("calendar_date"));
        assertTrue(night.has("respiratory_indices"));
        assertTrue(night.has("events"));
        JsonNode therapy = night.path("therapy");
        assertTrue(therapy.has("pressure"));
        assertFalse(night.path("channels").has("flow_brp"));
        assertFalse(night.has("notable_moments"));
        assertFalse(night.path("events").has("timed_sample"));
    }

    @Test
    void fullDetailKeepsExistingShape() {
        OscarTrendService service = ...;
        JsonNode root = JsonApi.mapper().readTree(service.trend("2026-05-21", 7, "full"));
        JsonNode night = root.path("nights").get(0);
        assertTrue(night.has("notable_moments"));
    }
```

- [ ] **Step 2: Run, verify fail**

Run: `./mvnw test -Dtest=OscarTrendServiceTest`
Expected: FAIL — method signature doesn't accept detail.

- [ ] **Step 3: Add detail parameter and slim projector**

In `OscarTrendService`:

```java
    public String trend(int days) { return trend(days, "summary"); }
    public String trend(int days, String detail) {
        if (days < 1 || days > 90) throw new IllegalArgumentException("days must be between 1 and 90");
        LocalDate end = oscarRepository.getLastSessionDate().orElse(LocalDate.now());
        LocalDate start = end.minusDays(days - 1L);
        return serializeTrend(start, end, mode(detail));
    }
    public String trend(String endDate, int days) { return trend(endDate, days, "summary"); }
    public String trend(String endDate, int days, String detail) {
        LocalDate end = LocalDate.parse(SleepHqPathParams.requireCalendarDate(endDate, "endDate"));
        LocalDate start = end.minusDays(days - 1L);
        return serializeTrend(start, end, mode(detail));
    }

    private static String mode(String detail) {
        if (detail == null || detail.isBlank()) return "summary";
        String m = detail.toLowerCase(Locale.ROOT);
        return m.equals("full") ? "full" : "summary";
    }

    private String serializeTrend(LocalDate start, LocalDate end, String mode) {
        ObjectNode root = JsonApi.mapper().createObjectNode();
        root.put("start_date", start.toString());
        root.put("end_date", end.toString());
        root.put("detail", mode);
        ArrayNode nights = root.putArray("nights");
        Map<String, ObjectNode> bySessionId = new LinkedHashMap<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            nightAnalysisService.analyzeNight(date.toString()).ifPresent(node -> {
                String sessionId = node.path("session").path("session_id").asText("");
                if (!sessionId.isBlank()) {
                    bySessionId.put(sessionId, "full".equals(mode) ? node : slim(node));
                }
            });
        }
        bySessionId.values().forEach(nights::add);
        try {
            return JsonApi.mapper().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize trend", e);
        }
    }

    private ObjectNode slim(ObjectNode night) {
        ObjectNode out = JsonApi.mapper().createObjectNode();
        out.set("date", night.path("date").deepCopy());
        out.set("session", night.path("session").deepCopy());
        out.set("respiratory_indices", night.path("respiratory_indices").deepCopy());
        if (night.has("sleephq")) out.set("sleephq", night.get("sleephq").deepCopy());

        ObjectNode events = JsonApi.mapper().createObjectNode();
        JsonNode src = night.path("events");
        for (String key : new String[] {
                "counts", "summary_counts", "summary_counts_source",
                "eve_total", "summary_total", "total",
                "event_count_authority", "event_counts_agree"}) {
            if (src.has(key)) events.set(key, src.get(key).deepCopy());
        }
        out.set("events", events);

        ObjectNode therapy = JsonApi.mapper().createObjectNode();
        JsonNode channels = night.path("channels");
        for (String key : new String[] {
                "pressure", "ipap", "epap", "leak", "leak_total", "leak_median", "leak_max",
                "ahi", "rdi", "resp_rate", "minute_vent", "tidal_volume"}) {
            if (channels.has(key)) therapy.set(key, channels.get(key).deepCopy());
        }
        out.set("therapy", therapy);
        return out;
    }
```

Add imports for `Locale`, `JsonNode`.

- [ ] **Step 4: Expose `detail` on `OscarTools` `get-oscar-trend`**

In `OscarTools` (find existing `get-oscar-trend` method), add an optional `detail` parameter (default `"summary"`) and pass through.

- [ ] **Step 5: Run tests, verify pass**

Run: `./mvnw test -Dtest=OscarTrendServiceTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/adriangarett/sleephqmcp/service/OscarTrendService.java \
        src/main/java/com/adriangarett/sleephqmcp/tools/OscarTools.java \
        src/test/java/com/adriangarett/sleephqmcp/service/OscarTrendServiceTest.java
git commit -m "feat(oscar): trend detail=summary slim row, default; full retains existing shape"
```

---

### Task 8: Per-night SleepHQ overlay in trend (P1 #7)

**Files:**
- Modify: `src/main/java/com/adriangarett/sleephqmcp/service/OscarTrendService.java`
- Modify: `src/main/java/com/adriangarett/sleephqmcp/service/MachineDateTimeOffsetLoader.java` (read its actual API first — used by CombinedNightService)

The combined-night flow already fetches `machineDateAttrs` per night via the SleepHQ machine_date route. Inject the same loader (or `MachineDateTimeOffsetLoader` / `MachineDateLoader` — whichever provides `ahi_summary`) into `OscarTrendService` and pass it through to `nightAnalysisService.analyzeNight(date, machineDateAttrs, null)`.

- [ ] **Step 1: Read `MachineDateTimeOffsetLoader.java` and `CombinedNightService.java` to identify the loader that returns machine_date `attributes` JsonNode**

Run: `grep -n "machine_date\|machineDateAttrs" src/main/java/com/adriangarett/sleephqmcp/service/*.java`

- [ ] **Step 2: Write failing test**

```java
    @Test
    void summaryDetailIncludesSleepHqAhiWhenMachineDateAvailable() {
        // Stub the machine_date loader to return ahi_summary for one night.
        // Assert nights[0].respiratory_indices.sleephq_ahi_per_hr is present.
    }
```

- [ ] **Step 3: Run, verify fail**

Run: `./mvnw test -Dtest=OscarTrendServiceTest#summaryDetailIncludesSleepHqAhiWhenMachineDateAvailable`
Expected: FAIL.

- [ ] **Step 4: Inject the loader and call per-night**

Constructor:

```java
    public OscarTrendService(OscarRepository oscarRepository,
                             UnifiedNightAnalysisService nightAnalysisService,
                             MachineDateAttributesLoader machineDateLoader) {
        this.oscarRepository = oscarRepository;
        this.nightAnalysisService = nightAnalysisService;
        this.machineDateLoader = machineDateLoader;
    }
```

(Use the actual loader class name discovered in step 1. If only a method on `CombinedNightService` does this, extract a `MachineDateAttributesLoader` interface or reuse the same Spring bean.)

In the loop:

```java
            JsonNode machineDateAttrs = machineDateLoader.loadOrNull(date.toString());
            nightAnalysisService.analyzeNight(date.toString(), machineDateAttrs, null).ifPresent(node -> { ... });
```

Calls to SleepHQ across N nights should fail soft per night — wrap each call in try/catch and continue.

- [ ] **Step 5: Run tests, verify pass**

Run: `./mvnw test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/adriangarett/sleephqmcp/service/OscarTrendService.java \
        src/test/java/com/adriangarett/sleephqmcp/service/OscarTrendServiceTest.java
git commit -m "feat(oscar): fetch SleepHQ machine_date per night in trend output"
```

---

### Task 9: Confirm `session_metric` semantics (P2 #9)

**Files:**
- Modify: `src/main/java/com/adriangarett/sleephqmcp/oscar/OscarChannelCatalog.java` (label/unit cleanup)
- Modify: `CLAUDE.md` (document)

- [ ] **Step 1: Inspect OSCAR `schema.cpp` references**

Run: `grep -rn "0x1158\|SessionMetric\|CPAP_SessionMetric" docs/ context/ 2>/dev/null`

Decide: keep as `session_metric` with note in catalog comment that this is the ResMed extended summary metric. If clearly a therapy hour or % usage, rename accordingly.

- [ ] **Step 2: Apply rename or annotation**

If schema dump confirms it's, e.g., `Time at Pressure %`, update catalog entry to:

```java
        m.put(OscarChannelIds.CPAP_SessionMetric, meta("time_at_pressure_pct", "Time at pressure", "%"));
```

Otherwise tighten label/unit:

```java
        m.put(OscarChannelIds.CPAP_SessionMetric, meta("session_metric", "ResMed session metric (extended)", ""));
```

- [ ] **Step 3: Add a CLAUDE.md note** documenting the choice.

- [ ] **Step 4: Run tests**

Run: `./mvnw test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/adriangarett/sleephqmcp/oscar/OscarChannelCatalog.java CLAUDE.md
git commit -m "docs(oscar): clarify session_metric (0x1158) semantics"
```

---

### Task 10: Drop duplicate `calendar_date` and document trend windowing (P3 #12, #14)

**Files:**
- Modify: `src/main/java/com/adriangarett/sleephqmcp/service/UnifiedNightAnalysisService.java:58-61`
- Modify: `src/main/java/com/adriangarett/sleephqmcp/service/OscarTrendService.java`
- Modify: `docs/sleephq-openapi-gap.md`

- [ ] **Step 1: Decide policy**

`date` and `calendar_date` are identical in the current output. Drop `calendar_date` from the trend slim row (already done in Task 7) and from the night payload itself.

- [ ] **Step 2: Write failing test** — assert `calendar_date` absent from `analyzeNight` output.

```java
    @Test
    void analyzeNightOmitsDuplicateCalendarDate() {
        Optional<ObjectNode> opt = nightAnalysisService.analyzeNight("2026-05-18");
        assertTrue(opt.isPresent());
        assertFalse(opt.get().has("calendar_date"));
        assertEquals("2026-05-18", opt.get().get("date").asText());
    }
```

- [ ] **Step 3: Remove the `calendar_date` setter** in `UnifiedNightAnalysisService` (line 60).

- [ ] **Step 4: Add windowing note to `docs/sleephq-openapi-gap.md`**

> `get-oscar-trend` yields one row per OSCAR session in the calendar window; nights with no session are absent (e.g. 2026-05-20 in our test data).

- [ ] **Step 5: Run tests, verify pass**

Run: `./mvnw test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/adriangarett/sleephqmcp/service/UnifiedNightAnalysisService.java \
        docs/sleephq-openapi-gap.md \
        src/test/java/com/adriangarett/sleephqmcp/service/CombinedNightServiceTest.java
git commit -m "refactor(oscar): drop duplicate calendar_date; document trend session windowing"
```

---

### Task 11: Final integration test + docs sweep

**Files:**
- Modify: `CLAUDE.md`
- Modify: `docs/sleephq-openapi-gap.md`

- [ ] **Step 1: Run full test suite**

Run: `./mvnw test`
Expected: PASS, all suites.

- [ ] **Step 2: Manual smoke**

Run: `./run.sh`
Wait for `Classpath sanity OK`.

Test:
```bash
curl -s -X POST http://localhost:8080/mcp \
  -H "X-SleepHQ-MCP-Key: $SLEEPHQ_MCP_API_KEY" \
  -d '{"method":"tools/call","params":{"name":"get-combined-night-by-date","arguments":{"date":"2026-05-18"}}}'
```

Verify:
- `channels.*` has **no** `clear_airway`, `obstructive`, `hypopnea` keys (event channels belong in `events`, not `channels`).
- Every key in `events.counts` exists in `events.summary_counts` with the **same value**; extra zero-count keys only in `summary_counts` are OK.
- `events.event_count_authority` is `oscar_summary_000` when summary present.
- `events.event_counts_agree` is `true` on 2026-05-18 (totals match).
- No `calendar_date` field.
- No `Recording starts` in `events.timed_sample`.

Full Goose checklist: [`docs/smoke-test-oscar-mcp.md`](../../smoke-test-oscar-mcp.md).

Then:
```bash
curl -s -X POST http://localhost:8080/mcp \
  -H "X-SleepHQ-MCP-Key: $SLEEPHQ_MCP_API_KEY" \
  -d '{"method":"tools/call","params":{"name":"get-oscar-trend","arguments":{"days":7}}}'
```

Verify slim shape and per-night `sleephq_ahi_per_hr` when available.

- [ ] **Step 3: Update `CLAUDE.md` "Key conventions" with two new bullets**

> - **Canonical event labels:** EVE labels map to the same field names as `.000` counts via `OscarEventLabelCanonicalizer`. `counts` is sparse (EVE); `summary_counts` is full (dashboard, incl. zeros). Event channels (0x1001–0x1028) are excluded from `channels.*`.
> - **Trend payload mode:** `get-oscar-trend(detail="summary"|"full")` — default `summary` emits one slim row per session; `full` returns the same shape as `get-combined-night-by-date`.

- [ ] **Step 4: Commit docs**

```bash
git add CLAUDE.md docs/sleephq-openapi-gap.md
git commit -m "docs: canonical event labels and trend detail modes"
```

---

## Self-review notes

- **Coverage:** Items P0 #1 (Task 4), #2 (Tasks 2+3), #3 (Task 1); P1 #4 (Task 3), #5 (Task 3), #6 (Task 7), #7 (Task 8); P2 #8 (Task 6), #9 (Task 9), #10 (Task 5), #11 (covered by canonicalizer arousal→rera in Task 2); P3 #12 (Task 7+10), #13 (no change, noted in Task 7), #14 (Task 10 docs).
- **Type consistency:** New `coverageNode` signature has 7 args; all call sites updated in Task 6. `slim()` projector keys match the JSON shape from `UnifiedNightAnalysisService`.
- **Placeholders:** Task 5 leaves the fixture-based unit test optional because the project's existing waveform tests rely on real EDF fixtures; the mapper edit itself is shown in full. Task 9 has a decision branch documented but the final string depends on schema.cpp confirmation — flagged explicitly.
