package com.adriangarett.sleephqmcp.oscar;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class OscarSqliteDbTest {

    @Test
    void queriesInMemoryFixture() throws Exception {
        try (var conn = OscarSqliteFixture.createInMemory();
             var db = OscarSqliteDb.wrap(conn)) {
            List<String> dates = db.query(
                "SELECT date FROM daily_summaries WHERE profile_id=1",
                rs -> rs.getString("date"));
            assertThat(dates).containsExactly("2024-01-15");
        }
    }

    @Test
    void queryOneReturnsEmpty() throws Exception {
        try (var conn = OscarSqliteFixture.createInMemory();
             var db = OscarSqliteDb.wrap(conn)) {
            var result = db.queryOne(
                "SELECT date FROM daily_summaries WHERE date='9999-01-01'",
                rs -> rs.getString("date"));
            assertThat(result).isEmpty();
        }
    }

    @Test
    void queryWithParams() throws Exception {
        try (var conn = OscarSqliteFixture.createInMemory();
             var db = OscarSqliteDb.wrap(conn)) {
            List<Long> counts = db.query(
                "SELECT COUNT(*) FROM sessions WHERE enabled=?",
                rs -> rs.getLong(1), 1);
            assertThat(counts.get(0)).isEqualTo(2L);
        }
    }
}
