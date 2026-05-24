package com.adriangarett.sleephqmcp.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin wrapper over the SleepHQ REST API. Cross-cutting auth + retry lives in {@code AuthInterceptor};
 * this class is only path composition and JSON pass-through.
 */
@Component
public class SleepHqClient {

    private final RestClient restClient;

    public SleepHqClient(@Qualifier("sleepHqRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    // --- documented (v1 swagger.json) ---

    public String getMe() {
        return get("/api/v1/me");
    }

    public String listTeams(Integer page, Integer perPage) {
        return get("/api/v1/teams", pageParams(page, perPage));
    }

    public String listMachines(String teamId, Integer page, Integer perPage) {
        return get("/api/v1/teams/" + teamId + "/machines", pageParams(page, perPage));
    }

    public String getMachine(String machineId) {
        return get("/api/v1/machines/" + machineId);
    }

    public String listMachineDates(String machineId, String sortOrder, Integer page, Integer perPage) {
        Map<String, String> q = pageParams(page, perPage);
        if (sortOrder != null && !sortOrder.isBlank()) q.put("sort_order", sortOrder);
        return get("/api/v1/machines/" + machineId + "/machine_dates", q);
    }

    public String getMachineDateByDate(String machineId, String date) {
        return get("/api/v1/machines/" + machineId + "/machine_dates/" + date);
    }

    public String getMachineDate(String machineDateId) {
        return get("/api/v1/machine_dates/" + machineDateId);
    }

    public String listMasks(String teamId, Integer page, Integer perPage) {
        return get("/api/v1/teams/" + teamId + "/masks", pageParams(page, perPage));
    }

    public String listPatients(String teamId, Integer page, Integer perPage) {
        return get("/api/v1/teams/" + teamId + "/patients", pageParams(page, perPage));
    }

    public String listSleepTests(String teamId, String bucket, Integer page, Integer perPage) {
        Map<String, String> q = pageParams(page, perPage);
        if (bucket != null && !bucket.isBlank()) q.put("bucket", bucket);
        return get("/api/v1/teams/" + teamId + "/sleep_tests", q);
    }

    public String listJournals(String teamId, Integer page, Integer perPage) {
        return get("/api/v1/teams/" + teamId + "/journals", pageParams(page, perPage));
    }

    public String listDevices() {
        return get("/api/v1/devices");
    }

    // --- undocumented but live (probed) ---

    public String getNightWaveform(String machineDateId, String channelPathSegment) {
        return get("/api/v1/machine_dates/" + machineDateId + "/" + channelPathSegment);
    }

    public String getNightSessions(String machineDateId) {
        return get("/api/v1/machine_dates/" + machineDateId + "/sessions");
    }

    public String getNightEvents(String machineDateId) {
        return get("/api/v1/machine_dates/" + machineDateId + "/events_data");
    }

    public String getComparison(String machineId, String fromDate, String toDate) {
        Map<String, String> q = new LinkedHashMap<>();
        if (machineId != null && !machineId.isBlank()) q.put("machine_id", machineId);
        if (fromDate != null && !fromDate.isBlank()) q.put("from", fromDate);
        if (toDate != null && !toDate.isBlank()) q.put("to", toDate);
        return get("/api/v1/comparisons", q);
    }

    public String getShareDashboard(String shareLinkToken) {
        return get("/api/v1/share_links/" + shareLinkToken + "/dashboard");
    }

    // --- helpers ---

    private String get(String path) {
        return get(path, Map.of());
    }

    private String get(String path, Map<String, String> queryParams) {
        URI uri = buildUri(path, queryParams);
        return restClient.get()
                .uri(uri)
                .retrieve()
                .body(String.class);
    }

    private URI buildUri(String path, Map<String, String> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        queryParams.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                builder.queryParam(key, value);
            }
        });
        return builder.build().toUri();
    }

    private static Map<String, String> pageParams(Integer page, Integer perPage) {
        Map<String, String> q = new LinkedHashMap<>();
        if (page != null) q.put("page", page.toString());
        if (perPage != null) q.put("per_page", perPage.toString());
        return q;
    }
}
