package com.dylan.game.handler;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.StallService;
import com.dylan.net.ServerConfig;
import com.dylan.protocol.body.PrivateStorePackets;

import java.util.Set;

/**
 * 摆摊 CMD 85–90（A4 发包点 DNF.exe 0088AF2B / 0088AECB / 0088AFA8 / 0088ADE8 / 0088AF88 / 0088C4BF，
 * 号和 A5 CmdName 一致，见 07_协议检修/社交/摆摊.md §1）。
 */
public final class StallHandler implements CommandHandler {

    static final int BUY = 85;
    static final int ENTER = 86;
    static final int EXIT = 87;
    static final int CREATE = 88;
    static final int REMOVE = 89;
    static final int DISPLAY = 90;

    @Override
    public Set<Integer> handles() {
        return ServerConfig.A4 ? Set.of(BUY, ENTER, EXIT, CREATE, REMOVE, DISPLAY) : Set.of();
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        switch (packetId) {
            case BUY -> StallService.buy(session, PrivateStorePackets.BuyRequest.parse(plain));
            case ENTER -> StallService.enter(session, PrivateStorePackets.EnterRequest.parse(plain).sellerUniqueId());
            case EXIT -> StallService.exit(session);
            case CREATE -> StallService.create(session, PrivateStorePackets.CreateRequest.parse(plain));
            case REMOVE -> StallService.remove(session);
            case DISPLAY -> StallService.display(session, PrivateStorePackets.CompleteDisplayRequest.parse(plain));
            default -> throw new IllegalStateException("不该路由到这里：CMD " + packetId);
        }
    }
}
