package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class EventStore extends DomainStore {

    EventStore(DataSource dataSource) {
        super(dataSource);
    }

    public List<EventLogRow> loadActiveEvents(int serverId) {
        String sql = """
                SELECT log_id, occ_time, event_type, parameter1, parameter2, server_id,
                       start_time, end_time, m_id, expl
                  FROM dnf_event_log
                 WHERE server_id IN (0, ?)
                   AND start_time <= CAST(strftime('%s','now') AS INTEGER)
                   AND (end_time = 0 OR end_time > CAST(strftime('%s','now') AS INTEGER))
                 ORDER BY start_time
                """;
        List<EventLogRow> out = new ArrayList<>();
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(readEventLog(rs));
                }
            }
        } catch (SQLException e) {
            throw new StoreException("读 dnf_event_log 失败：" + e.getMessage(), e);
        }
        return out;
    }

    public long startEvent(int eventType, int parameter1, int parameter2,
                           int serverId, long mId, String expl) {
        long now = Instant.now().getEpochSecond();
        try (Connection c = connection()) {
            closeOpenEventRows(c, eventType, serverId, now);
            String sql = """
                    INSERT INTO dnf_event_log
                        (occ_time, event_type, parameter1, parameter2, server_id,
                         event_flag, start_time, end_time, m_id, expl, etc)
                    VALUES (?, ?, ?, ?, ?, 1, ?, 0, ?, ?, '')
                    """;
            try (PreparedStatement ps = c.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, now);
                ps.setInt(2, eventType);
                ps.setInt(3, parameter1);
                ps.setInt(4, parameter2);
                ps.setInt(5, serverId);
                ps.setLong(6, now);
                ps.setLong(7, mId);
                ps.setString(8, fit(expl, 200));
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    return keys.next() ? keys.getLong(1) : 0L;
                }
            }
        } catch (SQLException e) {
            throw new StoreException("写 dnf_event_log（开活动 " + eventType + "）失败："
                    + e.getMessage(), e);
        }
    }

    public int endEvent(int eventType, int serverId) {
        long now = Instant.now().getEpochSecond();
        try (Connection c = connection()) {
            return closeOpenEventRows(c, eventType, serverId, now);
        } catch (SQLException e) {
            throw new StoreException("关活动 " + eventType + " 失败：" + e.getMessage(), e);
        }
    }

    private static int closeOpenEventRows(Connection c, int eventType, int serverId, long now)
            throws SQLException {
        String sql = """
                UPDATE dnf_event_log SET end_time = ?
                 WHERE event_type = ? AND server_id IN (0, ?) AND end_time = 0
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, now);
            ps.setInt(2, eventType);
            ps.setInt(3, serverId);
            return ps.executeUpdate();
        }
    }

    private static EventLogRow readEventLog(ResultSet rs) throws SQLException {
        return new EventLogRow(
                rs.getLong("log_id"), rs.getLong("occ_time"), rs.getInt("event_type"),
                rs.getInt("parameter1"), rs.getInt("parameter2"), rs.getInt("server_id"),
                rs.getLong("start_time"), rs.getLong("end_time"), rs.getLong("m_id"),
                rs.getString("expl"));
    }

    public void updateEventInfoClientTexts(Map<Integer, String> texts) {
        String sql = "UPDATE dnf_event_info SET client_text = ? WHERE event_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (Map.Entry<Integer, String> e : texts.entrySet()) {
                ps.setString(1, fit(e.getValue(), 60));
                ps.setInt(2, e.getKey());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new StoreException("写 dnf_event_info.client_text 失败：" + e.getMessage(), e);
        }
    }

    static void seedEventInfo(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM dnf_event_info")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        }
        String sql = """
                INSERT INTO dnf_event_info
                    (event_id, event_name, event_explain, apply_type,
                     start_date, end_date, client_text)
                VALUES (?, ?, ?, ?, '1970-01-01', '1970-01-01', '')
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (EventSchema.EventInfoRow row : EventSchema.EVENT_INFO_SEED) {
                ps.setInt(1, row.eventId());
                ps.setString(2, row.name());
                ps.setString(3, fit(row.explain(), 100));
                ps.setInt(4, row.applyType());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
