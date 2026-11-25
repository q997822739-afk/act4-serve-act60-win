package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class DomainStore {
    protected final DataSource dataSource;

    protected DomainStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    protected Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    protected int oneInt(String sql, int characNo, String what) {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new StoreException(what + "：" + e.getMessage(), e);
        }
    }

    protected void setOneInt(String sql, int value, int characNo, String what) {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, value);
            ps.setInt(2, characNo);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException(what + "：" + e.getMessage(), e);
        }
    }

    protected static boolean isUniqueViolation(SQLException e) {
        if (!(e instanceof org.sqlite.SQLiteException sqlite)) {
            return false;
        }
        return switch (sqlite.getResultCode()) {
            case SQLITE_CONSTRAINT_UNIQUE, SQLITE_CONSTRAINT_PRIMARYKEY -> true;
            default -> false;
        };
    }

    protected static String fit(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
