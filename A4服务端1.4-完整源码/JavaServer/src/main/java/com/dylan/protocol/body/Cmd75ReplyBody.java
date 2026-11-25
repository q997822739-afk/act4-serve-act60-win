package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public final class Cmd75ReplyBody {
    public static final int PACKET_ID = 75;

    public static final int LENGTH = 3;

    public static final int PHASE_CONFIRM = 1;

    public static final int BUTTON_RESTART = 0;

    public static final int BUTTON_OTHER_DUNGEON = 1;

    public static final int BUTTON_TOWN = 2;

    public static final int BUTTON_STAY = 3;

    private Cmd75ReplyBody() {
    }

    public static byte[] echo(int phase, int button) {
        if (phase < 0 || phase > 0xFF || button < 0 || button > 0xFF) {
            throw new PacketFormatException("phase/button 超出 u8：" + phase + "," + button);
        }
        PacketWriter w = new PacketWriter(LENGTH);
        w.u8(1).u8(phase).u8(button);
        return w.toArray();
    }

    public static byte[] rejected() {
        PacketWriter w = new PacketWriter(1);
        w.u8(0);
        return w.toArray();
    }

    public static byte[] error(int code, int button) {
        if (code < 0 || code > 0xFF || button < 0 || button > 0xFF) {
            throw new PacketFormatException("错误码/button 超出 u8：" + code + "," + button);
        }
        if (com.dylan.net.ServerConfig.A4) {
            // A4 客户端失败分支（DNF.exe 00418F65）只打日志不读包体，失败就是 1 字节 00，与 rejected() 相同。
            return rejected();
        }
        PacketWriter w = new PacketWriter(LENGTH);
        w.u8(0).u8(code).u8(button);
        return w.toArray();
    }

    public static final int ERROR_NOT_THE_LEADER = 8;

    public static final int ERROR_UNKNOWN_BUTTON = 1;
}
