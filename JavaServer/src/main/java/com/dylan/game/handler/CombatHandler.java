package com.dylan.game.handler;

import com.dylan.game.CombatService;
import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.protocol.body.Cmd127BossDieCheckRequest;
import com.dylan.protocol.body.Cmd42DieMonsterRequest;
import com.dylan.protocol.body.Cmd46GetItemRequest;

import java.util.Set;

public final class CombatHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(
                Cmd42DieMonsterRequest.PACKET_ID,
                Cmd46GetItemRequest.PACKET_ID,
                Cmd127BossDieCheckRequest.PACKET_ID,
                CombatService.CMD_ALLDIE_MONSTER);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        switch (packetId) {
            case Cmd42DieMonsterRequest.PACKET_ID -> CombatService.dieMonster(session, plain);
            case Cmd46GetItemRequest.PACKET_ID -> CombatService.getItem(session, plain);
            case Cmd127BossDieCheckRequest.PACKET_ID -> CombatService.bossDying(session, plain);
            case CombatService.CMD_ALLDIE_MONSTER -> CombatService.allMonstersDead(session);
            default -> throw new IllegalStateException("不该路由到这里：" + packetId);
        }
    }
}
