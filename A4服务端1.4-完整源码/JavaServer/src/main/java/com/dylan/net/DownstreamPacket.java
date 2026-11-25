package com.dylan.net;

import com.dylan.protocol.NotiHeader;
import com.dylan.protocol.io.PacketFormatException;

public sealed interface DownstreamPacket permits OutboundNoti, OutboundCmdReply {

    int category();

    int packetId();

    byte[] body();

    void validateDispatchable();

    static void requireDispatchable(int category, int packetId) {
        NotiHeader h = new NotiHeader(category, packetId, NotiHeader.SIZE);
        if (h.isDispatchable()) {
            return;
        }
        if (packetId == NotiHeader.ID_ANTIBOT || packetId == NotiHeader.ID_DPROTO) {
            throw new PacketFormatException("id " + packetId
                    + " 在分发之前就被客户端截给 AntiBot/DProto 了，服务端不该发它");
        }
        throw new PacketFormatException("category " + category + " 的 id " + packetId
                + " 超出客户端分发器的边界检查");
    }
}
