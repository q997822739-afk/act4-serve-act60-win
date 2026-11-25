package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;
import com.dylan.protocol.io.PacketWriter;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public final class PrivateStorePackets {
    private PrivateStorePackets() {
    }

    private static final Charset TEXT = Charset.forName("GBK");

    public record CreateRequest(int village, int area, int x, int y) {
        public static final int LENGTH = 6;

        public static CreateRequest parse(byte[] body) {
            PacketReader r = new PacketReader(body);
            if (r.remaining() < LENGTH) {
                throw new PacketFormatException("CREATE_PRIVATE_STORE 需要 " + LENGTH
                        + " 字节，只有 " + r.remaining());
            }
            return new CreateRequest(r.u8(), r.u8(), (short) r.u16(), (short) r.u16());
        }

        public byte[] toBytes() {
            return new PacketWriter(LENGTH)
                    .u8(village).u8(area).u16(x & 0xFFFF).u16(y & 0xFFFF).toArray();
        }
    }

    public record EnterRequest(int sellerUniqueId) {
        public static EnterRequest parse(byte[] body) {
            PacketReader r = new PacketReader(body);
            if (r.remaining() >= 4) {
                return new EnterRequest(r.u32());
            }
            if (r.remaining() >= 2) {
                return new EnterRequest(r.u16());
            }
            throw new PacketFormatException("ENTER_PRIVATE_STORE 至少要 2 字节，只有 "
                    + r.remaining());
        }

        public byte[] toBytes() {
            return new PacketWriter(4).u32(sellerUniqueId).toArray();
        }
    }

    public record BuyRequest(int shelfIndex, int count) {
        public static final int LENGTH = 6;

        public static BuyRequest parse(byte[] body) {
            PacketReader r = new PacketReader(body);
            if (r.remaining() < LENGTH) {
                throw new PacketFormatException("BUY_PRIVATE_STORE_ITEM 需要 " + LENGTH
                        + " 字节，只有 " + r.remaining());
            }
            return new BuyRequest(r.u16(), r.u32());
        }

        public byte[] toBytes() {
            return new PacketWriter(LENGTH).u16(shelfIndex).u32(count).toArray();
        }
    }

    public record CompleteDisplayRequest(String title, List<Entry> items) {
        public record Entry(int shelfIndex, long price, int itemSpace, int invenSlot, int count) {
        }

        public CompleteDisplayRequest {
            items = List.copyOf(items);
        }

        public static CompleteDisplayRequest parse(byte[] body) {
            PacketReader r = new PacketReader(body);
            // A4 发包函数 DNF.exe 0088C490：标题指针为空时连 u32 长度都不写
            if (r.remaining() < 4) {
                return new CompleteDisplayRequest("", List.of());
            }
            int len = r.u32();
            if (len < 0 || len > r.remaining()) {
                throw new PacketFormatException("COMPLETE_DISPLAY 的摊位名长度 " + len
                        + " 超出包体剩余 " + r.remaining());
            }
            String title = new String(r.bytes(len), TEXT).trim();
            int count = r.u8();
            List<Entry> items = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                items.add(new Entry(r.u16(), r.u32() & 0xFFFF_FFFFL, r.u8(), r.u16(), r.u32()));
            }
            return new CompleteDisplayRequest(title, items);
        }

        public byte[] toBytes() {
            byte[] raw = title.getBytes(TEXT);
            PacketWriter w = new PacketWriter(5 + raw.length + items.size() * 13);
            w.u32(raw.length);
            w.bytes(raw);
            w.u8(items.size());
            for (Entry e : items) {
                w.u16(e.shelfIndex());
                w.u32((int) e.price());
                w.u8(e.itemSpace());
                w.u16(e.invenSlot());
                w.u32(e.count());
            }
            return w.toArray();
        }
    }

    public record StoreView(int sellerUniqueId, String title, boolean displaying,
                            int village, int area, int x, int y) {
    }

    // ---- A4 下行（依据：07_协议检修/社交/摆摊.md，地址为 DNF.exe 读包函数） ----

    public static final int NOTI_CREATE = 78;
    public static final int NOTI_REMOVE = 79;
    public static final int NOTI_FORCE_REMOVE = 80;
    public static final int NOTI_DISPLAY = 81;
    public static final int NOTI_SOLD = 82;
    public static final int NOTI_ITEM = 83;
    public static final int NOTI_LIST = 84;

    /** 摊位标题上限（NOTI 81 / 84 读包 ≤40 字节）。 */
    public static final int TITLE_MAX_BYTES = 40;

    /** 货架一格 21 字节：u16 货架号, u32 单价, u32 itemId, u32 数量, u8 attr, u16 耐久, u32 强化（0041A250 循环）。 */
    public record Shelf(int shelfIndex, long price, int itemId, int count, int itemAttr,
                        int durability, int enchant) {
        void write(PacketWriter w) {
            w.u16(shelfIndex).u32((int) price).u32(itemId).u32(count)
                    .u8(itemAttr).u16(durability).u32(enchant);
        }
    }

    private static byte[] gbk(String s) {
        return s == null ? new byte[0] : s.getBytes(TEXT);
    }

    /** NOTI 78（0042C647）：u16 uid, u8 village, u8 area, u16 x, u16 y —— 别人头顶"准备中"。 */
    public static byte[] notiCreate(int uid, int village, int area, int x, int y) {
        return new PacketWriter(8).u16(uid).u8(village).u8(area).u16(x & 0xFFFF).u16(y & 0xFFFF).toArray();
    }

    /** NOTI 79（0042C781）：u16 uid。 */
    public static byte[] notiRemove(int uid) {
        return new PacketWriter(2).u16(uid).toArray();
    }

    /** NOTI 81（0042C830）：u16 uid, str 标题 —— 别人可以点进来了。 */
    public static byte[] notiDisplay(int uid, String title) {
        byte[] t = gbk(title);
        return new PacketWriter(6 + t.length).u16(uid).u32(t.length).bytes(t).toArray();
    }

    /** NOTI 82 成功（0042C8F3）：给卖家。u8 1, u32 金币, u32 itemId, u16 货架号, u32 数量, str 买家名, u32 成交价, u32 手续费。 */
    public static byte[] notiSold(long sellerGold, int itemId, int shelfIndex, int count,
                                  String buyer, long price, long fee) {
        byte[] n = gbk(buyer);
        return new PacketWriter(27 + n.length).u8(1).u32((int) sellerGold).u32(itemId).u16(shelfIndex)
                .u32(count).u32(n.length).bytes(n).u32((int) price).u32((int) fee).toArray();
    }

    /** NOTI 83（0042CA56）：u16 货架号, u32 卖出数量 —— 给其他正在看这个摊的买家。 */
    public static byte[] notiItem(int shelfIndex, int sold) {
        return new PacketWriter(6).u16(shelfIndex).u32(sold).toArray();
    }

    /** NOTI 84（0042CA9E）：u8 n, n × {u16 uid, str 标题, u8 已开张, u8 village, u8 area, u16 x, u16 y}。 */
    public static byte[] notiList(List<StoreView> stores) {
        PacketWriter w = new PacketWriter(1 + stores.size() * 56);
        w.u8(stores.size());
        for (StoreView v : stores) {
            byte[] t = gbk(v.title());
            w.u16(v.sellerUniqueId()).u32(t.length).bytes(t).u8(v.displaying() ? 1 : 0)
                    .u8(v.village()).u8(v.area()).u16(v.x() & 0xFFFF).u16(v.y() & 0xFFFF);
        }
        return w.toArray();
    }

    /** REPLY 86 成功（0041A1F0）：u8 1, u16 摊主 uid, u8 n, n × Shelf。 */
    public static byte[] replyEnter(int sellerUid, List<Shelf> shelves) {
        PacketWriter w = new PacketWriter(4 + shelves.size() * 21);
        w.u8(1).u16(sellerUid).u8(shelves.size());
        for (Shelf s : shelves) {
            s.write(w);
        }
        return w.toArray();
    }

    /**
     * REPLY 85 成功（0041A043..0041A0B8）：u8 1, u32 金币, u32 胜点, u32 点券, u16 背包槽, u32 itemId,
     * u32 本次买到的数量（客户端同 itemId 是加数量，00475F30）, u8 attr, u16 耐久, u32 强化 = 30 字节。
     */
    public static byte[] replyBought(long gold, long winPoint, long cera, int slot, int itemId,
                                     int count, int itemAttr, int durability, int enchant) {
        return new PacketWriter(30).u8(1).u32((int) gold).u32((int) winPoint).u32((int) cera)
                .u16(slot).u32(itemId).u32(count).u8(itemAttr).u16(durability).u32(enchant).toArray();
    }

    public static byte[] ok() {
        return new byte[]{1};
    }

    public static byte[] fail(int code) {
        return new byte[]{0, (byte) code};
    }
}
