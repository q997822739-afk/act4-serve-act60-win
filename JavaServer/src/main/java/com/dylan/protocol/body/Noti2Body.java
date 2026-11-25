package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti2Body(int subType, List<Noti2Entry> entries) {
    public static final int PACKET_ID = 2;

    public static final int SUBTYPE_CHARACTER_LIST = 2;

    public static final int SUBTYPE_OWN_CHARACTER = 0;

    public static final int SUBTYPE_PARTY_ROSTER = 1;

    public static final int EMPTY_LENGTH = 3;

    public Noti2Body {
        entries = List.copyOf(entries);
    }

    public static Noti2Body characterList(List<Noti2CharacterEntry> entries) {
        return new Noti2Body(SUBTYPE_CHARACTER_LIST, List.<Noti2Entry>copyOf(entries));
    }

    public static Noti2Body ownCharacter(Noti2OwnCharacterEntry entry) {
        return new Noti2Body(SUBTYPE_OWN_CHARACTER, List.of(entry));
    }

    public static Noti2Body partyRoster(List<Noti2PartyEntry> entries) {
        return new Noti2Body(SUBTYPE_PARTY_ROSTER, List.<Noti2Entry>copyOf(entries));
    }

    public byte[] toBytes() {
        validate();
        PacketWriter w = new PacketWriter(128);
        w.u8(subType);
        w.u16(entries.size());
        for (Noti2Entry entry : entries) {
            entry.writeTo(w, subType);
        }
        return w.toArray();
    }

    private void validate() {
        if (subType < 0 || subType > 0xFF) {
            throw new PacketFormatException("子类型是 u8，超出 0..255：" + subType);
        }
        if (entries.size() > 0xFFFF) {
            throw new PacketFormatException("条数是 u16，超出 0..65535：" + entries.size());
        }
        for (Noti2Entry entry : entries) {
            if (!entry.encodesFor(subType)) {
                throw new PacketFormatException("这个条目的编码是给子类型 "
                        + entry.subType() + " 用的，不能放进子类型 " + subType + " 的包里");
            }
        }
    }
}
