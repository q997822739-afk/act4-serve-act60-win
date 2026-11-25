package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class GmStore extends DomainStore {

    GmStore(DataSource dataSource) {
        super(dataSource);
    }

    public java.util.Map<Long, Integer> loadGmManifest() {
        String sql = "SELECT m_id, level FROM gm_manifest";
        java.util.Map<Long, Integer> out = new java.util.HashMap<>();
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getLong(1), rs.getInt(2));
            }
        } catch (SQLException e) {
            throw new StoreException("读 gm_manifest 失败：" + e.getMessage(), e);
        }
        return out;
    }

    public int gmLevel(long mId) {
        String sql = "SELECT level FROM gm_manifest WHERE m_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, mId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new StoreException("读账号 " + mId + " 的 GM 等级失败：" + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------ 角色在线存在表
    //
    // 见 GmSchema 里 server_presence 的注释：这张表只给外部工具（网页 GM）读，
    // 用来判断"某个角色是不是正被服务端拿在内存里"。有行 = 在游戏中，没行 = 选择界面。

    /** 服务端启动时清空 —— 上一个进程可能是崩溃退出的，残留行会让 GM 误判成"还在游戏里"。 */
    public void clearAllPresence() {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM server_presence")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("清空 server_presence 失败：" + e.getMessage(), e);
        }
    }

    /** 角色进游戏（selectCharacter 加载完）：登记"服务端正持有它"。 */
    public void markPresent(int characNo, String characName, long mId) {
        String sql = "INSERT INTO server_presence (charac_no, charac_name, m_id)"
                + " VALUES (?, ?, ?)"
                + " ON CONFLICT(charac_no) DO UPDATE SET"
                + " charac_name = excluded.charac_name, m_id = excluded.m_id,"
                + " since = datetime('now','localtime')";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            ps.setString(2, fit(characName, 20));
            ps.setLong(3, mId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("登记角色 " + characNo + " 在线失败：" + e.getMessage(), e);
        }
    }

    /** 小退 / 断线：注销，服务端不再持有它。 */
    public void clearPresence(int characNo) {
        if (characNo < 0) {
            return;
        }
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM server_presence WHERE charac_no = ?")) {
            ps.setInt(1, characNo);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("注销角色 " + characNo + " 在线失败：" + e.getMessage(), e);
        }
    }

    /** 当前被服务端持有的角色号（在线 = 在游戏中）。 */
    public java.util.Set<Integer> loadPresent() {
        java.util.Set<Integer> out = new java.util.HashSet<>();
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement("SELECT charac_no FROM server_presence");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(rs.getInt(1));
            }
        } catch (SQLException e) {
            throw new StoreException("读 server_presence 失败：" + e.getMessage(), e);
        }
        return out;
    }

    public void setGmLevel(long mId, int level) {
        String sql = "INSERT INTO gm_manifest (m_id, level) VALUES (?, ?)"
                + " ON CONFLICT(m_id) DO UPDATE SET level = excluded.level";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, mId);
            ps.setInt(2, Math.clamp(level, 0, 0xFF));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("写账号 " + mId + " 的 GM 等级失败：" + e.getMessage(), e);
        }
    }

    public boolean clearGmLevel(long mId) {
        String sql = "DELETE FROM gm_manifest WHERE m_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, mId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new StoreException("删账号 " + mId + " 的 GM 资格失败：" + e.getMessage(), e);
        }
    }

    public record CharacterOwner(long accountId, int characNo, String name) {}

    public Optional<CharacterOwner> characterOwner(String name) {
        String sql = "SELECT m_id, charac_no, charac_name FROM charac_info WHERE charac_name = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new CharacterOwner(rs.getLong(1), rs.getInt(2),
                                rs.getString(3)))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("查角色「" + name + "」属于哪个账号失败："
                    + e.getMessage(), e);
        }
    }

    public void logGmCommand(long mId, int gmLevel, int characNo, String characName,
                             String command, String detail, boolean accepted, String result) {
        String sql = "INSERT INTO gm_command_log"
                + " (m_id, gm_level, charac_no, charac_name, command, detail, accepted, result)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, mId);
            ps.setInt(2, Math.clamp(gmLevel, 0, 0xFF));
            ps.setInt(3, characNo);
            ps.setString(4, fit(characName, 64));
            ps.setString(5, fit(command, 64));
            ps.setString(6, fit(detail, 255));
            ps.setInt(7, accepted ? 1 : 0);
            ps.setString(8, fit(result, 255));
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }
}
