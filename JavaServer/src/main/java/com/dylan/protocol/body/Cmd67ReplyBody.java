package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public final class Cmd67ReplyBody {

    public static final int PACKET_ID = 67;

    public static final int FAILURE_LENGTH = 23;

    public static final int SUCCESS_LENGTH = 32;

    public static final long NO_ITEM = 0xFFFF_FFFFL;

    public static final int SINGLE = 1;

    private Cmd67ReplyBody() {
    }

    public static byte[] success(int category, long commodityNo, long itemId,
                                 int seq, int total, boolean gift) {
        return success(category, commodityNo, itemId, seq, total, gift, List.of(), NO_ITEM, 0);
    }

    public static byte[] success(int category, long commodityNo, long itemId,
                                 int seq, int total, boolean gift,
                                 List<BundleItem> bundleItems) {
        return success(category, commodityNo, itemId, seq, total, gift,
                bundleItems, NO_ITEM, 0);
    }

    public static byte[] success(int category, long commodityNo, long itemId,
                                 int seq, int total, boolean gift,
                                 List<BundleItem> bundleItems, long bonusItemId, long bonusCount) {
        checkCategory(category);
        checkSeq(seq, total);
        checkU32("commodityNo", commodityNo);
        checkU32("itemId", itemId);
        checkU32("bonusItemId", bonusItemId);
        checkU32("bonusCount", bonusCount);
        if (bundleItems.size() > 0xFFFF) {
            throw new PacketFormatException("礼包子物品 " + bundleItems.size() + " 项超出 u16");
        }

        var w = new PacketWriter(SUCCESS_LENGTH + bundleItems.size() * 8)
                .u8(1)
                .u8(gift ? 1 : 0)
                .u32((int) category)
                .u32((int) commodityNo)
                .u32(seq)
                .u32(total)
                .u32((int) itemId)
                .u16(bundleItems.size());
        for (var sub : bundleItems) {
            checkU32("礼包子物品 itemId", sub.itemId());
            checkU32("礼包子物品数量", sub.count());
            w.u32((int) sub.itemId());
            w.u32((int) sub.count());
        }
        byte[] body = w.u32((int) bonusItemId).u32((int) bonusCount).toArray();
        if (bundleItems.isEmpty() && body.length != SUCCESS_LENGTH) {
            throw new PacketFormatException("单件成功应答应当是 " + SUCCESS_LENGTH
                    + " 字节，实际 " + body.length);
        }
        return body;
    }

    /**
     * A4 成功应答（客户端 DNF.exe 0041834E）：u8 1、u8 2、u32 类别、u32 商品号、u32 seq、u32 total、
     * u32 物品（官方恒 0）、u16 条数 + 条数×(u32 物品, u32 数量)、u32 赠品、u32 赠品数。
     * 客户端只靠这张表把东西记进背包，普通商品也要写成 1 条（官方 100044 → {@code 01 00 15000000 05000000}）；
     * 赠品「没有」是 {@code FFFF}，客户端拿 u32 跟 0xFFFF 比，写 FFFFFFFF 会去发一件不存在的赠品。
     */
    public static byte[] a4Success(int category, long commodityNo, long itemId, int count,
                                   int seq, int total, List<BundleItem> bundleItems) {
        checkCategory(category);
        checkSeq(seq, total);
        List<BundleItem> list = bundleItems.isEmpty()
                ? List.of(new BundleItem(itemId, Math.max(1, count))) : bundleItems;
        var w = new PacketWriter(SUCCESS_LENGTH + list.size() * 8)
                .u8(1)
                .u8(2)
                .u32(category)
                .u32((int) commodityNo)
                .u32(seq)
                .u32(total)
                .u32(0)
                .u16(list.size());
        for (var sub : list) {
            w.u32((int) sub.itemId());
            w.u32((int) sub.count());
        }
        return w.u32(A4_NO_BONUS).u32(0).toArray();
    }

    /** A4 赠品「没有」：客户端 {@code cmp eax, 0xffff}。 */
    public static final int A4_NO_BONUS = 0xFFFF;

    /**
     * A4 失败应答 21 字节：u8 0、u8 0、u8 错误码、u32 类别、u32 商品号、seq 0、total 0、u16 0。
     * 官方一次请求只回这一条（不管几件），seq = total = 0 让客户端解锁商城。
     * 点券不足官方是 4（日志「商城购买失败 cash=0」时回 {@code 00 00 04}）。
     */
    public static byte[] a4Failure(CashShopError error, int category, long commodityNo) {
        int code = error == CashShopError.NOT_ENOUGH_CERA ? A4_NOT_ENOUGH_CERA : error.code();
        return new PacketWriter(A4_FAILURE_LENGTH)
                .u8(0)
                .u8(0)
                .u8(code)
                .u32(category)
                .u32((int) commodityNo)
                .u32(0)
                .u32(0)
                .u16(0)
                .toArray();
    }

    /** A4 失败回包固定 21 字节（官方实抓）。 */
    public static final int A4_FAILURE_LENGTH = 21;

    /** A4 官方「点券不足」错误码（A5 CashShopError 的编号与 A4 不同）。 */
    public static final int A4_NOT_ENOUGH_CERA = 4;

    public static byte[] failure(CashShopError error, int category, long commodityNo,
                                 boolean gift, int seq, int total) {
        checkCategory(category);
        checkSeq(seq, total);
        checkU32("commodityNo", commodityNo);
        // A4 客户端失败分支（DNF.exe 004185C9）：第 2 字节只用来选日志文案，第 3 字节才是错误码
        // （官方 00 00 04 …），与 ACT5 的「错误码在前」相反。
        boolean a4 = com.dylan.net.ServerConfig.A4;
        byte[] body = new PacketWriter(FAILURE_LENGTH)
                .u8(0)
                .u8(a4 ? (gift ? 1 : 0) : error.code())
                .u8(a4 ? error.code() : (gift ? 1 : 0))
                .u32((int) category)
                .u32((int) commodityNo)
                .u32(seq)
                .u32(total)
                .u32(0)
                .toArray();
        if (body.length != FAILURE_LENGTH) {
            throw new PacketFormatException("失败应答应当是 " + FAILURE_LENGTH
                    + " 字节，实际 " + body.length);
        }
        return body;
    }

    private static void checkSeq(int seq, int total) {
        if (total < 1 || seq < 1 || seq > total) {
            throw new PacketFormatException("seq/total " + seq + "/" + total
                    + " 不合法：seq 要从 1 数到 total，最后一笔必须相等");
        }
    }

    public static byte[] failure(CashShopError error) {
        return failure(error, com.dylan.pvf.script.CashShopCatalog.CATEGORY_UNKNOWN,
                NO_ITEM, false, SINGLE, SINGLE);
    }

    public record BundleItem(long itemId, long count) {
    }

    private static void checkCategory(int category) {
        if (category < -1 || category > 6) {
            throw new PacketFormatException("category " + category
                    + " 超出客户端那七个商品分桶能认的 -1..6");
        }
    }

    private static void checkU32(String field, long value) {
        if (value < 0 || value > 0xFFFF_FFFFL) {
            throw new PacketFormatException(field + " " + value + " 超出 u32");
        }
    }
}
