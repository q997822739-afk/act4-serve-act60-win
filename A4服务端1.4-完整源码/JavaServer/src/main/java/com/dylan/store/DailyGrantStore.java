package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public final class DailyGrantStore extends DomainStore {

    DailyGrantStore(DataSource dataSource) {
        super(dataSource);
    }

    public Optional<Instant> lastDailyGrant(int characNo, int itemId) {
        String sql = "SELECT granted_at FROM charac_daily_grant"
                + " WHERE charac_no = ? AND item_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            ps.setInt(2, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(Instant.ofEpochMilli(rs.getLong(1)))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("读每日发放记录失败：" + e.getMessage(), e);
        }
    }

    public void saveDailyGrant(int characNo, int itemId, Instant periodStart) {
        String sql = "INSERT INTO charac_daily_grant(charac_no, item_id, granted_at)"
                + " VALUES(?, ?, ?)"
                + " ON CONFLICT(charac_no, item_id) DO UPDATE SET granted_at = excluded.granted_at";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            ps.setInt(2, itemId);
            ps.setLong(3, periodStart.toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("写每日发放记录失败：" + e.getMessage(), e);
        }
    }
}
