package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.config.OscarProperties;
import com.adriangarett.sleephqmcp.domain.ChannelStatistics;
import com.adriangarett.sleephqmcp.domain.DeviceEvent;
import com.adriangarett.sleephqmcp.domain.DeviceEventResult;
import com.adriangarett.sleephqmcp.domain.OscarEdfPaths;
import com.adriangarett.sleephqmcp.domain.OscarSession;
import com.adriangarett.sleephqmcp.domain.OscarSessionIndexEntry;
import com.adriangarett.sleephqmcp.oscar.OscarChannelHistogram;
import com.adriangarett.sleephqmcp.oscar.OscarChannelStatistics;
import com.adriangarett.sleephqmcp.oscar.OscarEventCorrelator;
import com.adriangarett.sleephqmcp.oscar.OscarEventSummaryBuilder;
import com.adriangarett.sleephqmcp.oscar.OscarRepository;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.NightAnalysisSupport;
import com.adriangarett.sleephqmcp.support.NightDataConflictAnalyzer;
import com.adriangarett.sleephqmcp.support.OscarFreshness;
import com.adriangarett.sleephqmcp.support.SleepHqPathParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UnifiedNightAnalysisService {

    private final OscarRepository oscarRepository;

    public UnifiedNightAnalysisService(OscarRepository oscarRepository) {
        this.oscarRepository = oscarRepository;
    }

    public Optional<ObjectNode> analyzeNight(String calendarDate) {
        return analyzeNight(calendarDate, null, null);
    }

    public Optional<ObjectNode> analyzeNight(String calendarDate, JsonNode machineDateAttrs, JsonNode journalAttrs) {
        String date = SleepHqPathParams.requireCalendarDate(calendarDate, "date");
        LocalDate localDate = LocalDate.parse(date);
        if (!oscarRepository.isConfigured()) {
            return Optional.empty();
        }
        Optional<OscarSessionIndexEntry> sessionOpt = oscarRepository.findSessionForDate(localDate);
        if (sessionOpt.isEmpty()) {
            return Optional.empty();
        }
        OscarSessionIndexEntry indexEntry = sessionOpt.get();
        OscarProperties.Analysis analysis = resolveAnalysis();
        Optional<OscarSession> oscarSession = oscarRepository.loadSession(indexEntry);

        Optional<LocalDate> lastSessionDateOpt = oscarRepository.getLastSessionDate();
        Long oscarExportLagDays = null;
        String oscarExportFreshness = null;
        if (lastSessionDateOpt.isPresent()) {
            LocalDate today = LocalDate.now(ZoneId.systemDefault());
            oscarExportLagDays = OscarFreshness.exportLagDays(lastSessionDateOpt.get(), today);
            oscarExportFreshness = OscarFreshness.categoryFromExportLagDays(oscarExportLagDays);
        }

        ObjectNode nightAnalysis = JsonApi.mapper().createObjectNode();
        nightAnalysis.put("date", date);

        Map<String, Double> header =
                oscarRepository.loadSummaryHeader(indexEntry).orElse(null);

        ObjectNode sessionNode = NightAnalysisSupport.sessionNode(indexEntry, header);
        LocalDate sessionStartDate = indexEntry.firstInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        LocalDate sessionEndDate = indexEntry.lastInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        if (sessionStartDate.isBefore(localDate) && sessionEndDate.equals(localDate)) {
            sessionNode.put("session_date_note", "matched_end_date");
        }
        nightAnalysis.set("session", sessionNode);

        Map<String, ChannelStatistics> channelStats = new LinkedHashMap<>();
        if (oscarSession.isPresent()) {
            Map<String, OscarChannelHistogram> histograms =
                    oscarRepository.loadChannelHistograms(localDate);
            OscarChannelStatistics.mergePreferEdf(channelStats,
                    OscarChannelStatistics.fromSummarySession(oscarSession.get(), histograms));
        }

        Optional<OscarEdfPaths> edfPaths = oscarRepository.edfPathsForSession(indexEntry);
        Optional<Map<String, Integer>> summaryEventCounts = oscarRepository.loadSummaryEventCounts(indexEntry);
        String sessionStart = indexEntry.firstInstant().toString().substring(0, 19);
        List<DeviceEvent> events = List.of();
        boolean hasPld = false;
        boolean pldHasStats = false;
        boolean hasEve = false;
        boolean hasBrp = false;
        boolean brpHasStats = false;

        if (edfPaths.isPresent()) {
            OscarEdfPaths paths = edfPaths.get();
            if (paths.pld().isPresent()) {
                hasPld = true;
                Map<String, ChannelStatistics> pldStats = loadPld(paths.pld().get(), analysis.percentile());
                pldHasStats = pldStats.values().stream().anyMatch(s -> s.sampleCount() > 0);
                mergeStats(channelStats, pldStats);
            }
            if (paths.brp().isPresent()) {
                hasBrp = true;
                Map<String, ChannelStatistics> brpStats = loadBrp(paths.brp().get(), analysis.percentile());
                brpHasStats = brpStats.values().stream().anyMatch(s -> s.sampleCount() > 0);
                mergeStats(channelStats, brpStats);
            }
            Optional<DeviceEventResult> eve = paths.eve().flatMap(oscarRepository::loadEvents);
            if (eve.isPresent()) {
                hasEve = true;
                events = eve.get().events();
                nightAnalysis.set("events", OscarEventSummaryBuilder.buildSummary(
                        eve.get(), analysis.maxTimedEvents(), summaryEventCounts));
                sessionStart = eve.get().startDatetime().substring(0, 19);
            } else if (summaryEventCounts.isPresent()) {
                nightAnalysis.set("events",
                        OscarEventSummaryBuilder.buildSummaryOnly(summaryEventCounts.get()));
            }
        }

        if (!nightAnalysis.has("events") && summaryEventCounts.isPresent()) {
            nightAnalysis.set("events", OscarEventSummaryBuilder.buildSummaryOnly(summaryEventCounts.get()));
        }

        ObjectNode channelsNode = NightAnalysisSupport.channelStatsNode(channelStats);
        if (oscarSession.isPresent() && channelStats.isEmpty()) {
            channelsNode = NightAnalysisSupport.summaryChannelNode(oscarSession.get());
        }
        nightAnalysis.set("channels", channelsNode);
        nightAnalysis.set("respiratory_indices",
                NightAnalysisSupport.respiratoryIndices(oscarSession, machineDateAttrs));

        NightAnalysisSupport.attachSleepHq(nightAnalysis, machineDateAttrs, journalAttrs);

        if (oscarSession.isPresent()) {
            ArrayNode conflicts = NightDataConflictAnalyzer.analyze(machineDateAttrs, oscarSession.get());
            if (!conflicts.isEmpty()) {
                nightAnalysis.set("data_conflicts", conflicts);
            }
        }

        ArrayNode sources = nightAnalysis.putArray("data_sources");
        if (machineDateAttrs != null) {
            sources.add("sleephq");
        }
        sources.add("oscar_sqlite");
        if (oscarSession.isPresent() && !oscarSession.get().channels().isEmpty()) {
            sources.add("oscar_sqlite_session");
        }
        // Gate EDF data_sources on actually-loaded stats, not mere file presence, so a present-but-unparsed
        // PLD/BRP file never fabricates a "data was used" claim. (Coverage still reports file presence below.)
        if (pldHasStats) {
            sources.add("oscar_pld_edf");
        }
        if (hasEve) {
            sources.add("oscar_eve_edf");
        }
        if (brpHasStats) {
            sources.add("oscar_brp_edf");
        }

        // Detailed provenance mapping
        ObjectNode provenance = nightAnalysis.putObject("provenance");
        if (machineDateAttrs != null) {
            ObjectNode shqProv = provenance.putObject("sleephq");
            shqProv.put("type", "api");
            shqProv.put("available", true);
        }
        ObjectNode oscarProv = provenance.putObject("oscar_sqlite");
        oscarProv.put("type", "sqlite");
        oscarProv.put("available", true);
        if (lastSessionDateOpt.isPresent()) {
            oscarProv.put("last_session_date", lastSessionDateOpt.get().toString());
            oscarProv.put("freshness_scope", OscarFreshness.SCOPE_EXPORT);
            if (oscarExportLagDays != null) {
                oscarProv.put("export_lag_days", oscarExportLagDays);
            }
            if (oscarExportFreshness != null) {
                oscarProv.put("export_freshness", oscarExportFreshness);
            }
        }
        if (journalAttrs != null && journalAttrs.isObject()) {
            ObjectNode journalProv = provenance.putObject("sleephq_journal");
            journalProv.put("type", "api");
            journalProv.put("available", true);
        }

        if (oscarSession.isPresent() && !oscarSession.get().channels().isEmpty()) {
            ObjectNode sum000Prov = provenance.putObject("oscar_sqlite_session");
            sum000Prov.put("type", "sqlite");
            sum000Prov.put("available", true);
        }
        if (hasPld) {
            ObjectNode pldProv = provenance.putObject("oscar_pld_edf");
            pldProv.put("type", "local_file");
            pldProv.put("present", true);
            pldProv.put("available", pldHasStats);
        }
        if (hasEve) {
            ObjectNode eveProv = provenance.putObject("oscar_eve_edf");
            eveProv.put("type", "local_file");
            eveProv.put("available", true);
        }
        if (hasBrp) {
            ObjectNode brpProv = provenance.putObject("oscar_brp_edf");
            brpProv.put("type", "local_file");
            brpProv.put("present", true);
            brpProv.put("available", brpHasStats);
        }

        nightAnalysis.set("coverage", NightAnalysisSupport.coverageNode(
                machineDateAttrs != null,
                oscarSession.isPresent(),
                hasPld,
                hasEve,
                hasBrp,
                channelsNode.size(),
                pldHasStats,
                oscarExportLagDays,
                oscarExportFreshness));

        List<ObjectNode> moments = OscarEventCorrelator.buildNotableMoments(
                channelStats,
                events,
                sessionStart,
                analysis.correlationWindowSeconds(),
                analysis.maxNotableMoments(),
                analysis.maxNearbyEventsPerMoment());
        NightAnalysisSupport.attachNotableMoments(nightAnalysis, moments);

        return Optional.of(nightAnalysis);
    }

    private static void mergeStats(Map<String, ChannelStatistics> target, Map<String, ChannelStatistics> source) {
        source.forEach(target::put);
    }

    private Map<String, ChannelStatistics> loadPld(Path pld, int percentile) {
        return Map.of();
    }

    private Map<String, ChannelStatistics> loadBrp(Path brp, int percentile) {
        return Map.of();
    }

    private OscarProperties.Analysis resolveAnalysis() {
        OscarProperties.Analysis analysis = oscarRepository.properties().analysis();
        return analysis != null ? analysis : new OscarProperties.Analysis(95, 120, 20, 100, 5);
    }
}
