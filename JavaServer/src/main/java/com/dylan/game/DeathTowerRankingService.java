package com.dylan.game;

import com.dylan.protocol.body.Noti156DeathTowerRankingBody;
import com.dylan.store.DeathTowerStore;
import com.dylan.store.GameStore;

import java.util.ArrayList;
import java.util.List;

public final class DeathTowerRankingService {

    private DeathTowerRankingService() {
    }

    public static int towerIndexOf(int dungeonIndex) {
        return dungeonIndex >= FIRST_TOWER && dungeonIndex <= LAST_TOWER
                ? dungeonIndex - FIRST_TOWER : -1;
    }

    public static final int FIRST_TOWER = 11000;

    public static final int LAST_TOWER = 11005;

    public static final int NO_RECORD = -1;

    public static final int NOT_RENEWED = 0;

    public static final int RENEWED = 1;

    public static final int BONUS_MY_RECORD = 1;

    public static final int BONUS_TOP_RECORD = 2;

    public static int settleMember(GameSession member, DeathTower tower, String memberInfo,
                                   long playTimeMs, boolean cleared) {
        int towerIndex = towerIndexOf(tower.dungeonIndex());
        if (towerIndex < 0) {
            return 0;
        }
        GameStore store = member.store();
        if (store == null) {
            return 0;
        }
        int stage = tower.currentStage();
        int partType = Math.clamp(tower.startMemberCount(), 1, Party.MAX_MEMBERS);
        int playTime = (int) Math.clamp(playTimeMs, 0, Integer.MAX_VALUE);

        int myRenew;
        boolean topRenew;
        int rank;
        Noti156DeathTowerRankingBody.Record best;
        List<Noti156DeathTowerRankingBody.Record> top;
        try {
            DeathTowerStore records = store.deathTower();
            myRenew = checkRenewMyRecord(records, member.characNo(), towerIndex, partType,
                    stage, playTime);
            topRenew = cleared && checkRenewTopRecord(records, towerIndex, partType,
                    stage, playTime);
            rank = records.towerRank(towerIndex, partType, stage, playTime);
            if (myRenew != NOT_RENEWED) {
                records.saveTowerBestRecord(new DeathTowerStore.TowerRecord(
                        member.characNo(), towerIndex, partType, memberInfo, stage, playTime));
            }
            best = records.towerBestRecord(member.characNo(), towerIndex, partType)
                    .map(DeathTowerRankingService::toRecord).orElse(null);
            top = new ArrayList<>();
            for (var row : records.towerTopRankers(towerIndex, partType,
                    Noti156DeathTowerRankingBody.TOP_RANKERS)) {
                top.add(toRecord(row));
            }
        } catch (RuntimeException e) {
            return 0;
        }
        boolean renewed = myRenew == RENEWED;
        byte[] body = Noti156DeathTowerRankingBody
                .of(playTime, stage, renewed, rank, best, top).toBytes();
        member.sendNow(Noti156DeathTowerRankingBody.PACKET_ID, body);

        int bonus = cleared
                ? (renewed ? BONUS_MY_RECORD : 0) + (topRenew ? BONUS_TOP_RECORD : 0)
                : 0;
        return bonus;
    }

    private static int checkRenewMyRecord(DeathTowerStore records, int characNo, int towerIndex,
                                          int partType, int stage, int playTimeMs) {
        var old = records.towerBestRecord(characNo, towerIndex, partType);
        if (old.isEmpty()) {
            return NO_RECORD;
        }
        return isBetter(stage, playTimeMs, old.get().stage(), old.get().playTimeMs())
                ? RENEWED : NOT_RENEWED;
    }

    private static boolean checkRenewTopRecord(DeathTowerStore records, int towerIndex,
                                               int partType, int stage, int playTimeMs) {
        var top = records.towerTopRankers(towerIndex, partType, 1);
        if (top.isEmpty()) {
            return false;
        }
        return isBetter(stage, playTimeMs, top.get(0).stage(), top.get(0).playTimeMs());
    }

    static boolean isBetter(int stage, int playTimeMs, int otherStage, int otherPlayTimeMs) {
        return stage == otherStage ? playTimeMs < otherPlayTimeMs : stage > otherStage;
    }

    static String encode(List<DeathTower.MemberInfo> members) {
        StringBuilder sb = new StringBuilder();
        for (DeathTower.MemberInfo m : members) {
            if (!sb.isEmpty()) {
                sb.append('|');
            }
            sb.append(m.name().replace(':', ' ').replace('|', ' '))
                    .append(':').append(m.job()).append(':').append(m.growType());
        }
        return sb.length() > 255 ? sb.substring(0, 255) : sb.toString();
    }

    static List<Noti156DeathTowerRankingBody.Member> decode(String memberInfo) {
        List<Noti156DeathTowerRankingBody.Member> out = new ArrayList<>();
        if (memberInfo == null || memberInfo.isBlank()) {
            return out;
        }
        for (String part : memberInfo.split("\\|")) {
            String[] fields = part.split(":", -1);
            if (fields.length < 3) {
                continue;
            }
            try {
                out.add(new Noti156DeathTowerRankingBody.Member(fields[0],
                        Integer.parseInt(fields[1].trim()) & 0xFF,
                        Integer.parseInt(fields[2].trim()) & 0xFF));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private static Noti156DeathTowerRankingBody.Record toRecord(DeathTowerStore.TowerRecord row) {
        return Noti156DeathTowerRankingBody.Record.of(decode(row.memberInfo()),
                row.stage(), row.playTimeMs(), 0);
    }
}
