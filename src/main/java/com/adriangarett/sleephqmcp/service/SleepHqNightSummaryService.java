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

        Resolved cpap = resolve((src, d) -> src.cpapSessions(d), date);
        Map<String, NightChannelSummary> cpapChannels =
                buildCpapChannels(cpap.files(), cpapSessions, cpapAnalysed);

        Resolved o2 = resolve((src, d) -> src.o2Sessions(d), date);
        Map<String, NightChannelSummary> o2Channels =
                buildO2Channels(o2.files(), o2Sessions, o2Analysed);

        coverage.put("cpap", !cpapChannels.isEmpty());
        coverage.put("oximetry", !o2Channels.isEmpty());
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
        addMirrorFreshness(provenance);
        root.set("provenance", provenance);

        return serialize(root);
    }

    private record Resolved(List<NightSessionFile> files, String source) {}

    private interface SessionFn {
        List<NightSessionFile> apply(NightFileSource src, String date);
    }

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
        Map<String, List<Double>> samplesByField = new LinkedHashMap<>();
        Map<String, String> unitByField = new LinkedHashMap<>();
        Map<String, Double> rateByField = new LinkedHashMap<>();

        for (NightSessionFile sf : files) {
            ObjectNode s = sessionsOut.addObject();
            s.put("name", sf.name());
            s.put("start", sf.start() == null ? null : sf.start().toString());
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
                    samplesByField.computeIfAbsent(field, k -> new ArrayList<>()).addAll(ch.samples());
                    unitByField.putIfAbsent(field, ch.unit());
                    rateByField.putIfAbsent(field, ch.sampleRate());
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
        for (Map.Entry<String, List<Double>> e : samplesByField.entrySet()) {
            NightChannelSummary summary = NightSummaryComputer.summarise(
                    e.getKey(), unitByField.get(e.getKey()), e.getValue(), rateByField.get(e.getKey()));
            if (summary != null) {
                channels.put(e.getKey(), summary);
            }
        }
        return channels;
    }

    private Map<String, NightChannelSummary> buildO2Channels(List<NightSessionFile> files,
                                                             ArrayNode sessionsOut, int[] analysed) {
        List<Double> spo2 = new ArrayList<>();
        List<Double> pulse = new ArrayList<>();
        List<Double> motion = new ArrayList<>();
        double interval = 1.0;
        for (NightSessionFile sf : files) {
            ObjectNode s = sessionsOut.addObject();
            s.put("name", sf.name());
            s.put("start", sf.start() == null ? null : sf.start().toString());
            try {
                OximetryResult r = ViatomSessionParser.parse(sf.bytes().get(), sf.name(), FULL_NIGHT_SECONDS);
                if (r.intervalSeconds() > 0) {
                    interval = r.intervalSeconds();
                }
                int n = 0;
                for (OximetrySample sample : r.samples()) {
                    if (sample.invalid() || sample.spo2() < 0 || sample.pulseBpm() < 0) {
                        continue;
                    }
                    spo2.add((double) sample.spo2());
                    pulse.add((double) sample.pulseBpm());
                    motion.add((double) sample.motion());
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
        double rate = interval > 0 ? 1.0 / interval : 1.0;
        Map<String, NightChannelSummary> channels = new LinkedHashMap<>();
        putIfPresent(channels, "spo2", NightSummaryComputer.summarise("spo2", "%", spo2, rate));
        putIfPresent(channels, "pulse_rate", NightSummaryComputer.summarise("pulse_rate", "bpm", pulse, rate));
        putIfPresent(channels, "movement", NightSummaryComputer.summarise("movement", "", motion, rate));
        return channels;
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
