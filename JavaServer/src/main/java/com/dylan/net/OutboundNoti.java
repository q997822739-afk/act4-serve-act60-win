package com.dylan.net;

import com.dylan.protocol.NotiHeader;

public record OutboundNoti(int packetId, byte[] body) implements DownstreamPacket {

    public static OutboundNoti of(int packetId, byte[] body) {
        return new OutboundNoti(packetId, body);
    }

    public static OutboundNoti empty(int packetId) {
        return new OutboundNoti(packetId, new byte[0]);
    }

    @Override
    public int category() {
        return NotiHeader.CATEGORY_NOTI;
    }

    @Override
    public void validateDispatchable() {
        DownstreamPacket.requireDispatchable(NotiHeader.CATEGORY_NOTI, packetId);
    }
}
