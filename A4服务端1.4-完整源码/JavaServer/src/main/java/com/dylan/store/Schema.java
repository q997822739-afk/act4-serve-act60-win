package com.dylan.store;

final class Schema {

    private Schema() {
    }

    static final String[] STATEMENTS = concat(
            AccountSchema.TABLES,
            CharacterSchema.TABLES,
            InventorySchema.TABLES,
            SkillSchema.TABLES,
            PvpSchema.TABLES,
            QuestSchema.TABLES,
            DungeonClearSchema.TABLES,
            DeathTowerSchema.TABLES,
            GrowthTableSchema.TABLES,
            DailyGrantSchema.TABLES,
            ElixirSchema.TABLES,
            ContinuousItemSchema.TABLES,
            ExpertJobSchema.TABLES,
            GameOptionSchema.TABLES,
            AvatarSchema.TABLES,
            MailSchema.TABLES,
            CreatureSchema.TABLES,
            PremiumSchema.TABLES,
            GmSchema.TABLES,
            SecuritySchema.TABLES,
            EventSchema.TABLES,
            ItemLockSchema.TABLES,
            SlangSchema.TABLES,
            GuildSchema.TABLES,
            MentorSchema.TABLES,
            FriendSchema.TABLES);

    static final String[] INDEXES = concat(
            AccountSchema.INDEXES,
            CharacterSchema.INDEXES,
            AvatarSchema.INDEXES,
            CreatureSchema.INDEXES,
            MailSchema.INDEXES,
            PremiumSchema.INDEXES,
            GmSchema.INDEXES,
            SecuritySchema.INDEXES,
            EventSchema.INDEXES);

    private static String[] concat(String[]... parts) {
        int n = 0;
        for (String[] part : parts) {
            n += part.length;
        }
        String[] out = new String[n];
        int at = 0;
        for (String[] part : parts) {
            System.arraycopy(part, 0, out, at, part.length);
            at += part.length;
        }
        return out;
    }
}
