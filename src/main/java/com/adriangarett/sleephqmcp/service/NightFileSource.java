package com.adriangarett.sleephqmcp.service;

import com.adriangarett.sleephqmcp.domain.NightSessionFile;

import java.util.List;

/** Strategy for where a night's session bytes come from (local mirror vs SleepHQ API). */
public interface NightFileSource {

    /** Short label for provenance, e.g. {@code "local"} or {@code "sleephq_api"}. */
    String label();

    /** Whether this source is usable in the current environment (configured / reachable). */
    boolean available();

    /** PLD session files for the night (CPAP), grouped by ResMed's DATALOG folder. */
    List<NightSessionFile> cpapSessions(String date);

    /** O2-ring session files for the night, grouped by noon-split. */
    List<NightSessionFile> o2Sessions(String date);
}
