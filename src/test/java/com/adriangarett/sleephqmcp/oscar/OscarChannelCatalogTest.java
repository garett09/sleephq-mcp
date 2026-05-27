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
}
