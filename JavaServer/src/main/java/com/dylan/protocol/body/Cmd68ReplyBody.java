package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Cmd68ReplyBody(String ticket, int check) {

    public static final int PACKET_ID = 68;

    public static final int RESULT_OK = 1;

    public static final int RESULT_FAIL = 0;

    public static final int MAX_TICKET_LENGTH = 50;

    public static final int MAX_CHECK = 0xFFFF;

    public Cmd68ReplyBody {
        if (ticket == null) {
            throw new PacketFormatException("票据号是 null");
        }
        if (check < 0 || check > MAX_CHECK) {
            throw new PacketFormatException("校验数 " + check + " 超出 0.." + MAX_CHECK);
        }
    }

    public byte[] toBytes() {
        byte[] raw = ticket.getBytes(PacketWriter.DEFAULT_CHARSET);
        if (raw.length >= MAX_TICKET_LENGTH) {
            throw new PacketFormatException("票据号 " + raw.length + " 字节，客户端上限是 "
                    + (MAX_TICKET_LENGTH - 1));
        }
        PacketWriter w = new PacketWriter(1 + 4 + raw.length + 4);
        w.u8(RESULT_OK);
        w.u32(raw.length);
        w.bytes(raw);
        w.u32(check);
        return w.toArray();
    }

    public static byte[] rejected(int errorCode) {
        if (errorCode < 0 || errorCode > 0xFF) {
            throw new PacketFormatException("错误码 " + errorCode + " 超出 u8");
        }
        if (com.dylan.net.ServerConfig.A4) {
            // A4 客户端失败分支什么都不读（DNF.exe 00418709 je 直接收尾），官方失败回包就是 1 字节 00
            //（参考_官方协议日志 3 次）。
            return new byte[]{(byte) RESULT_FAIL};
        }
        PacketWriter w = new PacketWriter(2);
        w.u8(RESULT_FAIL);
        w.u8(errorCode);
        return w.toArray();
    }
}
