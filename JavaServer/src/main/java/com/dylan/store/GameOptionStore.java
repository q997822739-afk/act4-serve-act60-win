package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class GameOptionStore extends DomainStore {

    GameOptionStore(DataSource dataSource) {
        super(dataSource);
    }

    public record GameOptionRow(byte[] option, byte[] hotkeys) {
    }

    public Optional<GameOptionRow> loadGameOption(long mId) {
        String sql = "SELECT option_1, option_2 FROM member_game_option WHERE m_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, mId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new GameOptionRow(rs.getBytes(1), rs.getBytes(2)));
            }
        } catch (SQLException e) {
            throw new StoreException("读账号 " + mId + " 的系统设置失败：" + e.getMessage(), e);
        }
    }

    public void saveGameOption(long mId, byte[] option) {
        upsertGameOptionColumn(mId, "option_1", option);
    }

    public void saveHotkeyOption(long mId, byte[] hotkeys) {
        upsertGameOptionColumn(mId, "option_2", hotkeys);
    }

    private void upsertGameOptionColumn(long mId, String column, byte[] value) {
        String sql = "INSERT INTO member_game_option (m_id, option_1, option_2, option_3,"
                + " shortcut_emoticon) VALUES (?, ?, ?, '', '')"
                + " ON CONFLICT(m_id) DO UPDATE SET " + column + " = excluded." + column;
        boolean isOption1 = "option_1".equals(column);
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, mId);
            ps.setBytes(2, isOption1 ? value : new byte[0]);
            ps.setBytes(3, isOption1 ? new byte[0] : value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("写账号 " + mId + " 的 " + column + " 失败："
                    + e.getMessage(), e);
        }
    }
}
