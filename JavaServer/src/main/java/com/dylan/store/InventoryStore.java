package com.dylan.store;

import com.dylan.store.blob.ItemArray;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class InventoryStore extends DomainStore {

    InventoryStore(DataSource dataSource) {
        super(dataSource);
    }

    public static void insertInventory(Connection c, int characNo, int coin) throws SQLException {
        insertInventory(c, characNo, coin, null);
    }

    /** equipslot 为 null 时写空串（与原来一致）。 */
    public static void insertInventory(Connection c, int characNo, int coin, byte[] equipslot)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO inventory (charac_no, coin, inventory, equipslot, creature, katagaki) "
                        + "VALUES (?, ?, '', ?, '', '')")) {
            ps.setInt(1, characNo);
            ps.setInt(2, coin);
            if (equipslot == null) {
                ps.setString(3, "");
            } else {
                ps.setBytes(3, equipslot);
            }
            ps.executeUpdate();
        }
    }

    public static void insertInvenExpand(Connection c, int characNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO charac_inven_expand (charac_no, cargo, cargo_capacity, jewel, "
                        + "switch_equipslot, expand_equipslot, redeem_info) "
                        + "VALUES (?, '', 0, '', '', '', '')")) {
            ps.setInt(1, characNo);
            ps.executeUpdate();
        }
    }

    public Optional<CharacterInventory> loadInventory(int characNo) {
        String sql = "SELECT money, coin, pay_coin, event_coin, "
                + "equipslot, inventory, creature FROM inventory WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new CharacterInventory(
                        characNo,
                        rs.getLong("money"),
                        rs.getLong("coin"),
                        rs.getLong("pay_coin"),
                        rs.getLong("event_coin"),
                        ItemArray.decodeTolerant(rs.getBytes("equipslot"),
                                ItemArray.EQUIPSLOT_SLOTS),
                        ItemArray.decode(rs.getBytes("inventory"), ItemArray.INVENTORY_SLOTS),
                        ItemArray.decodeTolerant(rs.getBytes("creature"),
                                ItemArray.CREATURE_SLOTS)));
            }
        } catch (SQLException e) {
            throw new StoreException("读 charac_no=" + characNo + " 的背包失败：" + e.getMessage(), e);
        }
    }

    public void saveInventory(CharacterInventory inv) {
        String sql = "UPDATE inventory SET money = ?, coin = ?, pay_coin = ?, event_coin = ?, "
                + "equipslot = ?, inventory = ?, creature = ? WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, inv.money());
            ps.setLong(2, inv.coin());
            ps.setLong(3, inv.payCoin());
            ps.setLong(4, inv.eventCoin());
            ps.setBytes(5, inv.equipslot().encode());
            ps.setBytes(6, inv.inventory().encode());
            ps.setBytes(7, inv.creature().encode());
            ps.setInt(8, inv.characNo());
            if (ps.executeUpdate() != 1) {
                throw new StoreException("存背包时 charac_no=" + inv.characNo() + " 没有对应的行");
            }
        } catch (SQLException e) {
            throw new StoreException("存 charac_no=" + inv.characNo() + " 的背包失败：" + e.getMessage(), e);
        }
    }

    public Optional<CharacterCargo> loadCargo(int characNo) {
        String sql = "SELECT cargo_capacity, cargo FROM charac_inven_expand WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new CharacterCargo(characNo, rs.getInt(1),
                        ItemArray.decodeTolerant(rs.getBytes(2), ItemArray.CARGO_SLOTS)));
            }
        } catch (SQLException e) {
            throw new StoreException("读 charac_no=" + characNo + " 的金库失败："
                    + e.getMessage(), e);
        }
    }

    public void saveCargo(CharacterCargo cargo) {
        String sql = "UPDATE charac_inven_expand SET cargo_capacity = ?, cargo = ? "
                + "WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, cargo.capacity());
            ps.setBytes(2, cargo.items().encode());
            ps.setInt(3, cargo.characNo());
            if (ps.executeUpdate() != 1) {
                throw new StoreException("存金库时 charac_no=" + cargo.characNo()
                        + " 在 charac_inven_expand 里没有对应的行");
            }
        } catch (SQLException e) {
            throw new StoreException("存 charac_no=" + cargo.characNo() + " 的金库失败："
                    + e.getMessage(), e);
        }
    }

    public void saveEquipslot(int characNo, ItemArray equipslot) {
        updateSingleBlob(characNo, "equipslot", equipslot.encode());
    }

    public void saveInventoryItems(int characNo, ItemArray inventory) {
        updateSingleBlob(characNo, "inventory", inventory.encode());
    }

    public void saveCreatureItems(int characNo, ItemArray creature) {
        updateSingleBlob(characNo, "creature", creature.encode());
    }

    private void updateSingleBlob(int characNo, String column, byte[] blob) {
        String sql = "UPDATE inventory SET " + column + " = ? WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBytes(1, blob);
            ps.setInt(2, characNo);
            if (ps.executeUpdate() != 1) {
                throw new StoreException("存 " + column + " 时 charac_no=" + characNo + " 没有对应的行");
            }
        } catch (SQLException e) {
            throw new StoreException("存 charac_no=" + characNo + " 的 " + column
                    + " 失败：" + e.getMessage(), e);
        }
    }
}
