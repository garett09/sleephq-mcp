package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.SleepHqPathParams;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Indexes team journals by calendar {@code attributes.date} via paged {@code list-journals}.
 */
@Service
public class JournalLookupService {

    private static final Logger log = LoggerFactory.getLogger(JournalLookupService.class);

    static final int MAX_PAGES = 5;
    static final int PER_PAGE = 100;

    private final SleepHqClient client;
    private final ClinicalContextProperties clinical;
    private final SleepHqCacheFacade cacheFacade;

    public JournalLookupService(SleepHqClient client, ClinicalContextProperties clinical,
                                SleepHqCacheFacade cacheFacade) {
        this.client = client;
        this.clinical = clinical;
        this.cacheFacade = cacheFacade;
    }

    public String requireTeamId(String teamIdOverride) {
        if (teamIdOverride != null && !teamIdOverride.isBlank()) {
            return SleepHqPathParams.requireResourceId(teamIdOverride, "teamId");
        }
        String configured = clinical.defaultTeamId();
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException("teamId is required, or set SLEEPHQ_TEAM_ID");
        }
        return SleepHqPathParams.requireResourceId(configured, "SLEEPHQ_TEAM_ID");
    }

    public Optional<JsonNode> findAttributesByDate(String teamId, String calendarDate) {
        String date = SleepHqPathParams.requireCalendarDate(calendarDate, "date");
        String resolvedTeamId = requireTeamId(teamId);
        JsonNode cached = cacheFacade.getJournalAttributesByDate(resolvedTeamId, date,
                () -> loadByDateRange(teamId, LocalDate.parse(date), LocalDate.parse(date)).get(date));
        return Optional.ofNullable(cached);
    }

    public Map<String, JsonNode> loadByDateRange(String teamIdOverride, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("fromDate must be on or before toDate");
        }
        String teamId = requireTeamId(teamIdOverride);
        Map<String, JsonNode> byDate = new HashMap<>();

        int page = 1;
        while (page <= MAX_PAGES) {
            String raw = client.listJournals(teamId, page, PER_PAGE);
            JsonNode data = JsonApi.parse(raw).path("data");
            if (!data.isArray() || data.isEmpty()) {
                break;
            }
            for (JsonNode item : data) {
                indexJournalItem(byDate, item, from, to);
            }
            if (data.size() < PER_PAGE) {
                break;
            }
            page++;
        }
        return Collections.unmodifiableMap(byDate);
    }

    private static void indexJournalItem(Map<String, JsonNode> byDate, JsonNode item, LocalDate from, LocalDate to) {
        JsonNode attrs = item.path("attributes");
        if (!attrs.isObject()) {
            return;
        }
        String dateStr = attrs.path("date").asText(null);
        if (dateStr == null || dateStr.isBlank()) {
            return;
        }
        LocalDate journalDate;
        try {
            journalDate = LocalDate.parse(dateStr);
        } catch (Exception e) {
            log.debug("Skipping journal with unparseable date: {}", dateStr);
            return;
        }
        if (journalDate.isBefore(from) || journalDate.isAfter(to)) {
            return;
        }
        if (byDate.containsKey(dateStr)) {
            log.debug("Duplicate journal for date={}; keeping first entry", dateStr);
            return;
        }
        byDate.put(dateStr, attrs.deepCopy());
    }
}
