package com.dylan.game.handler;

import java.util.Set;

import com.dylan.game.CommandHandler;
import com.dylan.game.GameSession;
import com.dylan.game.SessionRegistry;
import com.dylan.protocol.body.Cmd178AgreeEnchantRequest;
import com.dylan.protocol.body.Cmd179TryEnchantRequest;
import com.dylan.protocol.body.Cmd180PutItemForEnchantRequest;

public final class EnchantHandler implements CommandHandler {

    @Override
    public Set<Integer> handles() {
        return Set.of(Cmd178AgreeEnchantRequest.PACKET_ID,
                Cmd179TryEnchantRequest.PACKET_ID,
                Cmd180PutItemForEnchantRequest.PACKET_ID);
    }

    @Override
    public void handle(GameSession session, int packetId, byte[] plain) {
        if (!session.hasCharacter() || SessionRegistry.presenceOf(session) == null) {
            return;
        }
        if (packetId == Cmd179TryEnchantRequest.PACKET_ID) {
            Cmd179TryEnchantRequest request = Cmd179TryEnchantRequest.parse(plain);
            session.enchant().onTryEnchant(request);
            return;
        }
        if (packetId == Cmd178AgreeEnchantRequest.PACKET_ID) {
            Cmd178AgreeEnchantRequest request = Cmd178AgreeEnchantRequest.parse(plain);
            session.enchant().onAgreeEnchant(request);
            return;
        }
        Cmd180PutItemForEnchantRequest request = Cmd180PutItemForEnchantRequest.parse(plain);
        session.enchant().onPutItem(request);
    }
}
