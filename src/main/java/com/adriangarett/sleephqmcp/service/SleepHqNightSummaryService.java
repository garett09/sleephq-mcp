package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.domain.NightChannelSummary;
import com.adriangarett.sleephqmcp.domain.NightSessionFile;
import com.adriangarett.sleephqmcp.domain.OximetryResult;
import com.adriangarett.sleephqmcp.domain.OximetrySample;
import com.adriangarett.sleephqmcp.domain.WaveformChannel;
import com.adriangarett.sleephqmcp.domain.WaveformResult;
import com.adriangarett.sleephqmcp.support.EdfParser;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.NightSummaryComputer;
import com.adriangarett.sleephqmcp.support.NightSummaryValidation;
import com.adriangarett.sleephqmcp.support.ViatomSessionParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SleepHqNightSummaryService {

    private static final int FULL_NIGHT_SECONDS = 12 * 3600;
    /** Reject incomplete local PLD (mask blips); fall back to API for a full therapy night. */
    private static final int MIN_CPAP_ANALYSED_SECONDS = 2 * 3600;

    private final LocalNightFileSource localSource;
    private final ApiNightFileSource apiSource;
    private final SleepHqClient client;
    private final ClinicalContextProperties clinical;
    private final MachineDateAttributesLoader machineDateAttributesLoader;
    private final String syncReportPath;

    public SleepHqNightSummaryService(LocalNightFileSource localSource,
                                      ApiNightFileSource apiSource,
                                      SleepHqClient client,
                                      ClinicalContextProperties clinical,
                                      MachineDateAttributesLoader machineDateAttributesLoader,
                                      @Value("${sleephq.local.sync-report-path:}") String syncReportPath) {
        this.localSource = localSource;
        this.apiSource = apiSource;
        this.client = client;
        this.clinical = clinical;
        this.machineDateAttributesLoader = machineDateAttributesLoader;
        this.syncReportPath = syncReportPath;
    }

    public String getNightSummary(String date, String cpapMachineId, String o2MachineId) {
        ObjectNode root = JsonApi.mapper().createObjectNode();
        root.put("date", date);
        root.put("source", "sleephq");
        ObjectNode coverage = root.putObject("coverage");

        ObjectNode provenance = JsonApi.mapper().createObjectNode();
        ArrayNode cpapSessions = provenance.putArray("cpap_sessions");
        ArrayNode o2Sessions = provenance.putArray("o2_sessions");
        int[] cpapAnalysed = {0};
        int[] o2Analysed = {0};

        CpapResolveOutcome cpapOutcome = resolveCpap(date);
        Resolved cpap = cpapOutcome.resolved();
        Map<String, NightChannelSummary> cpapChannels =
                buildCpapChannels(cpap.files(), cpapSessions, cpapAnalysed);

        Resolved o2 = resolve((src, d) -> src.o2Sessions(d), date);
        Map<String, NightChannelSummary> o2Channels =
                buildO2Channels(o2.files(), o2Sessions, o2Analysed);

        coverage.put("cpap", !cpapChannels.isEmpty());
        if (cpapChannels.isEmpty()) {
            coverage.put("cpap_reason", cpapCoverageReason(cpap.files(), cpapSessions));
        }
        coverage.put("oximetry", !o2Channels.isEmpty());
        if (o2Channels.isEmpty()) {
            coverage.put("oximetry_reason", o2CoverageReason(o2.files(), o2Sessions));
        }
        if (cpapChannels.isEmpty() && o2Channels.isEmpty()) {
            throw new IllegalArgumentException(
                    "no_sleephq_data_for_date: no PLD or O2 sessions found locally or via API for " + date);
        }

        if (!cpapChannels.isEmpty()) {
            root.putObject("cpap").set("channels", channelsNode(cpapChannels));
        }
        if (!o2Channels.isEmpty()) {
            root.putObject("oximetry").set("channels", channelsNode(o2Channels));
        }

        Map<String, NightChannelSummary> all = new LinkedHashMap<>(cpapChannels);
        all.putAll(o2Channels);
        JsonNode cpapAttrs = machineDateAttributesLoader.loadOrNull(date);
        JsonNode o2Attrs = loadO2AttributesOrNull(orDefault(o2MachineId, clinical.defaultO2MachineId()), date);
        ObjectNode validation = NightSummaryValidation.build(all, cpapAttrs, o2Attrs);
        if (validation != null) {
            root.set("validation", validation);
        }

        provenance.put("machine_model", resolveModel(orDefault(cpapMachineId, clinical.defaultCpapMachineId())));
        provenance.put("session_count", contributing(cpapSessions) + contributing(o2Sessions));
        provenance.put("cpap_source", cpapChannels.isEmpty() ? null : cpap.source());
        provenance.put("o2_source", o2Channels.isEmpty() ? null : o2.source());
        provenance.put("cpap_analysed_seconds", cpapAnalysed[0]);
        provenance.put("o2_analysed_seconds", o2Analysed[0]);
        if (cpapOutcome.localSkippedReason() != null) {
            provenance.put("cpap_local_skipped_reason", cpapOutcome.localSkippedReason());
            provenance.put("cpap_local_analysed_seconds", cpapOutcome.localAnalysedSeconds());
        }
        addMirrorFreshness(provenance);
        root.set("provenance", provenance);

        return serialize(root);
    }

    private record Resolved(List<NightSessionFile> files, String source) {}

    private record CpapResolveOutcome(Resolved resolved, String localSkippedReason, int localAnalysedSeconds) {
        static CpapResolveOutcome of(Resolved resolved) {
            return new CpapResolveOutcome(resolved, null, 0);
        }
    }

    private interface SessionFn {
        List<NightSessionFile> apply(NightFileSource src, String date);
    }

    /**
     * Local PLD first unless total analysed duration is below {@link #MIN_CPAP_ANALYSED_SECONDS}
     * (e.g. a 10-minute mask blip while OSCAR shows 8+ hours) — then use API PLD when available.
     */
    private CpapResolveOutcome resolveCpap(String date) {
        if (localSource.available()) {
            try {
                List<NightSessionFile> local = localSource.cpapSessions(date);
                if (!local.isEmpty()) {
                    int localSeconds = estimatePldAnalysedSeconds(local);
                    if (localSeconds >= MIN_CPAP_ANALYSED_SECONDS) {
                        return CpapResolveOutcome.of(new Resolved(local, localSource.label()));
                    }
                    Resolved api = tryApiCpap(date);
                    if (!api.files().isEmpty()) {
                        return new CpapResolveOutcome(api, "local_session_too_short", localSeconds);
                    }
                    return new CpapResolveOutcome(new Resolved(local, localSource.label()),
                            "local_session_too_short", localSeconds);
                }
            } catch (RuntimeException ignored) {
                // fall through
            }
        }
        return CpapResolveOutcome.of(resolve((src, d) -> src.cpapSessions(d), date));
    }

    private Resolved tryApiCpap(String date) {
        if (!apiSource.available()) {
            return new Resolved(List.of(), null);
        }
        try {
            List<NightSessionFile> api = apiSource.cpapSessions(date);
            return api.isEmpty() ? new Resolved(List.of(), null) : new Resolved(api, apiSource.label());
        } catch (RuntimeException e) {
            return new Resolved(List.of(), null);
        }
    }

    private static int estimatePldAnalysedSeconds(List<NightSessionFile> files) {
        int total = 0;
        for (NightSessionFile sf : files) {
            try {
                WaveformResult parsed = EdfParser.parse(sf.bytes().get(), 0, FULL_NIGHT_SECONDS);
                if (parsed.durationSeconds() > 0) {
                    total += (int) Math.round(parsed.durationSeconds());
                }
            } catch (RuntimeException ignored) {
                // skip unreadable file
            }
        }
        return total;
    }

    /** Local mirror first, SleepHQ API when the night is not on disk. */
    private Resolved resolve(SessionFn fn, String date) {
        if (localSource.available()) {
            try {
                List<NightSessionFile> local = fn.apply(localSource, date);
                if (!local.isEmpty()) {
                    return new Resolved(local, localSource.label());
                }
            } catch (RuntimeException ignored) {
                // fall through to API
            }
        }
        if (apiSource.available()) {
            try {
                List<NightSessionFile> api = fn.apply(apiSource, date);
                if (!api.isEmpty()) {
                    return new Resolved(api, apiSource.label());
                }
            } catch (RuntimeException ignored) {
                // no data
            }
        }
        return new Resolved(List.of(), null);
    }

    private Map<String, NightChannelSummary> buildCpapChannels(List<NightSessionFile> files,
                                                               ArrayNode sessionsOut, int[] analysed) {
        Map<String, PldChannelAccumulator> accumulators = new LinkedHashMap<>();

        for (NightSessionFile sf : files) {
            ObjectNode s = sessionsOut.addObject();
            appendSessionIdentity(s, sf);
            try {
                WaveformResult parsed = EdfParser.parse(sf.bytes().get(), 0, FULL_NIGHT_SECONDS);
                if (parsed.durationSeconds() <= 0) {
                    s.put("sample_count", 0);
                    s.put("note", "empty_session");
                    continue;
                }
                int sessionSamples = 0;
                for (WaveformChannel ch : parsed.channels()) {
                    String field = NightSummaryComputer.mapPldLabel(ch.label());
                    if (field == null || ch.samples() == null || ch.samples().isEmpty()) {
                        continue;
                    }
                    accumulators.computeIfAbsent(field, k -> new PldChannelAccumulator())
                            .append(ch);
                    sessionSamples = Math.max(sessionSamples, ch.samples().size());
                }
                int durationSec = (int) Math.round(parsed.durationSeconds());
                analysed[0] += durationSec;
                s.put("duration_seconds", durationSec);
                s.put("sample_count", sessionSamples);
            } catch (RuntimeException e) {
                s.put("sample_count", 0);
                s.put("error", "download_or_parse_failed");
            }
        }

        Map<String, NightChannelSummary> channels = new LinkedHashMap<>();
        for (Map.Entry<String, PldChannelAccumulator> e : accumulators.entrySet()) {
            NightChannelSummary summary = e.getValue().summarise(e.getKey());
            if (summary != null) {
                channels.put(e.getKey(), summary);
            }
        }
        return channels;
    }

    private Map<String, NightChannelSummary> buildO2Channels(List<NightSessionFile> files,
                                                             ArrayNode sessionsOut, int[] analysed) {
        O2ChannelAccumulator o2 = new O2ChannelAccumulator();

        for (NightSessionFile sf : files) {
            ObjectNode s = sessionsOut.addObject();
            appendSessionIdentity(s, sf);
            try {
                OximetryResult r = ViatomSessionParser.parse(sf.bytes().get(), sf.name(), FULL_NIGHT_SECONDS);
                double rate = r.intervalSeconds() > 0 ? 1.0 / r.intervalSeconds() : 1.0;
                int n = 0;
                for (OximetrySample sample : r.samples()) {
                    if (sample.invalid() || sample.spo2() < 0 || sample.pulseBpm() < 0) {
                        continue;
                    }
                    o2.append(sample, rate);
                    n++;
                }
                int durationSec = (int) Math.round(r.durationSeconds());
                analysed[0] += durationSec;
                s.put("duration_seconds", durationSec);
                s.put("sample_count", n);
            } catch (RuntimeException e) {
                s.put("sample_count", 0);
                s.put("error", "download_or_parse_failed");
            }
        }

        Map<String, NightChannelSummary> channels = new LinkedHashMap<>();
        putIfPresent(channels, "spo2", o2.spo2Summary());
        putIfPresent(channels, "pulse_rate", o2.pulseSummary());
        putIfPresent(channels, "movement", o2.motionSummary());
        return channels;
    }

    /** Concatenates PLD samples across sessions, then one OSCAR-style percentile pass per channel. */
    private static final class PldChannelAccumulator {
        private String unit;
        private String edfLabel;
        private double sampleRate;
        private final List<Double> samples = new ArrayList<>();

        void append(WaveformChannel ch) {
            if (unit == null) {
                unit = ch.unit();
                edfLabel = ch.label();
            }
            sampleRate = ch.sampleRate();
            samples.addAll(ch.samples());
        }

        NightChannelSummary summarise(String field) {
            return samples.isEmpty()
                    ? null
                    : NightSummaryComputer.summarise(field, unit, edfLabel, samples, sampleRate);
        }
    }

    /** Valid oximetry samples only (invalid / -1 sentinels skipped); concatenated across sessions. */
    private static final class O2ChannelAccumulator {
        private double sampleRate = 1.0;
        private final List<Double> spo2 = new ArrayList<>();
        private final List<Double> pulse = new ArrayList<>();
        private final List<Double> motion = new ArrayList<>();

        void append(OximetrySample sample, double rate) {
            sampleRate = rate;
            spo2.add((double) sample.spo2());
            pulse.add((double) sample.pulseBpm());
            motion.add((double) sample.motion());
        }

        NightChannelSummary spo2Summary() {
            return NightSummaryComputer.summarise("spo2", "%", spo2, sampleRate);
        }

        NightChannelSummary pulseSummary() {
            return NightSummaryComputer.summarise("pulse_rate", "bpm", pulse, sampleRate);
        }

        NightChannelSummary motionSummary() {
            return NightSummaryComputer.summarise("movement", "", motion, sampleRate);
        }
    }

    private void putIfPresent(Map<String, NightChannelSummary> map, String key, NightChannelSummary v) {
        if (v != null) {
            map.put(key, v);
        }
    }

    private ObjectNode channelsNode(Map<String, NightChannelSummary> channels) {
        ObjectNode node = JsonApi.mapper().createObjectNode();
        for (Map.Entry<String, NightChannelSummary> e : channels.entrySet()) {
            node.set(e.getKey(), JsonApi.mapper().valueToTree(e.getValue()));
        }
        return node;
    }

    private static void appendSessionIdentity(ObjectNode session, NightSessionFile sf) {
        session.put("filename", sf.name());
        if (sf.fileId() != null && !sf.fileId().isBlank()) {
            session.put("file_id", sf.fileId());
        }
        if (sf.start() != null) {
            session.put("start", sf.start().toString());
        }
    }

    /** Machine-readable reason when {@code coverage.cpap} is false — do not invent PLD data. */
    private static String cpapCoverageReason(List<NightSessionFile> resolvedFiles, ArrayNode sessions) {
        if (resolvedFiles.isEmpty()) {
            return "no_sleephq_pld";
        }
        if (contributing(sessions) == 0) {
            return "cpap_sessions_empty";
        }
        return "cpap_channels_empty";
    }

    /** Machine-readable reason when {@code coverage.oximetry} is false. */
    private static String o2CoverageReason(List<NightSessionFile> resolvedFiles, ArrayNode sessions) {
        if (resolvedFiles.isEmpty()) {
            return "no_sleephq_o2";
        }
        if (contributing(sessions) == 0) {
            return "o2_sessions_empty";
        }
        return "o2_channels_empty";
    }

    private static int contributing(ArrayNode sessions) {
        int n = 0;
        for (JsonNode s : sessions) {
            if (s.path("sample_count").asInt(0) > 0) {
                n++;
            }
        }
        return n;
    }

    private JsonNode loadO2AttributesOrNull(String o2MachineId, String date) {
        if (o2MachineId == null || o2MachineId.isBlank()) {
            return null;
        }
        try {
            JsonNode doc = JsonApi.parse(client.getMachineDateByDate(o2MachineId, date));
            return JsonApi.hasSingleResourceData(doc) ? JsonApi.singleResourceData(doc).path("attributes") : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String resolveModel(String cpapMachineId) {
        if (cpapMachineId == null || cpapMachineId.isBlank()) {
            return null;
        }
        try {
            JsonNode attrs = JsonApi.attributes(JsonApi.parse(client.getMachine(cpapMachineId)));
            String combined = (attrs.path("brand").asText("").trim() + " "
                    + attrs.path("model").asText("").trim()).trim();
            return combined.isBlank() ? null : combined;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void addMirrorFreshness(ObjectNode provenance) {
        if (syncReportPath == null || syncReportPath.isBlank()) {
            return;
        }
        Path p = Path.of(syncReportPath);
        if (!Files.isRegularFile(p)) {
            return;
        }
        try {
            JsonNode report = JsonApi.parse(Files.readString(p));
            String ts = report.path("timestamp").asText(null);
            if (ts == null || ts.isBlank()) {
                return;
            }
            provenance.put("local_mirror_synced_at", ts);
            try {
                OffsetDateTime synced = OffsetDateTime.parse(ts,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XX"));
                long hours = Duration.between(synced.toInstant(), Instant.now()).toHours();
                provenance.put("local_mirror_age_hours", hours);
            } catch (RuntimeException ignored) {
                // raw timestamp only
            }
        } catch (Exception ignored) {
            // freshness unavailable
        }
    }

    private static String orDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private static String serialize(Object value) {
        try {
            return JsonApi.mapper().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize night summary", e);
        }
    }
}
