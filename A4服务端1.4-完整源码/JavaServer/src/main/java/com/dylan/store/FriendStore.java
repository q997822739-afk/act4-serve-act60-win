package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 好友名单（FriendSchema）。名字 / 等级 / 职业从 charac_info 取。 */
public final class FriendStore extends DomainStore {

    FriendStore(DataSource dataSource) {
        super(dataSource);
    }

    public record Person(int characNo, String name, int level, int job, int growType) {
    }

    private static final String PERSON = "SELECT charac_no, charac_name, lev, job, grow_type FROM charac_info";

    private static Person person(ResultSet rs) throws SQLException {
        return new Person(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5));
    }

    public Optional<Person> byName(String name) {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(PERSON + " WHERE charac_name = ? AND delete_flag = 0")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(person(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("按名字查角色失败：" + e.getMessage(), e);
        }
    }

    /** owner 名单里的人（按加入顺序）。删掉的角色不列。 */
    public List<Person> friendsOf(int ownerNo) {
        String sql = "SELECT c.charac_no, c.charac_name, c.lev, c.job, c.grow_type FROM friend_link f"
                + " JOIN charac_info c ON c.charac_no = f.friend_no"
                + " WHERE f.owner_no = ? AND c.delete_flag = 0 ORDER BY f.rowid";
        return list(sql, ownerNo);
    }

    /** 把 friendNo 加进名单的人（上下线要通知他们）。 */
    public List<Integer> ownersOf(int friendNo) {
        List<Integer> out = new ArrayList<>();
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement("SELECT owner_no FROM friend_link WHERE friend_no = ?")) {
            ps.setInt(1, friendNo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new StoreException("读好友反查失败：" + e.getMessage(), e);
        }
        return out;
    }

    public int count(int ownerNo) {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM friend_link WHERE owner_no = ?")) {
            ps.setInt(1, ownerNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new StoreException("数好友失败：" + e.getMessage(), e);
        }
    }

    public boolean has(int ownerNo, int friendNo) {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM friend_link WHERE owner_no = ? AND friend_no = ?")) {
            ps.setInt(1, ownerNo);
            ps.setInt(2, friendNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new StoreException("查好友失败：" + e.getMessage(), e);
        }
    }

    public void add(int ownerNo, int friendNo) {
        write("INSERT OR IGNORE INTO friend_link(owner_no, friend_no) VALUES(?, ?)", ownerNo, friendNo, "加好友");
    }

    public void remove(int ownerNo, int friendNo) {
        write("DELETE FROM friend_link WHERE owner_no = ? AND friend_no = ?", ownerNo, friendNo, "删好友");
    }

    private void write(String sql, int a, int b, String what) {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, a);
            ps.setInt(2, b);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException(what + "失败：" + e.getMessage(), e);
        }
    }

    private List<Person> list(String sql, int arg) {
        List<Person> out = new ArrayList<>();
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, arg);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(person(rs));
                }
            }
        } catch (SQLException e) {
            throw new StoreException("读好友名单失败：" + e.getMessage(), e);
        }
        return out;
    }
}
