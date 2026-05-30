package com.adriangarett.sleephqmcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Local SleepHQ mirror produced by {@code ezscript/sleephq_download.py}. {@code dataPath} is the
 * RESMED_DATA root (raw SD layout: {@code DATALOG/<YYYYMMDD>/...}, {@code STR.edf}); {@code o2Path} is
 * the flat SLEEPHQ_O2_RING directory of Viatom binaries. Blank => local source disabled (API only).
 */
@ConfigurationProperties(prefix = "sleephq.local")
public record SleepHqLocalProperties(String dataPath, String o2Path) {}
