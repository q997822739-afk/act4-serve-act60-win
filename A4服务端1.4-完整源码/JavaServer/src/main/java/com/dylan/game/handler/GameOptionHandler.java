package com.dylan.game.handler;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.protocol.body.Cmd210SaveGameOptionRequest;
import com.dylan.protocol.body.Cmd211SaveHotkeyOptionRequest;

import java.util.Set;

public final class GameOptionHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd210SaveGameOptionRequest.PACKET_ID,
                Cmd211SaveHotkeyOptionRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        switch (packetId) {
            case Cmd210SaveGameOptionRequest.PACKET_ID -> saveOption(session, plain);
            case Cmd211SaveHotkeyOptionRequest.PACKET_ID -> saveHotkeys(session, plain);
            default -> throw new IllegalStateException("不该路由到这里：CMD " + packetId);
        }
    }

    private void saveOption(GameSession session, byte[] plain) {
        Cmd210SaveGameOptionRequest req;
        try {
            req = Cmd210SaveGameOptionRequest.parse(plain);
        } catch (RuntimeException e) {
            session.fail("CMD " + Cmd210SaveGameOptionRequest.PACKET_ID
                    + " SAVE_GAME_OPTION_1 包体不合法（"
                    + (plain == null ? "解不开" : plain.length + " 字节") + "）", e);
            return;
        }

        session.gameOption().saveOption(req.option());
    }

    private void saveHotkeys(GameSession session, byte[] plain) {
        Cmd211SaveHotkeyOptionRequest req;
        try {
            req = Cmd211SaveHotkeyOptionRequest.parse(plain);
        } catch (RuntimeException e) {
            session.fail("CMD " + Cmd211SaveHotkeyOptionRequest.PACKET_ID
                    + " SAVE_GAME_OPTION_2 包体不合法（"
                    + (plain == null ? "解不开" : plain.length + " 字节") + "）", e);
            return;
        }

        session.gameOption().saveHotkeys(req.hotkeys());
    }
}
