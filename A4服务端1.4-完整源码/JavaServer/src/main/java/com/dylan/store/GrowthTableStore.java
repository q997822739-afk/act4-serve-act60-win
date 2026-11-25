package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class GrowthTableStore extends DomainStore {

    GrowthTableStore(DataSource dataSource) {
        super(dataSource);
    }

    static void seedMonsterReward(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM monster_reward_ref")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO monster_reward_ref (level, exp) VALUES (?, ?)")) {
            for (int level = 1; level <= GrowthTableSchema.MAX_SEEDED_LEVEL; level++) {
                ps.setInt(1, level);
                ps.setInt(2, GrowthTableSchema.MONSTER_REWARD_EXP[level - 1]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    static void seedSpReward(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM sp_reward")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        }
        int total = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO sp_reward (grade, sp) VALUES (?, ?)")) {
            for (int grade = 2; grade <= GrowthTableSchema.MAX_SEEDED_LEVEL; grade++) {
                int sp = GrowthTableSchema.spRewardFor(grade);
                total += sp;
                ps.setInt(1, grade);
                ps.setInt(2, sp);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public int spAtLevelUp(int grade) {
        if (grade < 2) {
            return 0;
        }
        String sql = "SELECT sp FROM sp_reward WHERE grade = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, grade);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new StoreException("读取升到 " + grade + " 级的 SP 失败：" + e.getMessage(), e);
        }
    }

    public int monsterRewardExp(int level) {
        String sql = "SELECT exp FROM monster_reward_ref WHERE level = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, level);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new StoreException("读取 " + level + " 级怪物经验失败：" + e.getMessage(), e);
        }
    }
}
