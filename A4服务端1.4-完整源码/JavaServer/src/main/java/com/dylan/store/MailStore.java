package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MailStore extends DomainStore {

    MailStore(DataSource dataSource) {
        super(dataSource);
    }

    private static final String MAIL_COLUMNS =
            "postal_id, CAST(strftime('%s', occ_time, 'utc') AS INTEGER), send_charac_no, seal_flag, item_id, add_info, "
            + "endurance, upgrade, gold, send_charac_name, unlimit_flag, avata_flag, "
            + "creature_flag, letter_id, amplify_option, amplify_value, extend_info";

    public List<MailRow> loadMails(int characNo, long afterPostalId, int limit) {
        String sql = "SELECT " + MAIL_COLUMNS + " FROM postal "
                + "WHERE receive_charac_no = ? AND delete_flag = 0 "
                + (afterPostalId > 0 ? "AND postal_id > ? " : "")
                + "AND (unlimit_flag = 1 OR occ_time > datetime('now','localtime','-'||?||' seconds')) "
                + "ORDER BY postal_id LIMIT ?";
        List<MailRow> out = new ArrayList<>();
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int at = 1;
            ps.setInt(at++, characNo);
            if (afterPostalId > 0) {
                ps.setLong(at++, afterPostalId);
            }
            ps.setInt(at++, MailRow.KEEP_SECONDS);
            ps.setInt(at, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new MailRow(
                            rs.getLong(1),
                            Instant.ofEpochSecond(rs.getLong(2)),
                            rs.getInt(3),
                            rs.getString(10),
                            rs.getLong(5),
                            rs.getLong(6),
                            rs.getInt(7),
                            rs.getInt(8),
                            rs.getInt(15),
                            rs.getInt(16),
                            rs.getLong(17),
                            rs.getLong(9),
                            rs.getInt(4) == 1,
                            rs.getInt(12) == 1,
                            rs.getInt(11) == 1,
                            rs.getInt(14)));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new StoreException("读 charac_no=" + characNo + " 的邮件失败：" + e.getMessage(), e);
        }
    }

    /**
     * 这个角色还没领、也没过期的邮件**总数**（口径与 {@link #loadMails} 的 WHERE 完全一致）。
     *
     * 为什么要单独数一下：{@code loadMails} 一次只取一页（LIMIT PAGE_SIZE），光看"这次拿回来几行"
     * 数不出"还有多少封没加载"。在线收信（邮件是外部进程插进来的）要把"还有 N 封"报准，
     * 客户端才愿意继续翻页。
     */
    public int countMails(int characNo) {
        String sql = "SELECT COUNT(*) FROM postal "
                + "WHERE receive_charac_no = ? AND delete_flag = 0 "
                + "AND (unlimit_flag = 1 OR occ_time > datetime('now','localtime','-'||?||' seconds'))";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            ps.setInt(2, MailRow.KEEP_SECONDS);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new StoreException("数 charac_no=" + characNo + " 的邮件失败：" + e.getMessage(), e);
        }
    }

    public int expireMails(int characNo) {
        String sql = "UPDATE postal SET delete_flag = ? WHERE receive_charac_no = ? "
                + "AND unlimit_flag = 0 AND occ_time < datetime('now','localtime','-'||?||' seconds') "
                + "AND delete_flag = 0";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, MailRow.DELETE_FLAG_EXPIRED);
            ps.setInt(2, characNo);
            ps.setInt(3, MailRow.KEEP_SECONDS);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("清 charac_no=" + characNo + " 的过期邮件失败："
                    + e.getMessage(), e);
        }
    }

    public boolean removeMail(long postalId, int deleteFlag) {
        String sql = "UPDATE postal SET delete_flag = ?, receive_time = datetime('now','localtime') WHERE postal_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, deleteFlag);
            ps.setLong(2, postalId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StoreException("删邮件 " + postalId + " 失败：" + e.getMessage(), e);
        }
    }

    public long insertMail(int senderCharacNo, String senderName, int receiverCharacNo,
                           Instant occTime, long itemId, long addInfo, int endurance,
                           int upgrade, int amplifyOption, int amplifyValue, long enchant,
                           long gold, boolean sealed, boolean avatar, boolean unlimited,
                           long letterId) {
        String sql = "INSERT INTO postal (occ_time, send_charac_no, receive_charac_no, seal_flag, "
                + "item_id, add_info, endurance, upgrade, amplify_option, amplify_value, gold, "
                + "send_charac_name, avata_flag, letter_id, unlimit_flag, extend_info) "
                + "VALUES (datetime(?,'unixepoch','localtime'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int at = 1;
            ps.setLong(at++, occTime.getEpochSecond());
            ps.setInt(at++, senderCharacNo);
            ps.setInt(at++, receiverCharacNo);
            ps.setInt(at++, sealed ? 1 : 0);
            ps.setLong(at++, itemId);
            ps.setLong(at++, addInfo);
            ps.setInt(at++, endurance);
            ps.setInt(at++, upgrade);
            ps.setInt(at++, amplifyOption);
            ps.setInt(at++, amplifyValue);
            ps.setLong(at++, gold);
            ps.setString(at++, senderName);
            ps.setInt(at++, avatar ? 1 : 0);
            ps.setLong(at++, letterId);
            ps.setInt(at++, unlimited ? 1 : 0);
            ps.setLong(at, enchant);
            if (ps.executeUpdate() != 1) {
                throw new StoreException("给 charac_no=" + receiverCharacNo + " 寄邮件时没有影响任何行");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new StoreException("给 charac_no=" + receiverCharacNo + " 寄邮件失败："
                    + e.getMessage(), e);
        }
    }

    public List<LetterRow> loadLetters(int characNo) {
        String sql = "SELECT letter_id, send_charac_no, send_charac_name, letter_text, "
                + "CAST(strftime('%s', reg_date, 'utc') AS INTEGER), stat FROM letter "
                + "WHERE (charac_no = ? AND stat = ?) "
                + "   OR (charac_no = ? AND reg_date > datetime('now','localtime','-'||?||' seconds')) "
                + "ORDER BY letter_id";
        List<LetterRow> out = new ArrayList<>();
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            ps.setInt(2, com.dylan.protocol.body.LetterStat.KEPT);
            ps.setInt(3, characNo);
            ps.setInt(4, LetterRow.KEEP_SECONDS);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new LetterRow(rs.getLong(1), characNo, rs.getInt(2), rs.getString(3),
                            rs.getString(4), Instant.ofEpochSecond(rs.getLong(5)), rs.getInt(6)));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new StoreException("读 charac_no=" + characNo + " 的信件失败：" + e.getMessage(), e);
        }
    }

    public int purgeLetters(int characNo) {
        String sql = "DELETE FROM letter WHERE charac_no = ? "
                + "AND reg_date < datetime('now','localtime','-'||?||' seconds') AND stat IN (?, ?)";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            ps.setInt(2, LetterRow.KEEP_SECONDS);
            ps.setInt(3, com.dylan.protocol.body.LetterStat.UNREAD);
            ps.setInt(4, com.dylan.protocol.body.LetterStat.READ);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("清 charac_no=" + characNo + " 的过期信件失败："
                    + e.getMessage(), e);
        }
    }

    public long insertLetter(int characNo, int senderCharacNo, String senderName, String text,
                             Instant regDate, int stat) {
        String sql = "INSERT INTO letter (charac_no, send_charac_no, send_charac_name, "
                + "letter_text, reg_date, stat) VALUES (?, ?, ?, ?, datetime(?,'unixepoch','localtime'), ?)";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, characNo);
            ps.setInt(2, senderCharacNo);
            ps.setString(3, senderName);
            ps.setString(4, text);
            ps.setLong(5, regDate.getEpochSecond());
            ps.setInt(6, stat);
            if (ps.executeUpdate() != 1) {
                throw new StoreException("给 charac_no=" + characNo + " 写信件时没有影响任何行");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new StoreException("给 charac_no=" + characNo + " 写信件失败：" + e.getMessage(), e);
        }
    }

    public boolean changeLetterStat(long letterId, int stat) {
        if (stat == com.dylan.protocol.body.LetterStat.DELETE) {
            String sql = "DELETE FROM letter WHERE letter_id = ?";
            try (Connection c = connection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, letterId);
                return ps.executeUpdate() == 1;
            } catch (SQLException e) {
                throw new StoreException("删信件 " + letterId + " 失败：" + e.getMessage(), e);
            }
        }
        String sql = "UPDATE letter SET stat = ? WHERE letter_id = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, stat);
            ps.setLong(2, letterId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StoreException("改信件 " + letterId + " 的状态失败：" + e.getMessage(), e);
        }
    }

    public Optional<Receiver> findReceiver(String characName) {
        String sql = "SELECT charac_no, lev, delete_flag, m_id FROM charac_info "
                + "WHERE charac_name = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, characName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Receiver(rs.getInt(1), rs.getInt(2),
                        rs.getInt(3) != 0, rs.getLong(4)));
            }
        } catch (SQLException e) {
            throw new StoreException("查收件人 " + characName + " 失败：" + e.getMessage(), e);
        }
    }

    public record Receiver(int characNo, int level, boolean deleted, long mId) {
    }
}
