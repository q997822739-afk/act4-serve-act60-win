package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class CeraStore extends DomainStore {

    CeraStore(DataSource dataSource) {
        super(dataSource);
    }

    public long cera(long mId) {
        String sql = "SELECT cera FROM member_info WHERE m_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, mId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new StoreException("读账号 " + mId + " 的点券失败：" + e.getMessage(), e);
        }
    }

    public boolean setCera(long mId, long value) {
        String sql = "UPDATE member_info SET cera = ? WHERE m_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, Math.clamp(value, 0, 0xFFFF_FFFFL));
            ps.setLong(2, mId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StoreException("写账号 " + mId + " 的点券失败：" + e.getMessage(), e);
        }
    }

    public boolean spendCera(long mId, long amount) {
        if (amount <= 0) {
            return true;
        }
        String sql = "UPDATE member_info SET cera = cera - ? WHERE m_id = ? AND cera >= ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, amount);
            ps.setLong(2, mId);
            ps.setLong(3, amount);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StoreException("扣账号 " + mId + " 的 " + amount + " 点券失败："
                    + e.getMessage(), e);
        }
    }

    public long grantCera(long mId, long amount) {
        if (amount <= 0) {
            return cera(mId);
        }
        String sql = "UPDATE member_info SET cera = LEAST(cera + ?, 4294967295) WHERE m_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, amount);
            ps.setLong(2, mId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("给账号 " + mId + " 加 " + amount + " 点券失败："
                    + e.getMessage(), e);
        }
        return cera(mId);
    }
}
