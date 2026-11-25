package com.dylan.game.handler;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.PvpLobby;
import com.dylan.net.ServerConfig;

import java.util.Set;

/**
 * A4 决斗场房间。53 建房、54 进房、55 座位、56 准备、57 模式、58 阵亡、59 超时、
 * 60 结算关窗、61 回 NOTI 49 的本局统计（官方 RES_PVP_RANK）、62 换图。
 * 房间邀请是已经入座的人发 CMD 10/11 kind 0，在 {@link PeerHandler} 里先于城镇组队分走。
 * 城镇 kind 2、3 仍回「对方已退出」。开打不走 CMD 15，也不走 CMD 122。
 */
public final class PvpHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return ServerConfig.A4 ? Set.of(53, 54, 55, 56, 57, 58, 59, 60, 61, 62) : Set.of();
    }

    @Override
    public void handle(GameSession s, int packetId, byte[] plain) {
        if (!s.hasCharacter()) {
            return;
        }
        switch (packetId) {
            case 53 -> PvpLobby.make(s, plain);
            case 54 -> PvpLobby.enter(s, plain);
            case 55 -> PvpLobby.seatState(s, plain);
            case 56 -> PvpLobby.ready(s, plain);
            case 57 -> PvpLobby.team(s, plain);
            case 58 -> PvpLobby.death(s, plain);
            case 59 -> PvpLobby.timeout(s, plain);
            case 60 -> PvpLobby.resultClosed(s);
            case 61 -> PvpLobby.rankReceived(s);
            case 62 -> PvpLobby.map(s, plain);
            default -> throw new IllegalStateException("不该路由到这里：CMD " + packetId);
        }
    }
}
