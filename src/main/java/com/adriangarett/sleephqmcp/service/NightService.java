package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.support.JournalOverlaySupport;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Per-night data. Journal wellness is merged on each read by calendar date.
 */
@Service
public class NightService {

    private final SleepHqClient client;
    private final JournalLookupService journalLookup;

    public NightService(SleepHqClient client, JournalLookupService journalLookup) {
        this.client = client;
        this.journalLookup = journalLookup;
    }

    public String getNightStats(String machineDateId) {
        return enrichWithJournal(client.getMachineDate(machineDateId));
    }

    private String enrichWithJournal(String envelopeJson) {
        JsonNode envelope = JsonApi.parse(envelopeJson);
        String date = JournalOverlaySupport.resolveCalendarDate(envelope);
        if (date == null) {
            return envelopeJson;
        }
        Optional<JsonNode> journal = journalLookup.findAttributesByDate(null, date);
        if (journal.isEmpty()) {
            return envelopeJson;
        }
        return JournalOverlaySupport.enrichEnvelopeJson(envelopeJson, journal.get());
    }
}
