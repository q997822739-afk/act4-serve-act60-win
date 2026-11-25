package com.dylan.game;

import com.dylan.protocol.body.Noti29Body;
import com.dylan.pvf.script.MapScript;

public record RoomMonster(int spawnIndex, int seqId, int monsterId, int level, int type,
                          int x, int y, int randomDropCount, int specifyDropCount,
                          int hellGroupOrder, int hellGroupId, int named, int hellDifficulty,
                          int camp, boolean fakeBoss, int passiveObject) {

    /** 挂在物件上的怪（{@link MapScript.PassiveMonsterSpawn}）存物件顺序号；普通怪是这个值。 */
    public static final int NO_PASSIVE_OBJECT = -1;

    public RoomMonster(int spawnIndex, int seqId, int monsterId, int level, int type,
                       int x, int y, int randomDropCount, int specifyDropCount,
                       int hellGroupOrder, int hellGroupId, int named, int hellDifficulty,
                       int camp, boolean fakeBoss) {
        this(spawnIndex, seqId, monsterId, level, type, x, y, randomDropCount, specifyDropCount,
                hellGroupOrder, hellGroupId, named, hellDifficulty, camp, fakeBoss,
                NO_PASSIVE_OBJECT);
    }

    public RoomMonster(int spawnIndex, int seqId, int monsterId, int level, int type,
                       int x, int y, int randomDropCount, int specifyDropCount,
                       int hellGroupOrder, int hellGroupId, int named, int hellDifficulty,
                       int camp) {
        this(spawnIndex, seqId, monsterId, level, type, x, y, randomDropCount, specifyDropCount,
                hellGroupOrder, hellGroupId, named, hellDifficulty, camp, false);
    }

    public static final int TYPE_APC = 5;

    public static final int NAMED_HELL_PARTY = 1;

    public RoomMonster(int spawnIndex, int seqId, int monsterId, int level, int type,
                       int x, int y, int randomDropCount, int specifyDropCount) {
        this(spawnIndex, seqId, monsterId, level, type, x, y, randomDropCount, specifyDropCount,
                0, 0, 0, 0);
    }

    public RoomMonster(int spawnIndex, int seqId, int monsterId, int level, int type,
                       int x, int y, int randomDropCount, int specifyDropCount, int camp) {
        this(spawnIndex, seqId, monsterId, level, type, x, y, randomDropCount, specifyDropCount,
                camp, false);
    }

    public RoomMonster(int spawnIndex, int seqId, int monsterId, int level, int type,
                       int x, int y, int randomDropCount, int specifyDropCount, int camp,
                       boolean fakeBoss) {
        this(spawnIndex, seqId, monsterId, level, type, x, y, randomDropCount, specifyDropCount,
                0, 0, 0, 0, camp, fakeBoss);
    }

    public RoomMonster(int spawnIndex, int seqId, int monsterId, int level, int type,
                       int x, int y, int randomDropCount, int specifyDropCount,
                       int hellGroupOrder, int hellGroupId, int named, int hellDifficulty) {
        this(spawnIndex, seqId, monsterId, level, type, x, y, randomDropCount, specifyDropCount,
                hellGroupOrder, hellGroupId, named, hellDifficulty, MapScript.CAMP_MONSTER);
    }

    public boolean isHellParty() {
        return hellGroupOrder != 0;
    }

    public boolean isNamedMonster() {
        return monsterId > 49999 && monsterId <= 51000
                || monsterId > 55999 && monsterId <= 60000;
    }

    public int expKind() {
        return switch (type) {
            case TYPE_APC -> MapScript.TYPE_NORMAL;
            case MapScript.ROLE_CHAMPION -> MapScript.TYPE_CHAMPION;
            case MapScript.ROLE_SUPER_CHAMPION -> MapScript.TYPE_SUPER_CHAMPION;
            case MapScript.ROLE_BOSS -> MapScript.TYPE_BOSS_MONSTER;
            default -> type;
        };
    }

    public RoomMonster withType(int newType) {
        return new RoomMonster(spawnIndex, seqId, monsterId, level, newType, x, y,
                randomDropCount, specifyDropCount, hellGroupOrder, hellGroupId, named,
                hellDifficulty, camp, false, passiveObject);
    }

    public RoomMonster withPassiveObject(int objectIndex) {
        return new RoomMonster(spawnIndex, seqId, monsterId, level, type, x, y,
                randomDropCount, specifyDropCount, hellGroupOrder, hellGroupId, named,
                hellDifficulty, camp, fakeBoss, objectIndex);
    }

    /** 物件没打碎客户端就不放它出来：不算清房，也不参与精英升级。 */
    public boolean isPassiveBound() {
        return passiveObject != NO_PASSIVE_OBJECT;
    }

    public boolean countsForRoomClear() {
        return camp == MapScript.CAMP_MONSTER && named == 0 && !isPassiveBound();
    }

    public boolean sweptOnBossDie() {
        return camp != MapScript.CAMP_CHARACTER;
    }

    public static final int TYPE_APC_BOSS = MapScript.ROLE_BOSS;

    public static String typeName(int wireType) {
        return switch (wireType) {
            case MapScript.TYPE_CHAMPION -> "精英";
            case MapScript.TYPE_SUPER_CHAMPION -> "超级精英";
            case MapScript.TYPE_BOSS_MONSTER -> "BOSS";
            case TYPE_APC -> "APC";
            case MapScript.ROLE_CHAMPION -> "APC精英";
            case MapScript.ROLE_SUPER_CHAMPION -> "APC超级精英";
            case MapScript.ROLE_BOSS -> "APC·BOSS";
            default -> "普通";
        };
    }

    public boolean isBoss() {
        return type == MapScript.TYPE_BOSS_MONSTER || type == TYPE_APC_BOSS;
    }

    public Noti29Body.Entity toEntity() {
        return toEntity(0);
    }

    public Noti29Body.Entity toEntity(int extraFlag) {
        // 客户端 00425243..004252C2：named 字节 != 0 → 构造参数 bool，namedGroup = 绑定的物件顺序号。
        // 官方 A4 hell_whitenight：`00 08 00 1B 04 21 00 01 05`（seq 8、怪 1051、Lv33、物件 5）。
        if (isPassiveBound()) {
            return new Noti29Body.Entity(hellGroupOrder, extraFlag, spawnIndex, seqId,
                    monsterId, level, type, 1, passiveObject);
        }
        return new Noti29Body.Entity(hellGroupOrder, extraFlag, spawnIndex, seqId,
                monsterId, level, type, named, 0);
    }

    public com.dylan.protocol.body.Noti155StartDeathTowerMapBody.Monster toTowerMonster() {
        return com.dylan.protocol.body.Noti155StartDeathTowerMapBody.Monster
                .of(spawnIndex, seqId, monsterId, level, type);
    }

    @Override
    public String toString() {
        return "#" + seqId + " monsterId=" + monsterId + " " + typeName(type) + " Lv" + level
                + (isHellParty() ? " ★深渊第 " + hellGroupOrder + " 波/组 " + hellGroupId : "");
    }
}
