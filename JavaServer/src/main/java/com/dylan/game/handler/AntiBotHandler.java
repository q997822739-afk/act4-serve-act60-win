package com.dylan.game.handler;

import com.dylan.game.AntiBotState;
import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.Cmd118ClientCheckRequest;
import com.dylan.protocol.body.Cmd118ReplyBody;
import com.dylan.protocol.body.Cmd280AntiBotRequest;
import com.dylan.protocol.body.Cmd282DProtoCallbackRequest;
import com.dylan.protocol.io.PacketFormatException;

import java.util.Set;

public final class AntiBotHandler implements CommandHandler {
    /** A4 SecurityCard。回包 0041B563：结果非 0 不再读。私人服直接放行。 */
    static final int A4_SECURITY_CARD = 161;

    @Override
    public Set<Integer> handles() {
        if (ServerConfig.A4) {
            return Set.of(Cmd280AntiBotRequest.PACKET_ID, Cmd282DProtoCallbackRequest.PACKET_ID,
                    Cmd118ClientCheckRequest.PACKET_ID, A4_SECURITY_CARD);
        }
        return Set.of(Cmd280AntiBotRequest.PACKET_ID, Cmd282DProtoCallbackRequest.PACKET_ID,
                Cmd118ClientCheckRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        if (plain == null) {
            session.fail(GameSession.describe(packetId) + " 的包体解不开（不是 16 的倍数）");
            return;
        }
        try {
            switch (packetId) {
                case Cmd280AntiBotRequest.PACKET_ID -> antiBot(session, plain);
                case Cmd282DProtoCallbackRequest.PACKET_ID -> dprotoCallback(session, plain);
                case Cmd118ClientCheckRequest.PACKET_ID -> clientCheck(session, plain);
                case A4_SECURITY_CARD -> {
                    // 00438AE5 发出。结果 1 时 0041B563 不再读字节，安全卡窗口不会卡住。
                    session.reply(A4_SECURITY_CARD, new byte[] {1});
                    session.flush();
                }
                default -> throw new IllegalStateException("不该路由到这里：" + packetId);
            }
        } catch (PacketFormatException e) {
        }
    }

    private static void clientCheck(GameSession session, byte[] plain) {
        byte[] body = Cmd118ReplyBody.success();
        session.reply(Cmd118ReplyBody.PACKET_ID, body);

        Cmd118ClientCheckRequest.parse(plain);
    }

    private static void antiBot(GameSession session, byte[] plain) {
        Cmd280AntiBotRequest request = Cmd280AntiBotRequest.parse(plain);
        AntiBotState state = session.antiBot();
        boolean broken = !request.followsAfter(state.antiBotSerial());

        state.recordAntiBot(request.serial(), broken);
    }

    private static void dprotoCallback(GameSession session, byte[] plain) {
        Cmd282DProtoCallbackRequest request = Cmd282DProtoCallbackRequest.parse(plain);
        AntiBotState state = session.antiBot();
        boolean broken = !request.followsAfter(state.dprotoSerial());

        state.recordDproto(request.serial(), broken);
    }
}
