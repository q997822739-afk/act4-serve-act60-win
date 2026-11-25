package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class CreatureStore extends DomainStore {

    CreatureStore(DataSource dataSource) {
        super(dataSource);
    }

    private static final String CREATURE_COLUMNS =
            "ui_id, slot, it_id, name, stomach, exp, endurance, creature_type, no_charge, stat";

    public List<CreatureItemRow> loadCreatureItems(int characNo) {
        String sql = "SELECT " + CREATURE_COLUMNS + " FROM creature_items "
                + "WHERE charac_no = ? AND stat = 0 AND expire_date > datetime('now','localtime') ORDER BY slot";
        List<CreatureItemRow> out = new ArrayList<>();
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new CreatureItemRow(rs.getLong(1), characNo, rs.getInt(2),
                            rs.getInt(3), rs.getString(4), rs.getInt(5), rs.getLong(6),
                            0, rs.getInt(7), rs.getInt(8), rs.getInt(9), rs.getInt(10)));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new StoreException("读 charac_no=" + characNo + " 的宠物失败："
                    + e.getMessage(), e);
        }
    }

    public CreatureItemRow insertCreatureItem(int characNo, int slot, int itemId,
                                              int creatureType) {
        String sql = "INSERT INTO creature_items (charac_no, slot, it_id, reg_date, name, "
                + "stomach, exp, endurance, no_charge, stat, creature_type) "
                + "VALUES (?, ?, ?, datetime('now','localtime'), '', ?, 0, 0, 0, 0, ?)";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, characNo);
            ps.setInt(2, slot);
            ps.setInt(3, itemId);
            ps.setInt(4, CreatureItemRow.FULL_STOMACH);
            ps.setInt(5, creatureType);
            if (ps.executeUpdate() != 1) {
                throw new StoreException("往 charac_no=" + characNo + " 插宠物 " + itemId
                        + " 时没有影响任何行");
            }
            long uiId = CreatureItemRow.NOT_PERSISTED;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    uiId = keys.getLong(1);
                }
            }
            return CreatureItemRow.ofNew(characNo, slot, itemId, creatureType).withUiId(uiId);
        } catch (SQLException e) {
            throw new StoreException("往 charac_no=" + characNo + " 插宠物 " + itemId
                    + " 失败：" + e.getMessage(), e);
        }
    }

    public boolean moveCreatureItem(long uiId, int slot) {
        String sql = "UPDATE creature_items SET slot = ? WHERE ui_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, slot);
            ps.setLong(2, uiId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StoreException("把宠物 " + uiId + " 挪到槽 " + slot + " 失败："
                    + e.getMessage(), e);
        }
    }

    public boolean renameCreatureItem(long uiId, String name) {
        String sql = "UPDATE creature_items SET name = ? WHERE ui_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setLong(2, uiId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StoreException("给宠物 " + uiId + " 改名失败：" + e.getMessage(), e);
        }
    }

    public boolean hatchCreatureItem(long uiId, int hatchedItemId) {
        String sql = "UPDATE creature_items SET it_id = ?, creature_type = ?, stomach = ? "
                + "WHERE ui_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, hatchedItemId);
            ps.setInt(2, CreatureItemRow.TYPE_CREATURE);
            ps.setInt(3, CreatureItemRow.FULL_STOMACH);
            ps.setLong(4, uiId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StoreException("孵化宠物 " + uiId + " 失败：" + e.getMessage(), e);
        }
    }

    public boolean evolveCreatureItem(long uiId, int evolvedItemId) {
        String sql = "UPDATE creature_items SET it_id = ? WHERE ui_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, evolvedItemId);
            ps.setLong(2, uiId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StoreException("宠物 " + uiId + " 进化落库失败：" + e.getMessage(), e);
        }
    }

    public boolean updateCreatureItemStat(long uiId, long exp, int stomach) {
        String sql = "UPDATE creature_items SET exp = ?, stomach = ? WHERE ui_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, exp);
            ps.setInt(2, stomach);
            ps.setLong(3, uiId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StoreException("存宠物 " + uiId + " 的经验/饱食度失败："
                    + e.getMessage(), e);
        }
    }
}
