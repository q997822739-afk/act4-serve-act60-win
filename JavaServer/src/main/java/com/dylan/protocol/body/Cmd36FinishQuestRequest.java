package com.dylan.protocol.body;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;

public record Cmd36FinishQuestRequest(int questId, int rewardSelection, int multiplier) {

    public static final int PACKET_ID = 36;

    public static final int LENGTH = 6;

    public static final int NO_REWARD_SELECTION = 0xFFFF;

    public static final int SINGLE = 1;

    public Cmd36FinishQuestRequest {
        if (questId < 0 || questId > 0xFFFF) {
            throw new PacketFormatException("questId 超出 u16：" + questId);
        }
        if (rewardSelection < 0 || rewardSelection > 0xFFFF) {
            throw new PacketFormatException("rewardSelection 超出 u16：" + rewardSelection);
        }
        if (multiplier < 0 || multiplier > 0xFFFF) {
            throw new PacketFormatException("multiplier 超出 u16：" + multiplier);
        }
    }

    public boolean hasSelection() {
        return rewardSelection != NO_REWARD_SELECTION;
    }

    public boolean valid() {
        return multiplier > 0;
    }

    public static Cmd36FinishQuestRequest parse(byte[] plain) {
        if (plain == null || plain.length < LENGTH) {
            throw new PacketFormatException("FINISH_QUEST 包体至少要 " + LENGTH + " 字节，实际 "
                    + (plain == null ? "null" : plain.length));
        }
        PacketReader r = new PacketReader(plain);
        int questId = r.u16();
        int selection = r.u16();
        int multiplier = r.u16();
        // A4 没有倍数字段（官方日志三次 CMD 36 第 5–6 字节都是 00 00），按单倍交付；
        // 不然 valid() 判无效，A5 静默丢弃，客户端交不了任务。
        if (ServerConfig.A4) {
            multiplier = SINGLE;
        }
        return new Cmd36FinishQuestRequest(questId, selection, multiplier);
    }
}
