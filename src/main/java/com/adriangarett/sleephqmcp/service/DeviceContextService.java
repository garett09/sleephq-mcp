package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import com.adriangarett.sleephqmcp.config.ClinicalContextProperties;
import com.adriangarett.sleephqmcp.config.ClinicalDefaultsSupport;
import com.adriangarett.sleephqmcp.support.JsonApi;
import com.adriangarett.sleephqmcp.support.SleepHqPathParams;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Service;

/**
 * Full device context from SleepHQ only — menu settings, machines, registered masks.
 * Production deployments should not maintain a static device-current.md for pressure or mask type.
 */
@Service
public class DeviceContextService {

    private final SleepHqClient client;
    private final ClinicalContextProperties clinical;

    public DeviceContextService(SleepHqClient client, ClinicalContextProperties clinical) {
        this.client = client;
        this.clinical = clinical;
    }

    /**
     * @param machineId optional CPAP machine id; defaults to {@code SLEEPHQ_CPAP_MACHINE_ID}
     */
    public String deviceContextJson(String machineId) {
        String cpap = SleepHqPathParams.requireResourceId(
                firstNonBlank(machineId, clinical.defaultCpapMachineId()), "machineId");
        String therapyDate = resolveLatestTherapyDate(cpap);
        JsonNode nightAttrs = loadTherapyNightAttributes(cpap, therapyDate);
        JsonNode settings = nightAttrs.path("machine_settings");
        if (!settings.isObject() || settings.isEmpty()) {
            throw new IllegalStateException(
                    "No machine_settings on therapy night " + therapyDate + " for machine " + cpap);
        }

        ObjectNode root = JsonApi.mapper().createObjectNode();
        root.put("source", "sleephq-mcp/device_context");
        root.set("configured", JsonApi.mapper().valueToTree(ClinicalDefaultsSupport.configuredDefaultsBody(clinical)));
        root.put("therapy_date", therapyDate);
        root.put("cpap_machine_id", cpap);
        root.set("machine_settings", settings.deepCopy());
        attachMachine(root, "cpap_machine", cpap);
        attachOptionalMachine(root, "o2_machine", clinical.defaultO2MachineId());
        attachMasks(root);
        root.put("note",
                "Authoritative device context from SleepHQ (newest therapy night). With magic uploader, "
                        + "menu settings match last night's upload.");
        return serialize(root);
    }

    private JsonNode loadTherapyNightAttributes(String cpap, String therapyDate) {
        String nightJson = client.getMachineDateByDate(cpap, therapyDate);
        return JsonApi.attributes(JsonApi.parse(nightJson));
    }

    private void attachMachine(ObjectNode root, String field, String machineId) {
        root.set(field, JsonApi.parse(client.getMachine(machineId)).path("data"));
    }

    private void attachOptionalMachine(ObjectNode root, String field, String machineId) {
        if (machineId == null || machineId.isBlank()) {
            root.putNull(field);
            return;
        }
        try {
            root.set(field, JsonApi.parse(client.getMachine(machineId)).path("data"));
        } catch (RuntimeException e) {
            ObjectNode err = root.putObject(field);
            err.put("unavailable", true);
            err.put("machine_id", machineId);
            err.put("reason", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private void attachMasks(ObjectNode root) {
        String teamId = clinical.defaultTeamId();
        if (teamId == null || teamId.isBlank()) {
            root.putArray("registered_masks");
            root.put("masks_note", "SLEEPHQ_TEAM_ID not configured; skipped list-masks");
            return;
        }
        try {
            JsonNode collection = JsonApi.parse(client.listMasks(teamId, 1, 50)).path("data");
            if (collection.isArray()) {
                root.set("registered_masks", collection.deepCopy());
            } else {
                root.putArray("registered_masks");
            }
        } catch (RuntimeException e) {
            root.putArray("registered_masks");
            root.put("masks_note", "list-masks failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    private String resolveLatestTherapyDate(String cpapMachineId) {
        String listJson = client.listMachineDates(cpapMachineId, "desc", 1, 1);
        JsonNode data = JsonApi.parse(listJson).path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new IllegalStateException("No machine_dates for machine " + cpapMachineId);
        }
        String date = data.get(0).path("attributes").path("date").asText(null);
        if (date == null || date.isBlank()) {
            throw new IllegalStateException("Latest machine_date missing attributes.date");
        }
        SleepHqPathParams.requireCalendarDate(date, "date");
        return date;
    }

    private static String serialize(ObjectNode root) {
        try {
            return JsonApi.mapper().writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize device context", e);
        }
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        throw new IllegalArgumentException("machineId required and SLEEPHQ_CPAP_MACHINE_ID is not configured");
    }
}
