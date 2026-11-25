package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Cmd273ChangeEmotionRequest(int emotion) {

    public static final int PACKET_ID = 273;

    /**
     * A4 SetMood。发包点 0091098E 写 2 字节表情号。
     * 回包 0041D1F0：结果非 0 再读 u16，结果为 0 再读 u8，和 {@link Cmd273ReplyBody} 一样。
     * 回包号必须是 195，客户端不认 273。
     */
    public static final int A4_PACKET_ID = 195;

    public static final int LENGTH = 2;

    public Cmd273ChangeEmotionRequest {
        if (emotion < 0 || emotion > 0xFFFF) {
            throw new PacketFormatException("表情号是 u16，超出 0..65535：" + emotion);
        }
    }

    public static Cmd273ChangeEmotionRequest parse(byte[] plain) {
        if (plain == null || plain.length < LENGTH) {
            throw new PacketFormatException("CHANGE_EMOTION 包体至少要 " + LENGTH
                    + " 字节，实际 " + (plain == null ? "null" : plain.length));
        }
        return new Cmd273ChangeEmotionRequest((plain[0] & 0xFF) | ((plain[1] & 0xFF) << 8));
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(LENGTH);
        w.u16(emotion);
        return w.toArray();
    }

    @Override
    public String toString() {
        return "emotion=" + emotion;
    }
}
