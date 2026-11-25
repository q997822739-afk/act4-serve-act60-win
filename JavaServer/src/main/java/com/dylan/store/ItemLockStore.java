package com.dylan.store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.dylan.store.blob.ItemLockTable;

public final class ItemLockStore extends DomainStore {
    ItemLockStore(DataSource dataSource) {
        super(dataSource);
    }

    public ItemLockTable loadItemLocks(int characNo) {
        String sql = "SELECT item_lock_info FROM charac_item_lock_info WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return ItemLockTable.empty();
                }
                return ItemLockTable.decode(rs.getBytes("item_lock_info"));
            }
        } catch (SQLException e) {
            throw new StoreException("读 charac_no=" + characNo + " 的物品锁定表失败："
                    + e.getMessage(), e);
        }
    }

    public void saveItemLocks(int characNo, ItemLockTable table) {
        String sql = "INSERT INTO charac_item_lock_info (charac_no, item_lock_info) VALUES (?, ?) "
                + "ON CONFLICT(charac_no) DO UPDATE SET item_lock_info = excluded.item_lock_info";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            ps.setBytes(2, table.encode());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("存 charac_no=" + characNo + " 的物品锁定表失败："
                    + e.getMessage(), e);
        }
    }
}
