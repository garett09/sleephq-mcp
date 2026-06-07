package com.adriangarett.sleephqmcp.oscar;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Creates an in-memory SQLite DB matching the OSCAR 2.0 schema with synthetic data.
 * Profile 1, machine 1, two sessions on 2024-01-15.
 * RespRate: avg=16.0, p95=20.0, gain=0.2, histogram: raw 80→50 samples, 100→50 samples.
 * Events: 2 ClearAirway + 1 Hypopnea.
 * daily_summaries: ahi=0.5, pressure_avg=10.6, leak_total_avg=2.0.
 */
public final class OscarSqliteFixture {

    private OscarSqliteFixture() {}

    public static Connection createInMemory() throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = conn.createStatement()) {
            createSchema(st);
            insertData(st);
        }
        return conn;
    }

    private static void createSchema(Statement st) throws Exception {
        st.executeUpdate("""
            CREATE TABLE profiles (
                id INTEGER PRIMARY KEY, name TEXT NOT NULL
            )""");
        st.executeUpdate("""
            CREATE TABLE machines (
                id INTEGER PRIMARY KEY,
                profile_id INTEGER NOT NULL,
                machine_id INTEGER NOT NULL,
                loader_name TEXT,
                machine_type INTEGER DEFAULT 0,
                serial_number TEXT,
                model TEXT
            )""");
        st.executeUpdate("""
            CREATE TABLE sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                machine_id INTEGER NOT NULL,
                start_time INTEGER NOT NULL,
                end_time INTEGER NOT NULL,
                duration INTEGER NOT NULL,
                enabled INTEGER DEFAULT 1,
                summary_only INTEGER DEFAULT 0,
                no_settings INTEGER DEFAULT 0,
                events_loaded INTEGER DEFAULT 0
            )""");
        st.executeUpdate("""
            CREATE TABLE channels (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER NOT NULL,
                channel_id INTEGER NOT NULL,
                channel_code TEXT NOT NULL,
                type INTEGER,
                enabled INTEGER DEFAULT 1,
                label TEXT,
                UNIQUE(profile_id, channel_id)
            )""");
        st.executeUpdate("""
            CREATE TABLE session_channels (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                profile_id INTEGER NOT NULL,
                channel_id INTEGER NOT NULL,
                count INTEGER DEFAULT 0,
                sum REAL DEFAULT 0,
                avg REAL DEFAULT 0,
                wavg REAL DEFAULT 0,
                min REAL DEFAULT 0,
                max REAL DEFAULT 0,
                median REAL DEFAULT 0,
                p90 REAL DEFAULT 0,
                p95 REAL DEFAULT 0,
                phys_min REAL DEFAULT 0,
                phys_max REAL DEFAULT 0,
                cph REAL DEFAULT 0,
                sph REAL DEFAULT 0,
                gain REAL DEFAULT 1.0,
                UNIQUE(session_id, channel_id)
            )""");
        st.executeUpdate("""
            CREATE TABLE session_channel_values (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_channel_id INTEGER NOT NULL,
                value INTEGER NOT NULL,
                count INTEGER DEFAULT 0,
                time_ms INTEGER DEFAULT 0,
                UNIQUE(session_channel_id, value)
            )""");
        st.executeUpdate("""
            CREATE TABLE respiratory_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                profile_id INTEGER NOT NULL,
                channel_id INTEGER,
                event_type INTEGER NOT NULL,
                start_time INTEGER NOT NULL,
                end_time INTEGER NOT NULL,
                duration INTEGER NOT NULL
            )""");
        st.executeUpdate("""
            CREATE TABLE daily_summaries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                profile_id INTEGER NOT NULL,
                date TEXT NOT NULL,
                session_count INTEGER DEFAULT 0,
                enabled_session_count INTEGER DEFAULT 0,
                total_hours REAL DEFAULT 0,
                mask_on_hours REAL DEFAULT 0,
                ahi REAL DEFAULT 0,
                rdi REAL DEFAULT 0,
                obstructive_count INTEGER DEFAULT 0,
                unclassified_count INTEGER DEFAULT 0,
                hypopnea_count INTEGER DEFAULT 0,
                rera_count INTEGER DEFAULT 0,
                clear_airway_count INTEGER DEFAULT 0,
                pressure_avg REAL,
                pressure_min REAL,
                pressure_max REAL,
                pressure_95th REAL,
                leak_total_avg REAL,
                leak_total_95th REAL,
                leak_total_max REAL,
                leak_unintentional_avg REAL,
                spo2_avg REAL,
                spo2_min REAL,
                pulse_avg REAL,
                pulse_min REAL,
                pulse_max REAL,
                is_compliant INTEGER DEFAULT 0,
                has_oximetry INTEGER DEFAULT 0,
                UNIQUE(profile_id, date)
            )""");
        st.executeUpdate("""
            CREATE TABLE session_settings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                profile_id INTEGER NOT NULL,
                channel_id INTEGER NOT NULL,
                value REAL NOT NULL,
                data_type TEXT,
                UNIQUE(session_id, channel_id)
            )""");
        st.executeUpdate("""
            CREATE TABLE schema_version (
                version INTEGER PRIMARY KEY,
                applied_at TEXT DEFAULT CURRENT_TIMESTAMP
            )""");
    }

    private static void insertData(Statement st) throws Exception {
        // 2024-01-15: two sessions
        long s1Start = 1705278600000L; // 2024-01-15 00:30 UTC
        long s1End   = 1705296000000L; // 2024-01-15 05:00 UTC
        long s2Start = 1705296300000L; // 2024-01-15 05:05 UTC
        long s2End   = 1705307400000L; // 2024-01-15 08:10 UTC

        st.executeUpdate("INSERT INTO profiles(id,name) VALUES(1,'TestProfile')");
        st.executeUpdate("INSERT INTO machines(id,profile_id,machine_id,loader_name,serial_number,model) VALUES(1,1,1001,'ResMed','SN123','AirSense 10')");
        st.executeUpdate("INSERT INTO schema_version(version) VALUES(17)");

        st.executeUpdate("INSERT INTO sessions(id,session_id,machine_id,start_time,end_time,duration,enabled) VALUES(1,1001,1," + s1Start + "," + s1End + "," + (s1End-s1Start)/1000 + ",1)");
        st.executeUpdate("INSERT INTO sessions(id,session_id,machine_id,start_time,end_time,duration,enabled) VALUES(2,1002,1," + s2Start + "," + s2End + "," + (s2End-s2Start)/1000 + ",1)");

        // Channels
        st.executeUpdate("INSERT INTO channels(profile_id,channel_id,channel_code,label) VALUES(1,4353,'RespRate','Resp. Rate')");
        st.executeUpdate("INSERT INTO channels(profile_id,channel_id,channel_code,label) VALUES(1,4364,'Pressure','Pressure')");
        st.executeUpdate("INSERT INTO channels(profile_id,channel_id,channel_code,label) VALUES(1,4372,'Leak','Leak Rate')");
        st.executeUpdate("INSERT INTO channels(profile_id,channel_id,channel_code,label) VALUES(1,4374,'AHI','AHI')");
        st.executeUpdate("INSERT INTO channels(profile_id,channel_id,channel_code,label) VALUES(1,4097,'ClearAirway','CA')");
        st.executeUpdate("INSERT INTO channels(profile_id,channel_id,channel_code,label) VALUES(1,4101,'Hypopnea','H')");

        // session_channels for session 1: RespRate gain=0.2, histogram 80→50, 100→50
        st.executeUpdate("INSERT INTO session_channels(id,session_id,profile_id,channel_id,count,avg,min,max,p95,gain) VALUES(1,1,1,4353,100,16.0,10.0,22.0,20.0,0.2)");
        st.executeUpdate("INSERT INTO session_channel_values(session_channel_id,value,count) VALUES(1,80,50)");
        st.executeUpdate("INSERT INTO session_channel_values(session_channel_id,value,count) VALUES(1,100,50)");

        // session_channels for session 2: RespRate
        st.executeUpdate("INSERT INTO session_channels(id,session_id,profile_id,channel_id,count,avg,min,max,p95,gain) VALUES(2,2,1,4353,60,17.0,12.0,24.0,22.0,0.2)");
        st.executeUpdate("INSERT INTO session_channel_values(session_channel_id,value,count) VALUES(2,85,30)");
        st.executeUpdate("INSERT INTO session_channel_values(session_channel_id,value,count) VALUES(2,110,30)");

        // Pressure session 1
        st.executeUpdate("INSERT INTO session_channels(id,session_id,profile_id,channel_id,count,avg,min,max,p95,gain) VALUES(3,1,1,4364,100,10.6,10.6,10.6,10.6,1.0)");

        // Respiratory events: 2 ClearAirway + 1 Hypopnea
        long evBase = s1Start + 3600000L;
        st.executeUpdate("INSERT INTO respiratory_events(session_id,profile_id,channel_id,event_type,start_time,end_time,duration) VALUES(1,1,4097,1," + evBase + "," + (evBase+30000) + ",30)");
        st.executeUpdate("INSERT INTO respiratory_events(session_id,profile_id,channel_id,event_type,start_time,end_time,duration) VALUES(1,1,4097,1," + (evBase+60000) + "," + (evBase+100000) + ",40)");
        st.executeUpdate("INSERT INTO respiratory_events(session_id,profile_id,channel_id,event_type,start_time,end_time,duration) VALUES(1,1,4101,1," + (evBase+120000) + "," + (evBase+150000) + ",30)");

        // daily_summaries
        st.executeUpdate("""
            INSERT INTO daily_summaries(profile_id,date,session_count,enabled_session_count,
                total_hours,mask_on_hours,ahi,rdi,obstructive_count,hypopnea_count,
                clear_airway_count,pressure_avg,pressure_95th,leak_total_avg,leak_total_95th)
            VALUES(1,'2024-01-15',2,2,7.5,7.5,0.5,0.5,0,1,2,10.6,10.6,2.0,3.0)
            """);

        // session_settings
        st.executeUpdate("INSERT INTO session_settings(session_id,profile_id,channel_id,value) VALUES(1,1,4364,10.6)");
        st.executeUpdate("INSERT INTO session_settings(session_id,profile_id,channel_id,value) VALUES(1,1,57857,0.0)");
    }
}
