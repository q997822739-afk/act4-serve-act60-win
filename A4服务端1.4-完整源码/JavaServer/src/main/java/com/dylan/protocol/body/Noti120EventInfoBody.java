package com.dylan.protocol.body;

import java.util.List;

import com.dylan.protocol.io.PacketWriter;

public record Noti120EventInfoBody(List<Integer> events, List<AddInfo> addInfos) {
    public static final int PACKET_ID = 120;

    public static final int MAX_EVENT_NO = 165;

    public static final int EVENT_SCHOOL_MATCH = 9;

    public static final int EVENT_GUILD_WAR = 33;

    public static final int EMPTY_LENGTH = 3;

    public record AddInfo(int eventNo, int value1, int value2) {
        public AddInfo {
            requireEventNo(eventNo);
            requireU16(value1, "value1");
            requireU16(value2, "value2");
        }
    }

    private static final Noti120EventInfoBody NONE =
            new Noti120EventInfoBody(List.of(), List.of());

    public Noti120EventInfoBody {
        events = List.copyOf(events);
        addInfos = List.copyOf(addInfos);
        if (events.size() > 0xFFFF) {
            throw new IllegalArgumentException("活动数 " + events.size() + " 放不进 u16");
        }
        if (addInfos.size() > 0xFF) {
            throw new IllegalArgumentException("附加信息 " + addInfos.size() + " 条放不进 u8");
        }
        for (int eventNo : events) {
            requireEventNo(eventNo);
            if (eventNo == EVENT_SCHOOL_MATCH) {
                throw new IllegalArgumentException(
                        "活动 " + EVENT_SCHOOL_MATCH + "（超级学校对战）开着时客户端会额外读"
                                + "「u8 条数 + N 个 u16」，本项目产不出那段数据");
            }
            if (eventNo == EVENT_GUILD_WAR) {
                throw new IllegalArgumentException(
                        "活动 " + EVENT_GUILD_WAR + "（公会战）这一版不做 —— "
                                + "官方公会战逻辑在另一个进程，我们一条数据都没有");
            }
        }
    }

    public static Noti120EventInfoBody none() {
        return NONE;
    }

    public static Noti120EventInfoBody of(int... eventNos) {
        java.util.ArrayList<Integer> list = new java.util.ArrayList<>(eventNos.length);
        for (int eventNo : eventNos) {
            list.add(eventNo);
        }
        return new Noti120EventInfoBody(list, List.of());
    }

    /**
     * A4 客户端（DNF.exe 0042E2E0）只读一个 u32（按位当活动开关用，含义未知）。官方恒值 {@code 00 80 00 20}：
     * 现用官方服进世界 20 个 Lv60 角色（中间数据/dummy/enter_off_NN.txt）全是这 4 字节；
     * 参考_官方协议日志 里较新的会话（18:10、12:14，带"通知 178"）也是它，早期会话（09:13）是 {@code 00000000}。
     */
    public static final byte[] A4_BYTES = {0x00, (byte) 0x80, 0x00, 0x20};

    public static final int A4_LENGTH = A4_BYTES.length;

    public byte[] toBytes() {
        if (com.dylan.net.ServerConfig.A4) {
            return A4_BYTES.clone();
        }
        PacketWriter w = new PacketWriter(EMPTY_LENGTH + events.size() * 2 + addInfos.size() * 6);
        w.u16(events.size());
        for (int eventNo : events) {
            w.u16(eventNo);
        }
        w.u8(addInfos.size());
        for (AddInfo info : addInfos) {
            w.u16(info.eventNo());
            w.u16(info.value1());
            w.u16(info.value2());
        }
        return w.toArray();
    }

    private static void requireEventNo(int eventNo) {
        if (eventNo < 0 || eventNo > MAX_EVENT_NO) {
            throw new IllegalArgumentException(
                    "活动号 " + eventNo + " 超出官方的 0.." + MAX_EVENT_NO);
        }
    }

    private static void requireU16(int value, String what) {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException(what + " " + value + " 放不进 u16");
        }
    }
}
