package com.dylan.store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;

public final class StaminaStore extends DomainStore {

    public static final int FULL = 100;

    StaminaStore(DataSource dataSource) {
        super(dataSource);
    }

    public int loadStamina(int characNo) {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT HP FROM charac_stat WHERE charac_no = ?")) {
            ps.setInt(1, characNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return FULL;
                }
                int stored = rs.getInt(1);
                return stored == 0 ? FULL : stored;
            }
        } catch (SQLException e) {
            throw new StoreException("读体力失败：" + e.getMessage(), e);
        }
    }

    public void saveStamina(int characNo, int stamina) {
        setOneInt("UPDATE charac_stat SET HP = ? WHERE charac_no = ?", stamina, characNo,
                "写体力失败");
    }
}
