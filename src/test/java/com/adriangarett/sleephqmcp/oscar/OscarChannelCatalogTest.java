package com.adriangarett.sleephqmcp.oscar;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OscarChannelCatalogTest {

    @Test
    void fieldName_resMedBackupChannels_useStableNames() {
        assertThat(OscarChannelCatalog.fieldName(OscarChannelIds.CPAP_UsageFlag)).isEqualTo("usage_flag");
        assertThat(OscarChannelCatalog.fieldName(OscarChannelIds.CPAP_FlowRateHiRes)).isEqualTo("flow_rate_hi_res");
        assertThat(OscarChannelCatalog.fieldName(OscarChannelIds.CPAP_SessionMetric)).isEqualTo("session_metric");
    }

    @Test
    void fieldName_expiratoryTimeWave_0x110A() {
        assertThat(OscarChannelCatalog.fieldName(OscarChannelIds.CPAP_ExpiratoryTimeWave))
                .isEqualTo("expiratory_time_wave");
    }

    @Test
    void fieldName_inspiratoryTime_0x110B() {
        assertThat(OscarChannelCatalog.fieldName(OscarChannelIds.CPAP_InspiratoryTime))
                .isEqualTo("inspiratory_time");
    }

    @Test
    void unit_inspiratoryTime_isSeconds() {
        assertThat(OscarChannelCatalog.find(OscarChannelIds.CPAP_InspiratoryTime))
                .isPresent()
                .get()
                .extracting(OscarChannelCatalog.ChannelMeta::unit)
                .isEqualTo("s");
    }
}
