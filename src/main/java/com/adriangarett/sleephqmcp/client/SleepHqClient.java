package com.adriangarett.sleephqmcp.client;

import com.adriangarett.sleephqmcp.domain.WaveformChannel;
import com.adriangarett.sleephqmcp.support.SleepHqPathParams;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.util.function.Function;

/**
 * Thin wrapper over the SleepHQ REST API. Cross-cutting auth + retry lives in {@code AuthInterceptor};
 * this class is only path composition and JSON pass-through. Path variables use URI templates so values
 * are encoded as single segments; {@link SleepHqPathParams} rejects malformed identifiers up front.
 */
@Component
public class SleepHqClient {

    private final RestClient restClient;

    public SleepHqClient(@Qualifier("sleepHqRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    // --- documented (v1 swagger.json) ---

    public String getMe() {
        return get(uriBuilder -> uriBuilder.path("/api/v1/me").build());
    }

    public String listTeams(Integer page, Integer perPage) {
        return get(uriBuilder -> appendPageParams(uriBuilder.path("/api/v1/teams"), page, perPage).build());
    }

    public String listMachines(String teamId, Integer page, Integer perPage) {
        String id = SleepHqPathParams.requireResourceId(teamId, "teamId");
        return get(uriBuilder -> appendPageParams(uriBuilder.path("/api/v1/teams/{teamId}/machines"), page, perPage)
                .build(id));
    }

    public String getMachine(String machineId) {
        String id = SleepHqPathParams.requireResourceId(machineId, "machineId");
        return get(uriBuilder -> uriBuilder.path("/api/v1/machines/{machineId}").build(id));
    }

    public String listMachineDates(String machineId, String sortOrder, Integer page, Integer perPage) {
        String id = SleepHqPathParams.requireResourceId(machineId, "machineId");
        String sort = SleepHqPathParams.optionalQueryToken(sortOrder, "sortOrder");
        return get(uriBuilder -> {
            UriBuilder b = uriBuilder.path("/api/v1/machines/{machineId}/machine_dates");
            if (sort != null) {
                b.queryParam("sort_order", sort);
            }
            appendPageParams(b, page, perPage);
            return b.build(id);
        });
    }

    public String getMachineDateByDate(String machineId, String date) {
        String mid = SleepHqPathParams.requireResourceId(machineId, "machineId");
        String d = SleepHqPathParams.requireCalendarDate(date, "date");
        return get(uriBuilder -> uriBuilder
                .path("/api/v1/machines/{machineId}/machine_dates/{date}")
                .build(mid, d));
    }

    public String getMachineDate(String machineDateId) {
        String id = SleepHqPathParams.requireResourceId(machineDateId, "machineDateId");
        return get(uriBuilder -> uriBuilder.path("/api/v1/machine_dates/{machineDateId}").build(id));
    }

    public String listMasks(String teamId, Integer page, Integer perPage) {
        String id = SleepHqPathParams.requireResourceId(teamId, "teamId");
        return get(uriBuilder -> appendPageParams(uriBuilder.path("/api/v1/teams/{teamId}/masks"), page, perPage)
                .build(id));
    }

    public String listPatients(String teamId, Integer page, Integer perPage) {
        String id = SleepHqPathParams.requireResourceId(teamId, "teamId");
        return get(uriBuilder -> appendPageParams(uriBuilder.path("/api/v1/teams/{teamId}/patients"), page, perPage)
                .build(id));
    }

    public String listSleepTests(String teamId, String bucket, Integer page, Integer perPage) {
        String id = SleepHqPathParams.requireResourceId(teamId, "teamId");
        String b = SleepHqPathParams.optionalQueryToken(bucket, "bucket");
        return get(uriBuilder -> {
            UriBuilder ub = uriBuilder.path("/api/v1/teams/{teamId}/sleep_tests");
            if (b != null) {
                ub.queryParam("bucket", b);
            }
            appendPageParams(ub, page, perPage);
            return ub.build(id);
        });
    }

    public String listJournals(String teamId, Integer page, Integer perPage) {
        String id = SleepHqPathParams.requireResourceId(teamId, "teamId");
        return get(uriBuilder -> appendPageParams(uriBuilder.path("/api/v1/teams/{teamId}/journals"), page, perPage)
                .build(id));
    }

    public String listDevices() {
        return get(uriBuilder -> uriBuilder.path("/api/v1/devices").build());
    }

    // --- undocumented but live (probed) ---

    public String getNightWaveform(String machineDateId, String channelPathSegment) {
        String md = SleepHqPathParams.requireResourceId(machineDateId, "machineDateId");
        String seg = WaveformChannel.requireKnownPathSegment(channelPathSegment);
        return get(uriBuilder -> uriBuilder
                .path("/api/v1/machine_dates/{machineDateId}/{segment}")
                .build(md, seg));
    }

    public String getNightSessions(String machineDateId) {
        String id = SleepHqPathParams.requireResourceId(machineDateId, "machineDateId");
        return get(uriBuilder -> uriBuilder
                .path("/api/v1/machine_dates/{machineDateId}/sessions")
                .build(id));
    }

    public String getNightEvents(String machineDateId) {
        String id = SleepHqPathParams.requireResourceId(machineDateId, "machineDateId");
        return get(uriBuilder -> uriBuilder
                .path("/api/v1/machine_dates/{machineDateId}/events_data")
                .build(id));
    }

    public String getShareDashboard(String shareLinkToken) {
        String token = SleepHqPathParams.requireResourceId(shareLinkToken, "shareLinkToken");
        return get(uriBuilder -> uriBuilder
                .path("/api/v1/share_links/{token}/dashboard")
                .build(token));
    }

    // --- helpers ---

    private String get(Function<UriBuilder, java.net.URI> uriFunction) {
        return restClient.get()
                .uri(uriFunction)
                .retrieve()
                .body(String.class);
    }

    private static UriBuilder appendPageParams(UriBuilder builder, Integer page, Integer perPage) {
        if (page != null) {
            builder.queryParam("page", page);
        }
        if (perPage != null) {
            builder.queryParam("per_page", perPage);
        }
        return builder;
    }
}
