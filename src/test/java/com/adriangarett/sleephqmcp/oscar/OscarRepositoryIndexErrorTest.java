package com.adriangarett.sleephqmcp.oscar;

import com.adriangarett.sleephqmcp.config.OscarProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TODO(Task 11): Binary Summaries.xml.gz behavior removed in OSCAR 2.0 SQLite migration.
 * Tests replaced with stubs to keep the build green; will be deleted in Task 11.
 */
class OscarRepositoryIndexErrorTest {

    @Test
    void stubTest_repositoryHasNoSummariesIndex() {
        OscarProperties props = new OscarProperties(false, "", "", "", null);
        OscarRepository repo = new OscarRepository(props);
        assertThat(repo.getLastIndexError()).isNull();
        assertThat(repo.isReachable()).isFalse();
    }
}
