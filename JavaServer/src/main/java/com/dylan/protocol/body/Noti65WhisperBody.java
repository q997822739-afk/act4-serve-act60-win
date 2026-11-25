package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

/**
 * A4 NOTI 65：带名字的聊天（密语用这条）。官方成品服没发过，布局按客户端读包函数 DNF.exe 00422354：
 * u8 频道, u8 状态, string 名字（00 9E5C90 上限 0x1F，含结尾 0 → 最多 30 字节）, string 正文（上限 0x100）。
 * 频道 0x21 且状态 0 → 00422079 显示路径，名字作为 009392D0 第 4 参数传给密语窗口（008F6CA0 用它开页签）；
 * 状态非 0 → 008F6CF0（对方状态提示），不显示正文。
 * NOTI 12（00421F2D）走同一个显示函数但第 4 参数固定 0，频道 33 用 NOTI 12 会在 008F6BE0 strlen(NULL) 崩溃
 * （2026-10-28 外网实测 qol.log / BugTrap 栈 00422098 → 009392D0 → 008F6CA0 → 008F6B80）。
 */
public record Noti65WhisperBody(int msgType, int status, String name, String text) {

    public static final int PACKET_ID = 65;

    public static final int MAX_NAME_BYTES = 0x1F - 1;

    public Noti65WhisperBody {
        if (msgType < 0 || msgType > 0xFF || status < 0 || status > 0xFF) {
            throw new PacketFormatException("频道 / 状态放不进 u8：" + msgType + " / " + status);
        }
        name = Noti12MessageBody.fit(name == null ? "" : name, MAX_NAME_BYTES);
        text = Noti12MessageBody.sanitize(text);
    }

    public byte[] toBytes() {
        byte[] rawName = name.getBytes(PacketWriter.DEFAULT_CHARSET);
        byte[] rawText = text.getBytes(PacketWriter.DEFAULT_CHARSET);
        if (rawText.length > Noti12MessageBody.MAX_TEXT_BYTES) {
            throw new PacketFormatException("正文 " + rawText.length + " 字节，超过客户端容量");
        }
        return new PacketWriter(10 + rawName.length + rawText.length)
                .u8(msgType)
                .u8(status)
                .string(name)
                .string(text)
                .toArray();
    }
}
