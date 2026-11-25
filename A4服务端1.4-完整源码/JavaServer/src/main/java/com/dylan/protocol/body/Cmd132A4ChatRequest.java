package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketReader;

/**
 * A4 CMD 132 = 官方枚举 Chat（主聊天框），不是 A5 的 CREATURE_SCRIPT_MESSAGE。
 * 客户端发包点 DNF.exe 007E1354：u8 频道, u16 0, u32（[0xda368c]→vtbl+0x40 的值）, string 正文，只有一个串。
 * 频道由聊天页签换算（007E1303..007E1344）：页签 1→3 普通, 3→2, 10→2 队伍, 4→5, 7→10, 6→9，其余原样。
 * 官方成品服收下不回包（probe_chat.py，中间数据/chat/report.txt）。
 */
public record Cmd132A4ChatRequest(int msgType, int field16, int field32, String text) {

    public static final int PACKET_ID = 132;

    public static Cmd132A4ChatRequest parse(byte[] plain) {
        PacketReader r = new PacketReader(plain);
        int msgType = r.u8();
        int field16 = r.u16();
        int field32 = r.u32();
        String text = r.string(Noti12MessageBody.TEXT_CAPACITY, "聊天正文");
        return new Cmd132A4ChatRequest(msgType, field16, field32, text);
    }

    @Override
    public String toString() {
        return "CMD 132 Chat " + ChatType.name(msgType) + " 「" + text + "」";
    }
}
