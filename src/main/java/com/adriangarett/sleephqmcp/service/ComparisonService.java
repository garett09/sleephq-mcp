package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import org.springframework.stereotype.Service;

@Service
public class ComparisonService {

    private final SleepHqClient client;

    public ComparisonService(SleepHqClient client) {
        this.client = client;
    }

    public String compare(String machineId, String fromDate, String toDate) {
        return client.getComparison(machineId, fromDate, toDate);
    }

    public String shareDashboard(String shareLinkToken) {
        return client.getShareDashboard(shareLinkToken);
    }
}
