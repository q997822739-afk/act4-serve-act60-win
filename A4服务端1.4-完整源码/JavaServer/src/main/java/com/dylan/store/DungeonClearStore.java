package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class DungeonClearStore extends DomainStore {

    DungeonClearStore(DataSource dataSource) {
        super(dataSource);
    }

    public static void insertMemberDungeon(Connection c, long mId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO member_dungeon (m_id, dungeon) VALUES (?, '')")) {
            ps.setLong(1, mId);
            ps.executeUpdate();
        }
    }

    public Optional<String> loadDungeonClear(long mId) {
        String sql = "SELECT dungeon FROM member_dungeon WHERE m_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, mId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String value = rs.getString(1);
                return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
            }
        } catch (SQLException e) {
            throw new StoreException("读账号 " + mId + " 的副本通关记录失败：" + e.getMessage(), e);
        }
    }

    public void saveDungeonClear(long mId, String serialized) {
        String sql = "INSERT INTO member_dungeon (m_id, dungeon) VALUES (?, ?)"
                + " ON CONFLICT(m_id) DO UPDATE SET dungeon = excluded.dungeon";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, mId);
            ps.setString(2, serialized);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("写账号 " + mId + " 的副本通关记录失败：" + e.getMessage(), e);
        }
    }
}
