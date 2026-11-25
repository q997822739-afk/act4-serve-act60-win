package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 师徒关系（MentorSchema）。名字从 charac_info 取。 */
public final class MentorStore extends DomainStore {

    MentorStore(DataSource dataSource) {
        super(dataSource);
    }

    public record Person(int characNo, String name) {
    }

    public Optional<Person> byName(String name) {
        String sql = "SELECT charac_no, charac_name FROM charac_info WHERE charac_name = ? AND delete_flag = 0";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new Person(rs.getInt(1), rs.getString(2))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("按名字查角色失败：" + e.getMessage(), e);
        }
    }

    public Optional<Person> byId(int characNo) {
        String sql = "SELECT charac_no, charac_name FROM charac_info WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new Person(rs.getInt(1), rs.getString(2))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("查角色 " + characNo + " 失败：" + e.getMessage(), e);
        }
    }

    /** 这个角色的师傅。没有则空。 */
    public Optional<Person> masterOf(int apprenticeNo) {
        String sql = "SELECT c.charac_no, c.charac_name FROM mentor_link m"
                + " JOIN charac_info c ON c.charac_no = m.master_no WHERE m.apprentice_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, apprenticeNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new Person(rs.getInt(1), rs.getString(2))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("读师傅失败：" + e.getMessage(), e);
        }
    }

    public List<Person> apprenticesOf(int masterNo) {
        String sql = "SELECT c.charac_no, c.charac_name FROM mentor_link m"
                + " JOIN charac_info c ON c.charac_no = m.apprentice_no WHERE m.master_no = ? ORDER BY m.apprentice_no";
        List<Person> out = new ArrayList<>();
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, masterNo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Person(rs.getInt(1), rs.getString(2)));
                }
            }
        } catch (SQLException e) {
            throw new StoreException("读徒弟失败：" + e.getMessage(), e);
        }
        return out;
    }

    public boolean isMaster(int characNo) {
        return !apprenticesOf(characNo).isEmpty();
    }

    public void bind(int masterNo, int apprenticeNo) {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR REPLACE INTO mentor_link(apprentice_no, master_no) VALUES(?, ?)")) {
            ps.setInt(1, apprenticeNo);
            ps.setInt(2, masterNo);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("写入师徒关系失败：" + e.getMessage(), e);
        }
    }

    public void unbindApprentice(int apprenticeNo) {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM mentor_link WHERE apprentice_no = ?")) {
            ps.setInt(1, apprenticeNo);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("解除师徒失败：" + e.getMessage(), e);
        }
    }
}
