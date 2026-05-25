package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.client.SleepHqClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Per-night data. {@link #getNightStats(String)} is cached because the underlying machine_date
 * record is immutable once SleepHQ has imported the file — within a conversation the LLM
 * often re-asks the same night repeatedly.
 */
@Service
public class NightService {

    private final SleepHqClient client;

    public NightService(SleepHqClient client) {
        this.client = client;
    }

    @Cacheable(value = "nightStats", key = "#machineDateId")
    public String getNightStats(String machineDateId) {
        return client.getMachineDate(machineDateId);
    }
}
