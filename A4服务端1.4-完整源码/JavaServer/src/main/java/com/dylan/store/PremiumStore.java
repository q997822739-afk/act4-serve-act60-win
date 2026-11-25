package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class PremiumStore extends DomainStore {
    PremiumStore(DataSource dataSource) {
        super(dataSource);
    }

    public record PremiumRow(int preType, long start, long end, int eventId, int serverId) {
    }

    public List<PremiumRow> premiumRows(long mId, int serverId) {
        String sql = """
                SELECT pre_type, CAST(strftime('%s', service_start, 'utc') AS INTEGER), CAST(strftime('%s', service_end, 'utc') AS INTEGER),
                       event_id, server_id
                  FROM member_premium
                 WHERE m_id = ? AND service_end > datetime('now','localtime') AND server_id IN (0, ?)
                 ORDER BY pre_type, service_end DESC
                """;
        List<PremiumRow> out = new ArrayList<>();
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, mId);
            ps.setInt(2, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                int lastType = -1;
                while (rs.next()) {
                    int preType = rs.getInt(1);
                    if (preType == lastType) {
                        continue;
                    }
                    lastType = preType;
                    out.add(new PremiumRow(preType, rs.getLong(2), rs.getLong(3),
                            rs.getInt(4), rs.getInt(5)));
                }
            }
        } catch (SQLException e) {
            throw new StoreException("读账号 " + mId + " 的会员失败：" + e.getMessage(), e);
        }
        return out;
    }

    public long blackDiamondEnd(long mId) {
        String sql = "SELECT CAST(strftime('%s', black_diamond_end, 'utc') AS INTEGER) FROM member_info WHERE m_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, mId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return 0L;
                }
                return Math.max(0L, rs.getLong(1));
            }
        } catch (SQLException e) {
            throw new StoreException("读账号 " + mId + " 的黑钻订阅失败：" + e.getMessage(), e);
        }
    }

    /** 写 member_info.black_diamond_end（本地时间存，和 {@link #blackDiamondEnd} 的读法对称）。 */
    public void saveBlackDiamondEnd(long mId, long end) {
        String sql = "UPDATE member_info SET black_diamond_end = datetime(?,'unixepoch','localtime') WHERE m_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, end);
            ps.setLong(2, mId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("写账号 " + mId + " 的黑钻订阅失败：" + e.getMessage(), e);
        }
    }

    public void savePremium(long mId, int preType, long start, long end,
                            int eventId, int serverId) {
        String update = """
                UPDATE member_premium SET service_end = datetime(?,'unixepoch','localtime')
                 WHERE event_id = ? AND pre_type = ? AND m_id = ?
                   AND service_end > datetime(?,'unixepoch','localtime') AND server_id = ?
                """;
        String insert = """
                INSERT INTO member_premium
                       (pre_type, m_id, service_start, service_end, event_id, server_id)
                VALUES (?, ?, datetime(?,'unixepoch','localtime'), datetime(?,'unixepoch','localtime'), ?, ?)
                """;
        try (Connection c = connection()) {
            try (PreparedStatement ps = c.prepareStatement(update)) {
                ps.setLong(1, end);
                ps.setInt(2, eventId);
                ps.setInt(3, preType);
                ps.setLong(4, mId);
                ps.setLong(5, start);
                ps.setInt(6, serverId);
                if (ps.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement ps = c.prepareStatement(insert)) {
                ps.setInt(1, preType);
                ps.setLong(2, mId);
                ps.setLong(3, start);
                ps.setLong(4, end);
                ps.setInt(5, eventId);
                ps.setInt(6, serverId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new StoreException("写账号 " + mId + " 的会员 " + preType + " 失败："
                    + e.getMessage(), e);
        }
    }
}
