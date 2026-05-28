package com.adriangarett.sleephqmcp.support;

import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.domain.ApneaEvent;
import com.adriangarett.sleephqmcp.domain.ApneaScanResult;
import com.adriangarett.sleephqmcp.domain.DeviceEvent;
import com.adriangarett.sleephqmcp.domain.DeviceEventResult;
import com.adriangarett.sleephqmcp.domain.WaveformResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalInt;

/**
 * Applies a constant wall-clock correction to CPAP/EDF timestamps (ResMed clock drift).
 * O2 ring and journal clocks are never adjusted.
 */
public final class CpapClockAlignment {

    public static final String REFERENCE_CLOCK = "o2_and_journal";
    public static final String SOURCE_TOOL_OVERRIDE = "tool_override";
    public static final String SOURCE_SLEEPHQ_MACHINE_DATE = "sleephq_machine_date";
    public static final String SOURCE_ENV = "env";
    public static final String SOURCE_NONE = "none";

    /** Reject swagger placeholder / corrupt values; drift is expected to be well under one day. */
    public static final int MAX_REASONABLE_OFFSET_SECONDS = 86_400;

    private CpapClockAlignment() {
    }

    public record CpapClockAdjustResolution(int adjustSeconds, String source) {
    }

    public static OptionalInt parseMachineDateTimeOffset(JsonNode machineDateAttributes) {
        if (machineDateAttributes == null || machineDateAttributes.isMissingNode()) {
            return OptionalInt.empty();
        }
        JsonNode node = machineDateAttributes.get("time_offset");
        if (node == null || node.isNull() || !node.isNumber()) {
            return OptionalInt.empty();
        }
        long raw = node.asLong();
        if (raw == 0 || Math.abs(raw) > MAX_REASONABLE_OFFSET_SECONDS) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) raw);
    }

    public static CpapClockAdjustResolution resolveAdjust(ClinicalContextProperties clinical, Integer toolOverride,
                                                          OptionalInt machineDateOffset) {
        if (toolOverride != null) {
            if (toolOverride != 0 && Math.abs(toolOverride) > MAX_REASONABLE_OFFSET_SECONDS) {
                throw new IllegalArgumentException(
                        "cpapClockAdjustSeconds absolute value must not exceed "
                                + MAX_REASONABLE_OFFSET_SECONDS);
            }
            return new CpapClockAdjustResolution(toolOverride, SOURCE_TOOL_OVERRIDE);
        }
        if (machineDateOffset != null && machineDateOffset.isPresent() && machineDateOffset.getAsInt() != 0) {
            return new CpapClockAdjustResolution(machineDateOffset.getAsInt(), SOURCE_SLEEPHQ_MACHINE_DATE);
        }
        if (clinical != null && clinical.cpapClockAdjustSeconds() != null && clinical.cpapClockAdjustSeconds() != 0) {
            return new CpapClockAdjustResolution(clinical.cpapClockAdjustSeconds(), SOURCE_ENV);
        }
        return new CpapClockAdjustResolution(0, SOURCE_NONE);
    }

    /** @deprecated use {@link #resolveAdjust} */
    public static int resolveAdjustSeconds(ClinicalContextProperties clinical, Integer override) {
        return resolveAdjust(clinical, override, OptionalInt.empty()).adjustSeconds();
    }

    public static String shiftIso8601(String startDatetime, int adjustSeconds) {
        if (adjustSeconds == 0 || startDatetime == null || startDatetime.isBlank()) {
            return startDatetime;
        }
        LocalDateTime shifted = LocalDateTime.parse(startDatetime).plusSeconds(adjustSeconds);
        return shifted.toString();
    }

    public static WaveformResult alignWaveform(WaveformResult result, int adjustSeconds) {
        if (adjustSeconds == 0) {
            return result;
        }
        return new WaveformResult(
                result.filename(),
                shiftIso8601(result.startDatetime(), adjustSeconds),
                result.durationSeconds(),
                result.channels(),
                result.samplesDownsampled()
        );
    }

    public static ApneaScanResult alignApneaScan(ApneaScanResult result, int adjustSeconds) {
        if (adjustSeconds == 0) {
            return result;
        }
        List<ApneaEvent> events = result.events().stream()
                .map(e -> alignApneaEvent(e, adjustSeconds))
                .toList();
        return new ApneaScanResult(
                result.filename(),
                shiftIso8601(result.startDatetime(), adjustSeconds),
                result.durationSeconds(),
                result.channelScanned(),
                result.threshold(),
                events
        );
    }

    public static DeviceEventResult alignDeviceEvents(DeviceEventResult result, int adjustSeconds) {
        if (adjustSeconds == 0) {
            return result;
        }
        List<DeviceEvent> events = result.events().stream()
                .map(e -> alignDeviceEvent(e, adjustSeconds))
                .toList();
        return new DeviceEventResult(
                result.filename(),
                shiftIso8601(result.startDatetime(), adjustSeconds),
                result.durationSeconds(),
                result.source(),
                events
        );
    }

    private static ApneaEvent alignApneaEvent(ApneaEvent event, int adjustSeconds) {
        return new ApneaEvent(
                event.offset(),
                event.startSeconds(),
                event.durationSeconds(),
                shiftIso8601(event.timestamp(), adjustSeconds),
                event.classification()
        );
    }

    private static DeviceEvent alignDeviceEvent(DeviceEvent event, int adjustSeconds) {
        return new DeviceEvent(
                event.offset(),
                event.startSeconds(),
                event.durationSeconds(),
                shiftIso8601(event.timestamp(), adjustSeconds),
                event.label(),
                event.code()
        );
    }

    public static ObjectNode alignmentMeta(CpapClockAdjustResolution resolution) {
        ObjectNode meta = JsonApi.mapper().createObjectNode();
        meta.put("reference_clock", REFERENCE_CLOCK);
        meta.put("cpap_adjust_seconds", resolution.adjustSeconds());
        meta.put("source", resolution.source());
        meta.put("scope", "clock_drift_only");
        return meta;
    }

    public static String serializeWithAlignment(Object result, CpapClockAdjustResolution resolution) {
        try {
            ObjectNode root = JsonApi.mapper().valueToTree(result);
            if (resolution.adjustSeconds() != 0) {
                root.set("clock_alignment", alignmentMeta(resolution));
            }
            return JsonApi.mapper().writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize CPAP result", e);
        }
    }
}
