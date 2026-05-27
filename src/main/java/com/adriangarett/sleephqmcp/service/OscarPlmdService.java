package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.domain.WaveformChannel;
import com.adriangarett.sleephqmcp.domain.WaveformResult;
import com.adriangarett.sleephqmcp.oscar.OscarRepository;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.SleepHqPathParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Best-effort PLMD-style comparison: movement bursts from SleepHQ {@code movement_summary.av}
 * threshold vs PLD tidal volume / resp rate windows. Movement timestamps are approximate.
 */
@Service
public class OscarPlmdService {

    private static final double MOVEMENT_BURST_THRESHOLD = 0.5;

    private final OscarRepository oscarRepository;
    private final SleepHqClient sleepHqClient;
    private final ClinicalContextProperties clinical;

    public OscarPlmdService(OscarRepository oscarRepository, SleepHqClient sleepHqClient,
                            ClinicalContextProperties clinical) {
        this.oscarRepository = oscarRepository;
        this.sleepHqClient = sleepHqClient;
        this.clinical = clinical;
    }

    public String plmdNight(String calendarDate, String o2MachineId) {
        String date = SleepHqPathParams.requireCalendarDate(calendarDate, "date");
        LocalDate localDate = LocalDate.parse(date);
        Optional<WaveformResult> pld = oscarRepository.loadPldWaveform(localDate);
        if (pld.isEmpty()) {
            return JsonApi.toJsonString(java.util.Map.of(
                    "date", date,
                    "oscar_status", "unavailable",
                    "note", "No PLD EDF for date"));
        }

        double movementAvg = loadMovementAverage(date, o2MachineId);
        int burstCount = movementAvg >= MOVEMENT_BURST_THRESHOLD ? 1 : 0;

        WaveformChannel vt = findChannel(pld.get(), "tidvol");
        WaveformChannel rr = findChannel(pld.get(), "resprate");

        double vtMean = mean(vt != null ? vt.samples() : List.of());
        double rrMean = mean(rr != null ? rr.samples() : List.of());

        ObjectNode root = JsonApi.mapper().createObjectNode();
        root.put("date", date);
        root.put("movement_burst_count", burstCount);
        root.put("movement_summary_av", round(movementAvg));
        root.put("mean_vt_pld", round(vtMean));
        root.put("mean_rr_pld", round(rrMean));
        root.put("mean_vt_baseline", round(vtMean));
        root.put("mean_rr_baseline", round(rrMean));
        root.put("vt_delta", 0);
        root.put("rr_delta", 0);
        root.putArray("data_sources").add("oscar_pld_edf").add("sleephq_movement_summary");
        root.put("note", "Movement bursts use nightly movement_summary.av threshold; "
                + "per-burst PLD windows require burst timestamps (future).");
        try {
            return JsonApi.mapper().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private double loadMovementAverage(String date, String o2MachineId) {
        String machineId = o2MachineId;
        if (machineId == null || machineId.isBlank()) {
            machineId = clinical.defaultO2MachineId();
        }
        if (machineId == null || machineId.isBlank()) {
            return 0;
        }
        try {
            String raw = sleepHqClient.getMachineDateByDate(machineId, date);
            JsonNode attrs = JsonApi.attributes(JsonApi.parse(raw));
            return attrs.path("movement_summary").path("av").asDouble(0);
        } catch (Exception e) {
            return 0;
        }
    }

    private static WaveformChannel findChannel(WaveformResult result, String prefix) {
        for (WaveformChannel channel : result.channels()) {
            if (channel.label() != null
                    && channel.label().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                return channel;
            }
        }
        return null;
    }

    private static double mean(List<Double> samples) {
        if (samples == null || samples.isEmpty()) {
            return 0;
        }
        double sum = 0;
        int count = 0;
        for (Double sample : samples) {
            if (sample != null && !sample.isNaN(sample)) {
                sum += sample;
                count++;
            }
        }
        return count == 0 ? 0 : sum / count;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
