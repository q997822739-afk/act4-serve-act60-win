package com.dylan.net;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import com.dylan.protocol.crypto.A4Cipher;
import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public class DownstreamPacketEncoder extends MessageToByteEncoder<DownstreamPacket> {

    @Override
    protected void encode(ChannelHandlerContext ctx, DownstreamPacket msg, ByteBuf out) {
        encodeTo(ctx, msg, out);
    }

    /** 和 {@link #encode} 同一套编码，给要自己拆写的地方用（见 GameSession.replyThenClose）。 */
    public static void encodeTo(ChannelHandlerContext ctx, DownstreamPacket msg, ByteBuf out) {
        byte[] body = msg.body();
        if (body.length > PacketWriter.MAX_BODY_LENGTH) {
            throw new PacketFormatException("body of " + body.length
                    + " bytes exceeds the client's limit of " + PacketWriter.MAX_BODY_LENGTH);
        }
        msg.validateDispatchable();

        // A4 客户端没有 / 不读的 NOTI（官方成品服从不发，客户端收到会读错）：A5 服务照常跑，只在这里丢掉。
        // 清单 ServerConfig.A4_DROPPED_NOTI，依据各写在那里
        if (ServerConfig.A4 && msg.category() == 0 && ServerConfig.A4_DROPPED_NOTI.contains(msg.packetId())) {
            if (ServerConfig.WIRE_TRACE) {
                System.out.println("[A4 DROP] NOTI " + msg.packetId() + "（A4 没有这个包）");
            }
            return;
        }
        if ((msg.packetId() & ~0xFF) != 0) {
            throw new PacketFormatException("A4 下行包号是 1 字节，发不出 " + msg.packetId());
        }
        if (ServerConfig.WIRE_TRACE) {
            System.out.println("[A4 TX " + SessionTag.of(ctx) + "] "
                    + (msg.category() == 0 ? "NOTI " : "REPLY ") + msg.packetId()
                    + " " + Hex.dumpBlock(body, ServerConfig.DUMP_LIMIT));
        }

        // A4：type u8, id u8, 总长 u32（含这 6 字节头），然后整段载荷循环 XOR。
        byte[] wire = body.clone();
        A4Cipher.apply(wire, 0, wire.length);
        out.writeByte(msg.category());
        out.writeByte(msg.packetId());
        out.writeIntLE(A4_HEADER_SIZE + wire.length);
        out.writeBytes(wire);
    }

    public static final int A4_HEADER_SIZE = 6;
}
