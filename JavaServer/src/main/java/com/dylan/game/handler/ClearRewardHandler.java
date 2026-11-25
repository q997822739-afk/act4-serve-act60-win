package com.dylan.game.handler;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.RewardService;
import com.dylan.protocol.body.Cmd49PlayResultRequest;
import com.dylan.protocol.body.Cmd72ReplyBody;
import com.dylan.protocol.body.Cmd73ReplyBody;
import com.dylan.protocol.body.Cmd74SelectCardRequest;
import com.dylan.protocol.body.Cmd75ReplyBody;

import java.util.Set;

public final class ClearRewardHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(
                Cmd49PlayResultRequest.PACKET_ID,
                Cmd72ReplyBody.PACKET_ID,
                Cmd73ReplyBody.PACKET_ID,
                Cmd74SelectCardRequest.PACKET_ID,
                Cmd75ReplyBody.PACKET_ID,
                RewardService.CMD_CHARACTER_STATISTIC);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        switch (packetId) {
            case Cmd49PlayResultRequest.PACKET_ID -> RewardService.setPlayResult(session, plain);
            case Cmd72ReplyBody.PACKET_ID -> RewardService.scoreScrollState(session);
            case Cmd73ReplyBody.PACKET_ID -> RewardService.cardSelectRightState(session);
            case Cmd74SelectCardRequest.PACKET_ID -> RewardService.selectCard(session, plain);
            case Cmd75ReplyBody.PACKET_ID -> RewardService.eplpCommand(session, plain);
            case RewardService.CMD_CHARACTER_STATISTIC ->
                    RewardService.characterStatistic(session, plain);
            default -> throw new IllegalStateException("不该路由到这里：" + packetId);
        }
    }
}
