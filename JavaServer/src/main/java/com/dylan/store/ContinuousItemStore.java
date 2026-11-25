package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import com.dylan.store.blob.ContinuousItemBlob;

public final class ContinuousItemStore extends DomainStore {

    ContinuousItemStore(DataSource dataSource) {
        super(dataSource);
    }

    public Loaded loadContinuousItems(int characNo) {
        String sql = "SELECT cooltime_item, effect_item FROM charac_item_stat WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Loaded.EMPTY;
                }
                return new Loaded(ContinuousItemBlob.decode(rs.getBytes(1)),
                        ContinuousItemBlob.decode(rs.getBytes(2)));
            }
        } catch (SQLException e) {
            throw new StoreException("读角色 " + characNo + " 的持续性道具表失败："
                    + e.getMessage(), e);
        }
    }

    public record Loaded(Map<Integer, Long> coolTimes, Map<Integer, Long> effects) {

        static final Loaded EMPTY = new Loaded(Map.of(), Map.of());
    }

    public void saveCoolTimeItems(int characNo, Map<Integer, Long> entries) {
        save(characNo, "cooltime_item", entries);
    }

    public void saveEffectItems(int characNo, Map<Integer, Long> entries) {
        save(characNo, "effect_item", entries);
    }

    private void save(int characNo, String column, Map<Integer, Long> entries) {
        byte[] blob = ContinuousItemBlob.encode(entries);
        String sql = "INSERT INTO charac_item_stat(charac_no, " + column + ") VALUES(?, ?)"
                + " ON CONFLICT(charac_no) DO UPDATE SET " + column + " = excluded." + column;
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            ps.setBytes(2, blob);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("写角色 " + characNo + " 的 " + column + " 失败："
                    + e.getMessage(), e);
        }
    }
}
