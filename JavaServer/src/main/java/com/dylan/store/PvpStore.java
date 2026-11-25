package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class PvpStore extends DomainStore {

    PvpStore(DataSource dataSource) {
        super(dataSource);
    }

    public static void insertPvp(Connection c, int characNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO pvp_result (charac_no) VALUES (?)")) {
            ps.setInt(1, characNo);
            ps.executeUpdate();
        }
    }

    public long winPoint(int characNo) {
        String sql = "SELECT win_point FROM pvp_result WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new StoreException("读 charac_no=" + characNo + " 的决斗胜点失败："
                    + e.getMessage(), e);
        }
    }

    public boolean setWinPoint(int characNo, long value) {
        String sql = "UPDATE pvp_result SET win_point = ? WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, Math.clamp(value, 0, 0xFFFF_FFFFL));
            ps.setInt(2, characNo);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StoreException("写 charac_no=" + characNo + " 的决斗胜点失败："
                    + e.getMessage(), e);
        }
    }

    /** NOTI 47 的段位字节会盖掉客户端 +0xCC。没有行时返回 0，和库里的默认一样。 */
    public int grade(int characNo) {
        String sql = "SELECT pvp_grade FROM pvp_result WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new StoreException("读 charac_no=" + characNo + " 的决斗段位失败："
                    + e.getMessage(), e);
        }
    }

    /** 一局结束。胜点写绝对值；胜、负场各加 0 或 1。平局两个增量都是 0，场次仍加 1。 */
    public boolean recordMatch(int characNo, int winAdd, int loseAdd, long winPoint) {
        String sql = "UPDATE pvp_result SET win = win + ?, lose = lose + ?,"
                + " pvp_count = pvp_count + 1, play_count = play_count + 1, win_point = ?"
                + " WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, winAdd);
            ps.setInt(2, loseAdd);
            ps.setLong(3, Math.clamp(winPoint, 0, 0xFFFF_FFFFL));
            ps.setInt(4, characNo);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StoreException("写 charac_no=" + characNo + " 的决斗结算失败："
                    + e.getMessage(), e);
        }
    }

}
