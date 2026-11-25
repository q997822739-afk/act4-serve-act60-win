package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class ElixirStore extends DomainStore {

    ElixirStore(DataSource dataSource) {
        super(dataSource);
    }

    public java.util.Map<Integer, Integer> loadElixirs(int characNo) {
        String sql = "SELECT item_id, used FROM charac_elixir WHERE charac_no = ? AND used > 0";
        java.util.Map<Integer, Integer> out = new java.util.LinkedHashMap<>();
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getInt(1), rs.getInt(2));
                }
            }
        } catch (SQLException e) {
            throw new StoreException("读属性石记录失败：" + e.getMessage(), e);
        }
        return out;
    }

    public int addElixirUse(int characNo, int itemId, int count) {
        String sql = "INSERT INTO charac_elixir(charac_no, item_id, used) VALUES(?, ?, ?)"
                + " ON CONFLICT(charac_no, item_id) DO UPDATE SET used = used + excluded.used";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            ps.setInt(2, itemId);
            ps.setInt(3, count);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("写属性石记录失败：" + e.getMessage(), e);
        }
        return loadElixirs(characNo).getOrDefault(itemId, count);
    }
}
