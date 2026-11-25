package com.dylan.store;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class QuestStore extends DomainStore {

    QuestStore(DataSource dataSource) {
        super(dataSource);
    }

    private static final int QUEST_SLOTS = CharacterQuests.SLOTS;

    public Optional<CharacterQuests> loadQuests(int characNo) {
        StringBuilder sql = new StringBuilder("SELECT clear_quest, urgentQuestIndex");
        for (int i = 1; i <= QUEST_SLOTS; i++) {
            sql.append(", play_").append(i).append(", play_").append(i).append("_trigger");
        }
        sql.append(" FROM new_charac_quest WHERE charac_no = ?");
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            ps.setInt(1, characNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                int[] ids = new int[QUEST_SLOTS];
                long[] triggers = new long[QUEST_SLOTS];
                for (int i = 0; i < QUEST_SLOTS; i++) {
                    ids[i] = rs.getInt("play_" + (i + 1));
                    triggers[i] = rs.getLong("play_" + (i + 1) + "_trigger");
                }
                return Optional.of(CharacterQuests.of(characNo,
                        QuestClearFlags.decode(rs.getBytes("clear_quest")),
                        ids, triggers, rs.getInt("urgentQuestIndex")));
            }
        } catch (SQLException e) {
            throw new StoreException("读 charac_no=" + characNo + " 的任务失败：" + e.getMessage(), e);
        }
    }

    public void saveQuests(CharacterQuests quests) {
        boolean withClearFlags = quests.cleared().dirty();
        StringBuilder sql = new StringBuilder("UPDATE new_charac_quest SET ");
        if (withClearFlags) {
            sql.append("clear_quest = ?, ");
        }
        sql.append("urgentQuestIndex = ?");
        for (int i = 1; i <= QUEST_SLOTS; i++) {
            sql.append(", play_").append(i).append(" = ?")
                    .append(", play_").append(i).append("_trigger = ?");
        }
        sql.append(" WHERE charac_no = ?");
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int at = 1;
            if (withClearFlags) {
                ps.setBytes(at++, quests.cleared().encode());
            }
            ps.setInt(at++, quests.urgentQuestIndex());
            for (int i = 0; i < QUEST_SLOTS; i++) {
                ps.setInt(at++, quests.questIdAt(i));
                ps.setLong(at++, quests.triggerAt(i));
            }
            ps.setInt(at, quests.characNo());
            if (ps.executeUpdate() != 1) {
                throw new StoreException("存任务时 charac_no=" + quests.characNo() + " 没有对应的行");
            }
            quests.cleared().clearDirty();
        } catch (SQLException e) {
            throw new StoreException("存 charac_no=" + quests.characNo() + " 的任务失败："
                    + e.getMessage(), e);
        }
    }

    public static void insertQuest(Connection c, int characNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO new_charac_quest (charac_no, clear_quest, quest_notify) "
                        + "VALUES (?, '', '')")) {
            ps.setInt(1, characNo);
            ps.executeUpdate();
        }
    }
}
