package com.dylan.game;

import java.util.Set;

public interface CommandHandler {

    Set<Integer> handles();

    void handle(GameSession session, int packetId, byte[] plain);
}
