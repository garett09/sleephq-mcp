package com.adriangarett.sleephqmcp.oscar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class OscarSqliteDb implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OscarSqliteDb.class);

    private final Connection conn;

    private OscarSqliteDb(Connection conn) {
        this.conn = conn;
    }

    /** Opens a read-only connection to dbFile. */
    public static Optional<OscarSqliteDb> open(Path dbFile) {
        String url = "jdbc:sqlite:file:" + dbFile.toAbsolutePath() + "?mode=ro";
        try {
            Connection c = DriverManager.getConnection(url);
            return Optional.of(new OscarSqliteDb(c));
        } catch (SQLException e) {
            log.warn("Cannot open OSCAR db {}: {}", dbFile, e.getMessage());
            return Optional.empty();
        }
    }

    /** Wraps an existing connection — used in tests with in-memory DBs. */
    public static OscarSqliteDb wrap(Connection conn) {
        return new OscarSqliteDb(conn);
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        List<T> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
            }
        } catch (SQLException e) {
            log.warn("OSCAR query failed [{}]: {}", sql.substring(0, Math.min(60, sql.length())), e.getMessage());
            return List.of();
        }
        return results;
    }

    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        return query(sql, mapper, params).stream().findFirst();
    }

    @Override
    public void close() {
        try {
            if (!conn.isClosed()) conn.close();
        } catch (SQLException e) {
            log.warn("Failed to close OSCAR db connection: {}", e.getMessage());
        }
    }
}
