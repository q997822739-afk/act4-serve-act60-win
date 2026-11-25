package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

public final class AccountStore extends DomainStore {

    AccountStore(DataSource dataSource) {
        super(dataSource);
    }

    public static final int DEFAULT_CHARACTER_SLOTS = 16;

    /**
     * 消费网关发的一次性登录令牌（商业登录器 2026-11-02）。
     * 表由网关建：gw_login_token(token, user_id, m_id, expire_at, client_ip, created_at)。
     * 命中就删掉，同一个令牌只能进一次游戏。
     */
    public boolean consumeLoginToken(String userId, String token) {
        String sql = "DELETE FROM gw_login_token WHERE user_id = ? AND token = ? AND expire_at >= ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, token);
            ps.setLong(3, System.currentTimeMillis() / 1000L);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            // 网关没起来过 / 库是老的没有这张表，都当令牌无效
            return false;
        }
    }

    public Optional<AccountRow> findAccount(String userId) {
        String sql = "SELECT m_id, user_id, slot, reg_date FROM member_info WHERE user_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new AccountRow(
                        rs.getLong("m_id"), rs.getString("user_id"),
                        rs.getInt("slot"), rs.getLong("reg_date")));
            }
        } catch (SQLException e) {
            throw new StoreException("查账号 " + userId + " 失败：" + e.getMessage(), e);
        }
    }

    public long createAccount(String userId, String password) {
        return createAccount(userId, password, userId);
    }

    public AccountRow findOrCreateAccount(String userId, String password) {
        return findAccount(userId).orElseGet(() -> {
            long mId = createAccount(userId, password);
            return findAccount(userId).orElseThrow(
                    () -> new StoreException("刚建的账号 " + userId + " 查不回来（m_id=" + mId + "）"));
        });
    }

    public static final int ACCOUNT_STATE_NORMAL = 1;

    public record Credential(AccountRow account, String password, int state) {

        @Override
        public String toString() {
            return "Credential[" + account.userId() + ", state=" + state + ", passwd=***]";
        }
    }

    public Optional<Credential> findCredential(String userId) {
        String sql = "SELECT m_id, user_id, slot, reg_date, passwd, state "
                + "FROM member_info WHERE user_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                AccountRow row = new AccountRow(
                        rs.getLong("m_id"), rs.getString("user_id"),
                        rs.getInt("slot"), rs.getLong("reg_date"));
                return Optional.of(new Credential(
                        row, rs.getString("passwd"), rs.getInt("state")));
            }
        } catch (SQLException e) {
            throw new StoreException("查账号 " + userId + " 的凭据失败：" + e.getMessage(), e);
        }
    }

    public void touchLastPlayTime(String userId) {
        String sql = "UPDATE member_info SET last_play_time = datetime('now','localtime') WHERE user_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public boolean tutorialSkipable(long mId) {
        String sql = "SELECT tutorial_skipable FROM member_login WHERE m_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, mId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                String value = rs.getString(1);
                return value != null && !value.isEmpty() && value.charAt(0) != '0';
            }
        } catch (SQLException e) {
            throw new StoreException("查 m_id=" + mId + " 的 tutorial_skipable 失败："
                    + e.getMessage(), e);
        }
    }

    public void markTutorialSkipable(long mId) {
        String sql = "INSERT INTO member_login (m_id, tutorial_skipable) VALUES (?, '1')"
                + " ON CONFLICT(m_id) DO UPDATE SET tutorial_skipable = '1'";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, mId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("写 m_id=" + mId + " 的 tutorial_skipable 失败："
                    + e.getMessage(), e);
        }
    }

    public long createAccount(String userId, String password, String nickname) {
        String sql = "INSERT INTO member_info (user_id, user_name, passwd, nickname, slot, reg_date) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, userId);
            ps.setString(2, userId);
            ps.setString(3, password);
            ps.setString(4, (nickname == null || nickname.isBlank()) ? userId : nickname);
            ps.setInt(5, DEFAULT_CHARACTER_SLOTS);
            ps.setLong(6, System.currentTimeMillis() / 1000L);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new StoreException("建账号 " + userId + " 之后拿不到 m_id");
                }
                long mId = keys.getLong(1);
                return mId;
            }
        } catch (SQLException e) {
            throw new StoreException("建账号 " + userId + " 失败：" + e.getMessage(), e);
        }
    }

    public static final int GIFT_RECV_COUNT_UNKNOWN = -1;

    public int giftRecvCount(long mId) {
        String select = "SELECT last_gift_time, gift_cnt FROM member_login WHERE m_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(select)) {
            ps.setLong(1, mId);
            long lastGiftTime;
            int giftCount;
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return GIFT_RECV_COUNT_UNKNOWN;
                }
                lastGiftTime = rs.getLong(1);
                giftCount = rs.getInt(2);
            }
            if (giftCount != 0 && monthOf(lastGiftTime) != monthOf(nowSeconds())) {
                try (PreparedStatement up = c.prepareStatement(
                        "UPDATE member_login SET last_gift_time = ?, gift_cnt = 0"
                                + " WHERE m_id = ?")) {
                    up.setLong(1, nowSeconds());
                    up.setLong(2, mId);
                    up.executeUpdate();
                }
                return 0;
            }
            return giftCount;
        } catch (SQLException e) {
            throw new StoreException("查 m_id=" + mId + " 的礼物次数失败：" + e.getMessage(), e);
        }
    }

    private static long nowSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    private static int monthOf(long epochSeconds) {
        return java.time.Instant.ofEpochSecond(epochSeconds)
                .atZone(java.time.ZoneId.systemDefault())
                .getMonthValue();
    }
}
