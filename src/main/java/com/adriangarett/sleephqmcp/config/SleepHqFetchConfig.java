package com.adriangarett.sleephqmcp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableConfigurationProperties(SleepHqFetchProperties.class)
public class SleepHqFetchConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService sleepHqFetchExecutor(SleepHqFetchProperties properties) {
        int parallelism = properties.parallelism();
        if (parallelism < 1) {
            throw new IllegalStateException("sleephq.fetch.parallelism must be >= 1, got " + parallelism);
        }
        AtomicInteger threadIndex = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "sleephq-fetch-" + threadIndex.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(parallelism, factory);
    }
}
