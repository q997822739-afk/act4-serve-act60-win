package com.dylan.game.handler;

import com.dylan.game.CommandHandler;
import com.dylan.game.DeathTowerService;
import com.dylan.game.DungeonService;
import com.dylan.game.GameSession;
import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Cmd15ReplyBody;
import com.dylan.protocol.body.Cmd169DeathTowerStageRequest;
import com.dylan.protocol.body.Cmd16SelectDungeonRequest;
import com.dylan.protocol.body.Cmd204DungeonEventStoryPauseRequest;
import com.dylan.protocol.body.Cmd45ReplyBody;
import com.dylan.protocol.body.Cmd48MoveMapRequest;

import java.util.Set;

public final class DungeonHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        // A4 官方枚举里 204 是 RequestItemUnlock（回包 0041D3D6），没有剧情暂停这条命令。
        // 号留给 ItemLockHandler，否则解锁包会被当成暂停标志吃掉，客户端一直等回包。
        if (ServerConfig.A4) {
            return Set.of(
                    Cmd15ReplyBody.PACKET_ID,
                    Cmd16SelectDungeonRequest.PACKET_ID,
                    DungeonService.CMD_FINISH_LOADING,
                    Cmd45ReplyBody.PACKET_ID,
                    Cmd48MoveMapRequest.PACKET_ID,
                    DungeonService.CMD_BACK_2_VILLAGE,
                    Cmd169DeathTowerStageRequest.PACKET_ID,
                    DungeonService.CMD_HELLPARTY_START);
        }
        return Set.of(
                Cmd15ReplyBody.PACKET_ID,
                Cmd16SelectDungeonRequest.PACKET_ID,
                DungeonService.CMD_FINISH_LOADING,
                Cmd45ReplyBody.PACKET_ID,
                Cmd48MoveMapRequest.PACKET_ID,
                DungeonService.CMD_BACK_2_VILLAGE,
                Cmd169DeathTowerStageRequest.PACKET_ID,
                Cmd204DungeonEventStoryPauseRequest.PACKET_ID,
                DungeonService.CMD_HELLPARTY_START);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        switch (packetId) {
            case Cmd15ReplyBody.PACKET_ID -> DungeonService.startGame(session);
            case Cmd16SelectDungeonRequest.PACKET_ID -> DungeonService.selectDungeon(session, plain);
            case DungeonService.CMD_FINISH_LOADING -> DungeonService.finishLoading(session);
            case Cmd45ReplyBody.PACKET_ID -> DungeonService.giveUpGame(session);
            case Cmd48MoveMapRequest.PACKET_ID -> DungeonService.moveMap(session, plain);
            case DungeonService.CMD_BACK_2_VILLAGE -> DungeonService.backToVillage(session);
            case Cmd169DeathTowerStageRequest.PACKET_ID -> {
                // A4 的 169 是 GuildCreateRight（opcodes_cmd.txt），不是死亡之塔。
                // 客户端 008DE544 只 push 0xA9 后 SEND，包体为空，紧接着 008DE55F 本地开窗口 0x11，不等回包。
                // 官方实抓（probe_guild / catalog「申请创建公会」）收下不回。
                // 空包若交给 Cmd169DeathTowerStageRequest.parse，会因读不到 u8 抛异常并断开。
                // 有包体仍是 A5 死亡之塔的层开始/结束，不要改走公会。
                if (plain == null || plain.length == 0) {
                    return;
                }
                DeathTowerService.stageCommand(session, plain);
            }
            case Cmd204DungeonEventStoryPauseRequest.PACKET_ID ->
                    DungeonService.dungeonEventStoryPause(session, plain);
            case DungeonService.CMD_HELLPARTY_START -> DungeonService.hellPartyStart(session);
            default -> throw new IllegalStateException("不该路由到这里：" + packetId);
        }
    }
}
