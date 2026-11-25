package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CharacterStore extends DomainStore {
    CharacterStore(DataSource dataSource) {
        super(dataSource);
    }

    private static final int NEW_CHARACTER_HP = StaminaStore.FULL;

    public static final int DEFAULT_COIN_ON_CREATE = 5;

    public Optional<Long> characterCreateTime(int characNo) {
        String sql = "SELECT CAST(strftime('%s', create_time, 'utc') AS INTEGER) FROM charac_info WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                long secs = rs.getLong(1);
                return Optional.of(rs.wasNull() || secs < 0 ? 0L : secs);
            }
        } catch (SQLException e) {
            throw new StoreException("查 charac_no=" + characNo + " 的创建时间失败："
                    + e.getMessage(), e);
        }
    }

    public void saveCharacterVillage(int characNo, int village) {
        String sql = "UPDATE charac_info SET village = ? WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, village);
            ps.setInt(2, characNo);
            if (ps.executeUpdate() == 0) {
                throw new StoreException("写城镇号失败：charac_info 里没有 charac_no="
                        + characNo + " 那一行");
            }
        } catch (SQLException e) {
            throw new StoreException("写 charac_no=" + characNo + " 的城镇号失败："
                    + e.getMessage(), e);
        }
    }

    public Optional<Integer> characterVillage(int characNo) {
        String sql = "SELECT village FROM charac_info WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getInt(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("查 charac_no=" + characNo + " 的城镇号失败："
                    + e.getMessage(), e);
        }
    }

    public Optional<LevelAndExp> characterLevelAndExp(int characNo) {
        String sql = "SELECT lev, exp FROM charac_info WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new LevelAndExp(rs.getInt(1),
                        Integer.toUnsignedLong(rs.getInt(2))));
            }
        } catch (SQLException e) {
            throw new StoreException("查 charac_no=" + characNo + " 的等级和经验失败："
                    + e.getMessage(), e);
        }
    }

    public record LevelAndExp(int level, long exp) {}

    public boolean saveExperience(int characNo, int level, long totalExp) {
        try (Connection c = connection()) {
            int updated;
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE charac_info SET lev = ?, exp = ? WHERE charac_no = ?")) {
                ps.setInt(1, level);
                ps.setInt(2, (int) totalExp);
                ps.setInt(3, characNo);
                updated = ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE charac_stat SET exp = ? WHERE charac_no = ?")) {
                ps.setInt(1, (int) totalExp);
                ps.setInt(2, characNo);
                ps.executeUpdate();
            }
            return updated > 0;
        } catch (SQLException e) {
            throw new StoreException("写 charac_no=" + characNo + " 的等级和经验失败："
                    + e.getMessage(), e);
        }
    }

    public record TutorialFlagRow(int lowFlag, byte[] highBytes) {
    }

    public Optional<TutorialFlagRow> characterTutorialFlag(int characNo) {
        String sql = "SELECT tutorial_flag, escalade_tutorial_flag"
                + " FROM charac_stat WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new TutorialFlagRow(rs.getInt(1), rs.getBytes(2)))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("查 charac_no=" + characNo + " 的教程位失败："
                    + e.getMessage(), e);
        }
    }

    public boolean saveTutorialFlag(int characNo, int lowFlag, byte[] highBytes) {
        String sql = "UPDATE charac_stat SET tutorial_flag = ?, escalade_tutorial_flag = ?"
                + " WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, lowFlag);
            ps.setBytes(2, highBytes);
            ps.setInt(3, characNo);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new StoreException("写 charac_no=" + characNo + " 的教程位失败："
                    + e.getMessage(), e);
        }
    }

    public Optional<FatigueRow> loadFatigue(int characNo) {
        String sql = "SELECT fatigue, used_fatigue, premium_fatigue, fatigue_grownup_buff,"
                + " last_play_time FROM charac_stat WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Timestamp last = rs.getTimestamp(5);
                return Optional.of(new FatigueRow(
                        rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                        last == null ? Instant.EPOCH : last.toInstant()));
            }
        } catch (SQLException e) {
            throw new StoreException("查 charac_no=" + characNo + " 的疲劳失败："
                    + e.getMessage(), e);
        }
    }

    public boolean saveFatigue(int characNo, FatigueRow row) {
        String sql = "UPDATE charac_stat SET fatigue = ?, used_fatigue = ?, premium_fatigue = ?,"
                + " fatigue_grownup_buff = ?, last_play_time = ? WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, row.fatigue());
            ps.setInt(2, row.usedFatigue());
            ps.setInt(3, row.premiumFatigue());
            ps.setInt(4, row.grownUpBuff());
            ps.setTimestamp(5, Timestamp.from(row.lastPlayTime()));
            ps.setInt(6, characNo);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new StoreException("写 charac_no=" + characNo + " 的疲劳失败："
                    + e.getMessage(), e);
        }
    }

    public List<CharacterRow> charactersOf(long mId, int slotLimit) {
        String sql = "SELECT charac_no, charac_name, job, lev, grow_type, sex, guild_id "
                + "FROM charac_info WHERE m_id = ? AND delete_flag != 1 "
                + "ORDER BY charac_no ASC LIMIT ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, mId);
            ps.setInt(2, slotLimit);
            try (ResultSet rs = ps.executeQuery()) {
                List<CharacterRow> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new CharacterRow(
                            rs.getInt("charac_no"),
                            rs.getString("charac_name"),
                            rs.getByte("job"),
                            rs.getByte("lev"),
                            rs.getByte("grow_type"),
                            rs.getByte("sex"),
                            rs.getLong("guild_id")));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new StoreException("查 m_id=" + mId + " 的角色列表失败：" + e.getMessage(), e);
        }
    }

    public int countCharacters(long mId) {
        String sql = "SELECT COUNT(*) FROM charac_info WHERE m_id = ? AND delete_flag != 1";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, mId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new StoreException("数 m_id=" + mId + " 的角色数时没有结果行");
                }
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new StoreException("数 m_id=" + mId + " 的角色数失败：" + e.getMessage(), e);
        }
    }

    public boolean nameHeldByDeletedCharacter(String name) {
        String sql = "SELECT delete_flag FROM charac_info WHERE charac_name = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) != 0;
            }
        } catch (SQLException e) {
            throw new StoreException("查角色名 " + name + " 的删除状态失败：" + e.getMessage(), e);
        }
    }

    public int characterCount(long mId) {
        String sql = "SELECT COUNT(*) FROM charac_info WHERE m_id = ? AND delete_flag = 0";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, mId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new StoreException("数 m_id=" + mId + " 的角色数失败：" + e.getMessage(), e);
        }
    }

    public int createCharacter(long mId, String name, int job, int sex) {
        return createCharacter(mId, name, job, sex, null);
    }

    public int createCharacter(long mId, String name, int job, int sex, byte[] skillSlot) {
        return createCharacter(mId, name, job, sex, skillSlot, DEFAULT_COIN_ON_CREATE);
    }

    public int createCharacter(long mId, String name, int job, int sex, byte[] skillSlot,
                               int coinOnCreate) {
        return createCharacter(mId, name, job, sex, skillSlot, coinOnCreate, null);
    }

    /** equipslot 为 null 时装备栏留空（ACT5 原样）；A4 建角带初始武器时传编码好的装备栏。 */
    public int createCharacter(long mId, String name, int job, int sex, byte[] skillSlot,
                               int coinOnCreate, byte[] equipslot) {
        int coin = Math.max(0, coinOnCreate);
        try (Connection c = connection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                int characNo = insertCharacInfo(c, mId, name, job, sex);
                insertCharacStat(c, characNo);
                InventoryStore.insertInventory(c, characNo, coin, equipslot);
                InventoryStore.insertInvenExpand(c, characNo);
                SkillStore.insertSkill(c, characNo, skillSlot);
                PvpStore.insertPvp(c, characNo);
                QuestStore.insertQuest(c, characNo);
                DungeonClearStore.insertMemberDungeon(c, mId);
                insertCharacNpc(c, characNo);
                c.commit();
                return characNo;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            if (isUniqueViolation(e)) {
                throw new DuplicateNameException(name);
            }
            throw new StoreException("建角色 " + name + " 失败：" + e.getMessage(), e);
        }
    }

    public boolean deleteCharacter(long mId, int characNo) {
        String sql = "UPDATE charac_info SET delete_flag = 1, delete_time = datetime('now','localtime') "
                + "WHERE charac_no = ? AND m_id = ? AND delete_flag = 0";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            ps.setLong(2, mId);
            boolean deleted = ps.executeUpdate() == 1;
            return deleted;
        } catch (SQLException e) {
            throw new StoreException("删角色 " + characNo + " 失败：" + e.getMessage(), e);
        }
    }

    private int insertCharacInfo(Connection c, long mId, String name, int job, int sex)
            throws SQLException {
        String sql = "INSERT INTO charac_info "
                + "(m_id, charac_name, job, sex, element_resist, spec_property, create_time) "
                + "VALUES (?, ?, ?, ?, '', '', datetime('now','localtime'))";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, mId);
            ps.setString(2, name);
            ps.setInt(3, job);
            ps.setInt(4, sex);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new StoreException("插入 charac_info 之后拿不到 charac_no");
                }
                return keys.getInt(1);
            }
        }
    }

    private void insertCharacStat(Connection c, int characNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO charac_stat (charac_no, HP, forbidden_to_play) VALUES (?, ?, '')")) {
            ps.setInt(1, characNo);
            ps.setInt(2, NEW_CHARACTER_HP);
            ps.executeUpdate();
        }
    }

    private void insertCharacNpc(Connection c, int characNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO charac_npc (charac_no, npc_cnt, npc_data) VALUES (?, 0, '')")) {
            ps.setInt(1, characNo);
            ps.executeUpdate();
        }
    }

    public Optional<Long> accountIdOf(int characNo) {
        String sql = "SELECT m_id FROM charac_info WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, characNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("查 charac_no=" + characNo + " 属于哪个账号失败："
                    + e.getMessage(), e);
        }
    }

    public boolean saveGrowType(int characNo, int growType) {
        String sql = "UPDATE charac_info SET grow_type = ? WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.clamp(growType, 0, 0xFF));
            ps.setInt(2, characNo);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StoreException("写 charac_no=" + characNo + " 的转职分支失败："
                    + e.getMessage(), e);
        }
    }

    public boolean saveLuckPoint(int characNo, long value) {
        String sql = "UPDATE charac_stat SET luck_point = ? WHERE charac_no = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, Math.clamp(value, 0, 0x7FFF_FFFFL));
            ps.setInt(2, characNo);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StoreException("写 charac_no=" + characNo + " 的幸运点失败："
                    + e.getMessage(), e);
        }
    }

    public record QueriedCharacter(long mId, int characNo, int job, int level, int growType,
                                   int sex) {
    }

    public Optional<QueriedCharacter> queryCharacterByName(String name) {
        String sql = "SELECT delete_flag, m_id, charac_no, job, lev, grow_type, sex"
                + " FROM charac_info WHERE charac_name = ?";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt(1) != 0) {
                    return Optional.empty();
                }
                return Optional.of(new QueriedCharacter(
                        rs.getLong(2), rs.getInt(3), rs.getInt(4),
                        rs.getInt(5), rs.getInt(6), rs.getInt(7)));
            }
        } catch (SQLException e) {
            throw new StoreException("按名字查角色「" + name + "」失败：" + e.getMessage(), e);
        }
    }
}
