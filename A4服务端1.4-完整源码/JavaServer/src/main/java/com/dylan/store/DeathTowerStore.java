package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DeathTowerStore extends DomainStore {

    DeathTowerStore(DataSource dataSource) {
        super(dataSource);
    }

    public record TowerRecord(int characNo, int towerIndex, int partType,
                              String memberInfo, int stage, int playTimeMs) {}

    private static int clampPartType(int partType) {
        return Math.clamp(partType, 1, 4);
    }

    public Optional<TowerRecord> towerBestRecord(int characNo, int towerIndex, int partType) {
        int part = clampPartType(partType);
        String sql = "SELECT member_info_" + part + ", stage_" + part + ", play_time_" + part
                + " FROM charac_tower_record WHERE charac_no = ? AND tower_index = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            ps.setInt(2, towerIndex);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(2) <= 0) {
                    return Optional.empty();
                }
                return Optional.of(new TowerRecord(characNo, towerIndex, part,
                        rs.getString(1), rs.getInt(2), rs.getInt(3)));
            }
        } catch (SQLException e) {
            throw new StoreException("读死亡之塔最佳记录失败：" + e.getMessage(), e);
        }
    }

    public boolean saveTowerBestRecord(TowerRecord record) {
        int part = clampPartType(record.partType());
        String update = "UPDATE charac_tower_record SET member_info_" + part + " = ?, stage_"
                + part + " = ?, play_time_" + part + " = ?, occ_time_" + part + " = datetime('now','localtime')"
                + " WHERE charac_no = ? AND tower_index = ? AND stage_" + part + " <= ?";
        String insert = "INSERT INTO charac_tower_record(charac_no, tower_index, member_info_"
                + part + ", stage_" + part + ", play_time_" + part + ", occ_time_" + part
                + ") VALUES(?, ?, ?, ?, ?, datetime('now','localtime'))";
        try (Connection c = connection()) {
            try (PreparedStatement ps = c.prepareStatement(update)) {
                ps.setString(1, record.memberInfo());
                ps.setInt(2, record.stage());
                ps.setInt(3, record.playTimeMs());
                ps.setInt(4, record.characNo());
                ps.setInt(5, record.towerIndex());
                ps.setInt(6, record.stage());
                if (ps.executeUpdate() > 0) {
                    return true;
                }
            }
            try (PreparedStatement ps = c.prepareStatement(insert)) {
                ps.setInt(1, record.characNo());
                ps.setInt(2, record.towerIndex());
                ps.setString(3, record.memberInfo());
                ps.setInt(4, record.stage());
                ps.setInt(5, record.playTimeMs());
                ps.executeUpdate();
                return true;
            } catch (SQLException duplicate) {
                if (!isUniqueViolation(duplicate)) {
                    throw duplicate;
                }
                return false;
            }
        } catch (SQLException e) {
            throw new StoreException("写死亡之塔最佳记录失败：" + e.getMessage(), e);
        }
    }

    public int towerRank(int towerIndex, int partType, int stage, int playTimeMs) {
        int part = clampPartType(partType);
        String sql = "SELECT COUNT(*) FROM charac_tower_record WHERE tower_index = ?"
                + " AND stage_" + part + " > 0 AND (stage_" + part + " > ?"
                + " OR (stage_" + part + " = ? AND play_time_" + part + " < ?))";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, towerIndex);
            ps.setInt(2, stage);
            ps.setInt(3, stage);
            ps.setInt(4, playTimeMs);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) + 1 : 1;
            }
        } catch (SQLException e) {
            throw new StoreException("算死亡之塔名次失败：" + e.getMessage(), e);
        }
    }

    public List<TowerRecord> towerTopRankers(int towerIndex, int partType, int limit) {
        int part = clampPartType(partType);
        String sql = "SELECT charac_no, member_info_" + part + ", stage_" + part
                + ", play_time_" + part + " FROM charac_tower_record"
                + " WHERE tower_index = ? AND stage_" + part + " > 0"
                + " ORDER BY stage_" + part + " DESC, play_time_" + part + " ASC LIMIT ?";
        List<TowerRecord> out = new ArrayList<>();
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, towerIndex);
            ps.setInt(2, Math.max(0, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new TowerRecord(rs.getInt(1), towerIndex, part,
                            rs.getString(2), rs.getInt(3), rs.getInt(4)));
                }
            }
        } catch (SQLException e) {
            throw new StoreException("读死亡之塔榜首失败：" + e.getMessage(), e);
        }
        return out;
    }
}
