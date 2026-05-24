package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.domain.WaveformChannel;
import com.adriangarett.sleephqmcp.domain.WaveformResponse;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;

/**
 * Fetches multiple waveform channels for the same clock window in one JSON payload (fewer MCP round-trips).
 */
@Service
public class CorrelationWaveformService {

    private static final ObjectMapper MAPPER = JsonApi.mapper();

    private final WaveformService waveformService;

    public CorrelationWaveformService(WaveformService waveformService) {
        this.waveformService = waveformService;
    }

    public String fetchWindow(String machineDateId, LocalTime from, LocalTime to, List<WaveformChannel> channels) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("machine_date_id", machineDateId);
        root.put("from", from.toString());
        root.put("to", to.toString());
        ObjectNode channelsNode = root.putObject("channels");
        for (WaveformChannel channel : channels) {
            WaveformResponse response = waveformService.fetch(channel, machineDateId, from, to);
            try {
                channelsNode.set(channel.name().toLowerCase(), MAPPER.readTree(response.toJson()));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Correlation window: invalid JSON for channel " + channel.name(), e);
            }
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Correlation window: serialization failed", e);
        }
    }
}
