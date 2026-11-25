package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 公会表（GuildSchema）。成员列表连 charac_info 取名字 / 等级 / 职业。 */
public final class GuildStore extends DomainStore {

    GuildStore(DataSource dataSource) {
        super(dataSource);
    }

    public record Guild(int id, String name, String domain, int master, int level, int exp, String notice) {
    }

    public record Member(int characNo, int grade, String name, int level, int job, int growType) {
    }

    public static final int GRADE_MASTER = 1;
    public static final int GRADE_MEMBER = 0;

    public Optional<Guild> guildOf(int characNo) {
        return one("SELECT g.guild_id, g.name, g.domain, g.master_charac, g.level, g.exp, g.notice"
                + " FROM guild g JOIN guild_member m ON m.guild_id = g.guild_id WHERE m.charac_no = ?", characNo);
    }

    public Optional<Guild> byId(int guildId) {
        return one("SELECT guild_id, name, domain, master_charac, level, exp, notice FROM guild WHERE guild_id = ?",
                guildId);
    }

    public boolean nameTaken(String name) {
        return exists("SELECT 1 FROM guild WHERE name = ?", name);
    }

    public boolean domainTaken(String domain) {
        return exists("SELECT 1 FROM guild WHERE domain = ?", domain);
    }

    public int create(String name, String domain, int master) {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            int id;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO guild(name, domain, master_charac) VALUES(?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, domain);
                ps.setInt(3, master);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    id = rs.getInt(1);
                }
            }
            addMember(c, master, id, GRADE_MASTER);
            c.commit();
            return id;
        } catch (SQLException e) {
            throw new StoreException("建公会失败：" + e.getMessage(), e);
        }
    }

    public void join(int characNo, int guildId) {
        try (Connection c = connection()) {
            addMember(c, characNo, guildId, GRADE_MEMBER);
        } catch (SQLException e) {
            throw new StoreException("加入公会失败：" + e.getMessage(), e);
        }
    }

    public void leave(int characNo) {
        update("DELETE FROM guild_member WHERE charac_no = ?", characNo);
    }

    public void dismiss(int guildId) {
        update("DELETE FROM guild_member WHERE guild_id = ?", guildId);
        update("DELETE FROM guild WHERE guild_id = ?", guildId);
    }

    public void setNotice(int guildId, String notice) {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement("UPDATE guild SET notice = ? WHERE guild_id = ?")) {
            ps.setString(1, notice);
            ps.setInt(2, guildId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("改公会公告失败：" + e.getMessage(), e);
        }
    }

    public void setMaster(int guildId, int oldMaster, int newMaster) {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try (PreparedStatement g = c.prepareStatement("UPDATE guild SET master_charac = ? WHERE guild_id = ?");
                 PreparedStatement m = c.prepareStatement("UPDATE guild_member SET grade = ? WHERE charac_no = ?")) {
                g.setInt(1, newMaster);
                g.setInt(2, guildId);
                g.executeUpdate();
                m.setInt(1, GRADE_MEMBER);
                m.setInt(2, oldMaster);
                m.executeUpdate();
                m.setInt(1, GRADE_MASTER);
                m.setInt(2, newMaster);
                m.executeUpdate();
            }
            c.commit();
        } catch (SQLException e) {
            throw new StoreException("转让会长失败：" + e.getMessage(), e);
        }
    }

    public void setLevel(int guildId, int level) {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement("UPDATE guild SET level = ? WHERE guild_id = ?")) {
            ps.setInt(1, level);
            ps.setInt(2, guildId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("改公会等级失败：" + e.getMessage(), e);
        }
    }

    public List<Member> members(int guildId) {
        String sql = "SELECT m.charac_no, m.grade, c.charac_name, c.lev, c.job, c.grow_type"
                + " FROM guild_member m JOIN charac_info c ON c.charac_no = m.charac_no"
                + " WHERE m.guild_id = ? ORDER BY m.grade DESC, m.charac_no";
        List<Member> out = new ArrayList<>();
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Member(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getInt(4),
                            rs.getInt(5), rs.getInt(6)));
                }
            }
        } catch (SQLException e) {
            throw new StoreException("读公会成员失败：" + e.getMessage(), e);
        }
        return out;
    }

    private static void addMember(Connection c, int characNo, int guildId, int grade) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR REPLACE INTO guild_member(charac_no, guild_id, grade) VALUES(?, ?, ?)")) {
            ps.setInt(1, characNo);
            ps.setInt(2, guildId);
            ps.setInt(3, grade);
            ps.executeUpdate();
        }
    }

    private Optional<Guild> one(String sql, int arg) {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new Guild(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                        rs.getInt(5), rs.getInt(6), rs.getString(7)))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("读公会失败：" + e.getMessage(), e);
        }
    }

    private boolean exists(String sql, String arg) {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new StoreException("查公会失败：" + e.getMessage(), e);
        }
    }

    private void update(String sql, int arg) {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, arg);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("写公会失败：" + e.getMessage(), e);
        }
    }
}
