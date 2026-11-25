package com.dylan.net;

import com.dylan.protocol.NotiHeader;

public record OutboundCmdReply(int packetId, byte[] body) implements DownstreamPacket {

    public static OutboundCmdReply of(int packetId, byte[] body) {
        return new OutboundCmdReply(packetId, body);
    }

    @Override
    public int category() {
        return NotiHeader.CATEGORY_CMD_REPLY;
    }

    @Override
    public void validateDispatchable() {
        DownstreamPacket.requireDispatchable(NotiHeader.CATEGORY_CMD_REPLY, packetId);
    }
}
