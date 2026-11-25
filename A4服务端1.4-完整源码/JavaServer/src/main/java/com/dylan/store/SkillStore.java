package com.dylan.store;

import com.dylan.store.blob.SkillSlots;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class SkillStore extends DomainStore {

    SkillStore(DataSource dataSource) {
        super(dataSource);
    }

    public static void insertSkill(Connection c, int characNo, byte[] skillSlot)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO skill (charac_no, remain_sp, skill_slot, request_sp, skill_slot_2nd, "
                        + "request_sp_2nd, skill_slot_lethe, skill_slot_lethe_2nd, skill_command) "
                        + "VALUES (?, 0, ?, '', '', '', '', '', '')")) {
            ps.setInt(1, characNo);
            if (skillSlot == null) {
                ps.setString(2, "");
            } else {
                ps.setBytes(2, skillSlot);
            }
            ps.executeUpdate();
        }
    }

    public Optional<CharacterSkills> loadSkills(int characNo) {
        String sql = "SELECT remain_sp, skill_slot, skill_slot_2nd FROM skill WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new CharacterSkills(
                        characNo,
                        rs.getLong("remain_sp"),
                        SkillSlots.decode(rs.getBytes("skill_slot")),
                        SkillSlots.decode(rs.getBytes("skill_slot_2nd"))));
            }
        } catch (SQLException e) {
            throw new StoreException("读 charac_no=" + characNo + " 的技能失败：" + e.getMessage(), e);
        }
    }

    public void saveSkills(CharacterSkills skills) {
        String sql = "UPDATE skill SET remain_sp = ?, skill_slot = ?, skill_slot_2nd = ? "
                + "WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, skills.remainSp());
            ps.setBytes(2, skills.primary().encode());
            ps.setBytes(3, skills.secondary().encode());
            ps.setInt(4, skills.characNo());
            if (ps.executeUpdate() != 1) {
                throw new StoreException("存技能时 charac_no=" + skills.characNo() + " 没有对应的行");
            }
        } catch (SQLException e) {
            throw new StoreException("存 charac_no=" + skills.characNo() + " 的技能失败："
                    + e.getMessage(), e);
        }
    }
}
