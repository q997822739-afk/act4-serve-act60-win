package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketWriter;

public sealed interface Noti2Entry
        permits Noti2CharacterEntry, Noti2OwnCharacterEntry, Noti2PartyEntry {

    int subType();

    default boolean encodesFor(int subType) {
        return subType() == subType;
    }

    void writeTo(PacketWriter w, int subType);
}
