package com.dylan.game;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CommandRouter {
    private final Map<Integer, CommandHandler> byPacketId = new HashMap<>();

    public CommandRouter(List<CommandHandler> handlers) {
        for (CommandHandler handler : handlers) {
            for (int packetId : handler.handles()) {
                CommandHandler previous = byPacketId.put(packetId, handler);
                if (previous != null) {
                    throw new IllegalStateException(CmdLabel.of(packetId) + " 同时被 "
                            + previous.getClass().getSimpleName() + " 和 "
                            + handler.getClass().getSimpleName() + " 声明");
                }
            }
        }
    }

    public boolean dispatch(GameSession session, int packetId, byte[] plain) {
        CommandHandler handler = byPacketId.get(packetId);
        if (handler == null) {
            return false;
        }
        handler.handle(session, packetId, plain);
        if (session != null && session.inventory() != null) {
            session.inventory().flushEmptiedSlots();
        }
        return true;
    }

    private static final class CmdLabel {
        static String of(int packetId) {
            return com.dylan.protocol.CmdName.describe(packetId);
        }
    }
}
