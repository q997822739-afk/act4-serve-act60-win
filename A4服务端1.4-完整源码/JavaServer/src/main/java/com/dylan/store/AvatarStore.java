package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AvatarStore extends DomainStore {

    AvatarStore(DataSource dataSource) {
        super(dataSource);
    }

    public List<AvatarItemRow> loadAvatarItems(int characNo) {
        String sql = "SELECT ui_id, slot, it_id, expire_date, ability_no, ipg_agency_no "
                + "FROM user_items WHERE charac_no = ? AND stat = 0 ORDER BY slot";
        List<AvatarItemRow> out = new ArrayList<>();
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp expire = rs.getTimestamp(4);
                    Instant expireAt = expire == null
                            || !expire.toInstant().isAfter(AvatarItemRow.NO_EXPIRE)
                            ? null : expire.toInstant();
                    out.add(new AvatarItemRow(rs.getLong(1), characNo, rs.getInt(2), rs.getInt(3),
                            expireAt, rs.getInt(5), parseCommodityNo(rs.getString(6))));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new StoreException("读 charac_no=" + characNo + " 的装扮栏失败："
                    + e.getMessage(), e);
        }
    }

    public AvatarItemRow insertAvatarItem(int characNo, int slot, int itemId, int days,
                                          int abilityNo, long commodityNo, int obtainFrom) {
        boolean permanent = days <= 0;
        String sql = permanent
                ? "INSERT INTO user_items (charac_no, slot, it_id, obtain_from, reg_date, "
                        + "ability_no, ipg_agency_no, jewel_socket) "
                        + "VALUES (?, ?, ?, ?, datetime('now','localtime'), ?, ?, '')"
                : "INSERT INTO user_items (charac_no, slot, it_id, expire_date, obtain_from, "
                        + "reg_date, ability_no, ipg_agency_no, jewel_socket) "
                        + "VALUES (?, ?, ?, datetime('now','localtime','+'||?||' days'), ?, datetime('now','localtime'), ?, ?, '')";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int at = 1;
            ps.setInt(at++, characNo);
            ps.setInt(at++, slot);
            ps.setInt(at++, itemId);
            if (!permanent) {
                ps.setInt(at++, days);
            }
            ps.setInt(at++, obtainFrom);
            ps.setInt(at++, abilityNo);
            ps.setString(at, Long.toString(commodityNo));
            if (ps.executeUpdate() != 1) {
                throw new StoreException("往 charac_no=" + characNo + " 的装扮栏插 " + itemId
                        + " 时没有影响任何行");
            }
            long uiId = AvatarItemRow.NOT_PERSISTED;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    uiId = keys.getLong(1);
                }
            }
            Instant expireAt = permanent ? null : Instant.now().plusSeconds(86_400L * days);
            return new AvatarItemRow(uiId, characNo, slot, itemId, expireAt, abilityNo,
                    commodityNo);
        } catch (SQLException e) {
            throw new StoreException("往 charac_no=" + characNo + " 的装扮栏插 " + itemId
                    + " 失败：" + e.getMessage(), e);
        }
    }

    public boolean moveAvatarItem(long uiId, int slot) {
        String sql = "UPDATE user_items SET slot = ?, clear_avatar_id = 0 WHERE ui_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, slot);
            ps.setLong(2, uiId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StoreException("把装扮栏第 " + uiId + " 行挪到槽 " + slot + " 失败："
                    + e.getMessage(), e);
        }
    }

    public boolean deleteAvatarItem(long uiId) {
        String sql = "DELETE FROM user_items WHERE ui_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, uiId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StoreException("删装扮栏第 " + uiId + " 行失败：" + e.getMessage(), e);
        }
    }

    public AvatarItemRow insertAvatarItemAt(int characNo, int slot, int itemId, Instant expireAt,
                                            int abilityNo, String ipgAgencyNo, int obtainFrom) {
        boolean permanent = expireAt == null || !expireAt.isAfter(AvatarItemRow.NO_EXPIRE);
        String sql = permanent
                ? "INSERT INTO user_items (charac_no, slot, it_id, obtain_from, reg_date, "
                        + "ability_no, ipg_agency_no, jewel_socket) "
                        + "VALUES (?, ?, ?, ?, datetime('now','localtime'), ?, ?, '')"
                : "INSERT INTO user_items (charac_no, slot, it_id, expire_date, obtain_from, "
                        + "reg_date, ability_no, ipg_agency_no, jewel_socket) "
                        + "VALUES (?, ?, ?, datetime(?,'unixepoch','localtime'), ?, datetime('now','localtime'), ?, ?, '')";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int at = 1;
            ps.setInt(at++, characNo);
            ps.setInt(at++, slot);
            ps.setInt(at++, itemId);
            if (!permanent) {
                ps.setLong(at++, expireAt.getEpochSecond());
            }
            ps.setInt(at++, obtainFrom);
            ps.setInt(at++, abilityNo);
            ps.setString(at, ipgAgencyNo);
            if (ps.executeUpdate() != 1) {
                throw new StoreException("往 charac_no=" + characNo + " 的装扮栏插 " + itemId
                        + " 时没有影响任何行");
            }
            long uiId = AvatarItemRow.NOT_PERSISTED;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    uiId = keys.getLong(1);
                }
            }
            return new AvatarItemRow(uiId, characNo, slot, itemId, permanent ? null : expireAt,
                    abilityNo, parseCommodityNo(ipgAgencyNo));
        } catch (SQLException e) {
            throw new StoreException("往 charac_no=" + characNo + " 的装扮栏插 " + itemId
                    + " 失败：" + e.getMessage(), e);
        }
    }

    public boolean changeAvatarStat(long uiId, int characNo, int stat, int slot) {
        String sql = "UPDATE user_items SET stat = ?, slot = ? WHERE ui_id = ? AND charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, stat);
            ps.setInt(2, slot);
            ps.setLong(3, uiId);
            ps.setInt(4, characNo);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StoreException("把时装 ui_id=" + uiId + " 的 stat 改成 " + stat + " 失败："
                    + e.getMessage(), e);
        }
    }

    public Optional<AvatarItemRow> findAvatarItem(long uiId) {
        String sql = "SELECT charac_no, slot, it_id, expire_date, ability_no, ipg_agency_no "
                + "FROM user_items WHERE ui_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, uiId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Timestamp expire = rs.getTimestamp(4);
                Instant expireAt = expire == null
                        || !expire.toInstant().isAfter(AvatarItemRow.NO_EXPIRE)
                        ? null : expire.toInstant();
                return Optional.of(new AvatarItemRow(uiId, rs.getInt(1), rs.getInt(2),
                        rs.getInt(3), expireAt, rs.getInt(5), parseCommodityNo(rs.getString(6))));
            }
        } catch (SQLException e) {
            throw new StoreException("读时装 ui_id=" + uiId + " 失败：" + e.getMessage(), e);
        }
    }

    private static long parseCommodityNo(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
