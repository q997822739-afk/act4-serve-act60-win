package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public final class SecurityStore extends DomainStore {

    SecurityStore(DataSource dataSource) {
        super(dataSource);
    }

    public void logHack(long mId, int characNo, int hackType, int parameter1, int parameter2,
                        int cnt, String detail, String clientIp) {
        String sql = "INSERT INTO hack_log"
                + " (m_id, charac_no, hack_type, parameter1, parameter2, cnt, detail, client_ip)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, mId);
            ps.setInt(2, characNo);
            ps.setInt(3, Math.clamp(hackType, 0, 0xFFFF));
            ps.setInt(4, parameter1);
            ps.setInt(5, parameter2);
            ps.setInt(6, Math.max(0, cnt));
            ps.setString(7, fit(detail, 255));
            ps.setString(8, fit(clientIp, 45));
            ps.executeUpdate();
        } catch (SQLException e) {
        }
    }

    public record ItemHistoryRow(
            int characNo,
            String characName,
            int reason,
            int invenType,
            int slotNo,
            long itemId,
            long count,
            long addInfo,
            int endurance,
            int upgrade,
            long enchant,
            int amplifyOption,
            int amplifyValue,
            int sealCount,
            String randomOption,
            String guid,
            int itemXPos,
            int itemYPos,
            long goldAfter,
            int coinCol,
            long coinAfter) {
    }

    static final String[] ITEM_HISTORY_COLUMNS = {
            "charac_no", "charac_name", "reason", "inven_type", "slot_no", "item_id",
            "item_count", "add_info", "endurance", "upgrade", "enchant", "amplify_option",
            "amplify_value", "seal_cnt", "random_option", "guid", "item_x_pos", "item_y_pos",
            "gold_after", "coin_col", "coin_after",
    };

    static final String ITEM_HISTORY_INSERT = "INSERT INTO item_history ("
            + String.join(", ", ITEM_HISTORY_COLUMNS) + ") VALUES ("
            + String.join(", ", java.util.Collections.nCopies(ITEM_HISTORY_COLUMNS.length, "?"))
            + ")";

    public int logItemHistory(List<ItemHistoryRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        String sql = ITEM_HISTORY_INSERT;
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (ItemHistoryRow row : rows) {
                ps.setInt(1, row.characNo());
                ps.setString(2, fit(row.characName(), 64));
                ps.setInt(3, Math.clamp(row.reason(), 0, 0xFFFF));
                ps.setInt(4, Math.clamp(row.invenType(), 0, 0xFF));
                ps.setInt(5, Math.clamp(row.slotNo(), Short.MIN_VALUE, Short.MAX_VALUE));
                ps.setLong(6, Math.clamp(row.itemId(), 0, 0xFFFFFFFFL));
                ps.setLong(7, Math.clamp(row.count(), Integer.MIN_VALUE, Integer.MAX_VALUE));
                ps.setLong(8, Math.clamp(row.addInfo(), 0, 0xFFFFFFFFL));
                ps.setInt(9, Math.clamp(row.endurance(), 0, 0xFFFF));
                ps.setInt(10, Math.clamp(row.upgrade(), 0, 0xFF));
                ps.setLong(11, Math.clamp(row.enchant(), 0, 0xFFFFFFFFL));
                ps.setInt(12, Math.clamp(row.amplifyOption(), 0, 0xFF));
                ps.setInt(13, Math.clamp(row.amplifyValue(), 0, 0xFFFF));
                ps.setInt(14, Math.clamp(row.sealCount(), 0, 0xFF));
                ps.setString(15, fit(row.randomOption(), 32));
                ps.setString(16, fit(row.guid(), 32));
                ps.setInt(17, Math.clamp(row.itemXPos(), 0, 0xFFFF));
                ps.setInt(18, Math.clamp(row.itemYPos(), 0, 0xFFFF));
                ps.setLong(19, Math.max(0, row.goldAfter()));
                ps.setInt(20, Math.clamp(row.coinCol(), 0, 0xFF));
                ps.setLong(21, Math.clamp(row.coinAfter(), 0, 0xFFFFFFFFL));
                ps.addBatch();
            }
            int[] done = ps.executeBatch();
            return done.length;
        } catch (SQLException e) {
            return 0;
        }
    }
}
