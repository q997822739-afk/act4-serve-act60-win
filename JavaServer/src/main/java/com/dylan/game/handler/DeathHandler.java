package com.dylan.game.handler;

import com.dylan.game.CommandHandler;
import com.dylan.game.DeathService;
import com.dylan.game.GameSession;
import com.dylan.protocol.body.Cmd43DieCharacterRequest;
import com.dylan.protocol.body.Cmd44UseCoinRequest;
import com.dylan.protocol.io.PacketReader;

import java.util.Set;

public final class DeathHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd43DieCharacterRequest.PACKET_ID,
                Cmd44UseCoinRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        PacketReader r = new PacketReader(plain);
        switch (packetId) {
            case Cmd43DieCharacterRequest.PACKET_ID -> {
                Cmd43DieCharacterRequest request = Cmd43DieCharacterRequest.parse(r);
                warnIfMore(r, request.toString());
                DeathService.dieCharacter(session, request);
            }
            case Cmd44UseCoinRequest.PACKET_ID -> {
                Cmd44UseCoinRequest request = Cmd44UseCoinRequest.parse(r);
                warnIfMore(r, request.toString());
                DeathService.useCoin(session, request);
            }
            default -> throw new IllegalStateException("DeathHandler 收到了它没声明的号 " + packetId);
        }
    }

    private static void warnIfMore(PacketReader r, String what) {
        int left = r.remaining();
        if (left == 0) {
            return;
        }
        byte[] rest = r.bytes(left);
        for (byte b : rest) {
            if (b != 0) {
                return;
            }
        }
    }
}
