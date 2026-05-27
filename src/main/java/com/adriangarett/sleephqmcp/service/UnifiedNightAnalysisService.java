package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.config.OscarProperties;
import com.adriangarett.sleephqmcp.domain.ChannelStatistics;
import com.adriangarett.sleephqmcp.domain.DeviceEvent;
import com.adriangarett.sleephqmcp.domain.DeviceEventResult;
import com.adriangarett.sleephqmcp.domain.OscarEdfPaths;
import com.adriangarett.sleephqmcp.domain.OscarSession;
import com.adriangarett.sleephqmcp.domain.OscarSessionIndexEntry;
import com.adriangarett.sleephqmcp.oscar.OscarChannelStatistics;
import com.adriangarett.sleephqmcp.oscar.OscarEventCorrelator;
import com.adriangarett.sleephqmcp.oscar.OscarEventSummaryBuilder;
import com.adriangarett.sleephqmcp.oscar.OscarRepository;
import com.adriangarett.sleephqmcp.oscar.OscarSummaryHeaderParser;
import com.adriangarett.sleephqmcp.oscar.OscarWaveformStatistics;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.NightAnalysisSupport;
import com.adriangarett.sleephqmcp.support.SleepHqPathParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDate;
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

        ObjectNode nightAnalysis = JsonApi.mapper().createObjectNode();
        nightAnalysis.put("date", date);
        nightAnalysis.put("calendar_date", date);

        OscarSummaryHeaderParser.SummaryHeader header =
                oscarRepository.loadSummaryHeader(indexEntry).orElse(null);
        nightAnalysis.set("session", NightAnalysisSupport.sessionNode(indexEntry, header));

        Map<String, ChannelStatistics> channelStats = new LinkedHashMap<>();
        if (oscarSession.isPresent()) {
            OscarChannelStatistics.mergePreferEdf(channelStats,
                    OscarChannelStatistics.fromSummarySession(oscarSession.get()));
        }

        Optional<OscarEdfPaths> edfPaths = oscarRepository.edfPathsForSession(indexEntry);
        String sessionStart = indexEntry.firstInstant().toString().substring(0, 19);
        List<DeviceEvent> events = List.of();
        boolean hasPld = false;
        boolean hasEve = false;
        boolean hasBrp = false;

        if (edfPaths.isPresent()) {
            OscarEdfPaths paths = edfPaths.get();
            if (paths.pld().isPresent()) {
                hasPld = true;
                mergeStats(channelStats, loadPld(paths.pld().get(), analysis.percentile()));
            }
            if (paths.brp().isPresent()) {
                hasBrp = true;
                mergeStats(channelStats, loadBrp(paths.brp().get(), analysis.percentile()));
            }
            Optional<Map<String, Integer>> summaryEventCounts =
                    oscarRepository.loadSummaryEventCounts(indexEntry);
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

        ObjectNode channelsNode = NightAnalysisSupport.channelStatsNode(channelStats);
        if (oscarSession.isPresent() && channelStats.isEmpty()) {
            channelsNode = NightAnalysisSupport.summaryChannelNode(oscarSession.get());
        }
        nightAnalysis.set("channels", channelsNode);
        nightAnalysis.set("respiratory_indices",
                NightAnalysisSupport.respiratoryIndices(oscarSession, machineDateAttrs));

        NightAnalysisSupport.attachSleepHq(nightAnalysis, machineDateAttrs, journalAttrs);

        ArrayNode sources = nightAnalysis.putArray("data_sources");
        if (machineDateAttrs != null) {
            sources.add("sleephq");
        }
        sources.add("oscar_summaries_xml");
        if (oscarSession.isPresent() && !oscarSession.get().channels().isEmpty()) {
            sources.add("oscar_summary_000");
        }
        if (hasPld) {
            sources.add("oscar_pld_edf");
        }
        if (hasEve) {
            sources.add("oscar_eve_edf");
        }
        if (hasBrp) {
            sources.add("oscar_brp_edf");
        }

        nightAnalysis.set("coverage", NightAnalysisSupport.coverageNode(
                machineDateAttrs != null,
                oscarSession.isPresent(),
                hasPld,
                hasEve,
                hasBrp,
                channelsNode.size()));

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
        try {
            return OscarWaveformStatistics.fromPld(pld, percentile);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, ChannelStatistics> loadBrp(Path brp, int percentile) {
        try {
            return OscarWaveformStatistics.fromBrp(brp, percentile);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private OscarProperties.Analysis resolveAnalysis() {
        OscarProperties.Analysis analysis = oscarRepository.properties().analysis();
        return analysis != null ? analysis : new OscarProperties.Analysis(95, 120, 20, 100, 5);
    }
}
