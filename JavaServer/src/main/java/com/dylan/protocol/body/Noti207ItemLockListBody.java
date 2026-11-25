package com.dylan.protocol.body;

import java.util.ArrayList;
import java.util.List;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

public record Noti207ItemLockListBody(List<Entry> entries) {

    public static final int PACKET_ID = 207;

    /**
     * A4 的锁列表是 NOTI 192（DNF.exe 跳转表 0x4306E8 第 192 项 → 00430580）：同一布局 u16 条数，
     * 每条 u8 容器 + u16 槽 + u8 状态，状态 2 再跟 u32 剩余秒；逐条 0040DE40 写物品的锁状态，状态 2 再 0046F0F0 写倒计时。
     * 客户端不清旧状态，所以状态 0 = 解锁（解锁倒计时到期用这个，ACT5 的 NOTI 208 在 A4 一字节不读）。
     * 官方成品服每次重发 NOTI 13 后都跟一条（它没有锁定功能，所以恒为 00 00）。
     */
    public static final int A4_PACKET_ID = 192;

    public static final int STATE_FREE = 0;

    public static final int STATE_LOCKED = 1;

    public static final int STATE_UNLOCK_WAITING = 2;

    public Noti207ItemLockListBody {
        entries = List.copyOf(entries);
        if (entries.size() > 0xFFFF) {
            throw new PacketFormatException("条数是 u16，超出 65535：" + entries.size());
        }
    }

    public static Noti207ItemLockListBody empty() {
        return new Noti207ItemLockListBody(List.of());
    }

    public record Entry(int itemSpace, int slot, int state, int remainSec) {

        public Entry {
            Cmd229ReplyBody.check(itemSpace, slot);
            if (state != STATE_FREE && state != STATE_LOCKED && state != STATE_UNLOCK_WAITING) {
                throw new PacketFormatException("锁定状态只有 0 / 1 / 2，收到 " + state);
            }
            if (remainSec < 0) {
                throw new PacketFormatException("剩余秒数不能是负的：" + remainSec);
            }
        }

        public static Entry locked(int itemSpace, int slot) {
            return new Entry(itemSpace, slot, STATE_LOCKED, 0);
        }

        public static Entry released(int itemSpace, int slot) {
            return new Entry(itemSpace, slot, STATE_FREE, 0);
        }

        public static Entry waiting(int itemSpace, int slot, int remainSec) {
            return new Entry(itemSpace, slot, STATE_UNLOCK_WAITING, remainSec);
        }

        int length() {
            return state == STATE_UNLOCK_WAITING ? 8 : 4;
        }

        void write(PacketWriter w) {
            w.u8(itemSpace);
            w.u16(slot);
            w.u8(state);
            if (state == STATE_UNLOCK_WAITING) {
                w.u32(remainSec);
            }
        }
    }

    public byte[] toBytes() {
        int length = 2;
        for (Entry e : entries) {
            length += e.length();
        }
        PacketWriter w = new PacketWriter(length);
        w.u16(entries.size());
        for (Entry e : entries) {
            e.write(w);
        }
        return w.toArray();
    }

    public static final class Builder {

        private final List<Entry> entries = new ArrayList<>();

        public Builder add(Entry entry) {
            entries.add(entry);
            return this;
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }

        public Noti207ItemLockListBody build() {
            return new Noti207ItemLockListBody(entries);
        }
    }
}
