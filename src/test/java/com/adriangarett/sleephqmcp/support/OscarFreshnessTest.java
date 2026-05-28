package com.adriangarett.sleephqmcp.support;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class OscarFreshnessTest {

    @ParameterizedTest
    @CsvSource({
            "0, fresh",
            "6, acceptable_lag",
            "7, stale",
            "29, stale",
            "30, very_stale"
    })
    void categoryFromExportLagDays_mapsBoundaries(long lagDays, String expected) {
        assertThat(OscarFreshness.categoryFromExportLagDays(lagDays)).isEqualTo(expected);
    }
}
