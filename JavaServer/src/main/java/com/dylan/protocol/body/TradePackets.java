package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketWriter;

/**
 * A4 交易 / 邀请的包体。依据：07_协议检修/社交/交易.md（DNF.exe 读包函数地址写在各方法上）。
 */
public final class TradePackets {
    private TradePackets() {
    }

    public static final int KIND_PARTY = 0;
    public static final int KIND_TRADE = 1;

    public static final int NOTI_REQUEST_PEER = 7;
    public static final int NOTI_RESPONSE_PEER = 8;
    public static final int NOTI_CHANGE_ITEM = 15;
    public static final int NOTI_CANCEL = 16;
    public static final int NOTI_STATE = 17;
    public static final int NOTI_FINISH = 18;

    /** REPLY 10 / 11 错误码（00411275 / 0041159C）。 */
    public static final int E_GONE = 3;
    public static final int E_BUSY_TRADE = 0x13;
    public static final int E_REFUSED = 0x85;

    /** CMD 10 / 11：u16 uid, u8 kind, u32 peer [, u16 拒绝码]（发包点 00476A12 / 0090F294 / 0090F3F9）。 */
    public record PeerRequest(int uid, int kind, int peer, int refuse) {
        public static PeerRequest parse(byte[] b) {
            var r = new com.dylan.protocol.io.PacketReader(b);
            int uid = r.u16();
            int kind = r.u8();
            int peer = r.remaining() >= 4 ? r.u32() : 0;
            int refuse = r.remaining() >= 2 ? r.u16() : 0;
            return new PeerRequest(uid, kind, peer, refuse);
        }
    }

    public static byte[] ok() {
        return new byte[]{1};
    }

    /** REPLY 10 / 11 失败：00, u8 错误, u8 kind。 */
    public static byte[] fail(int error, int kind) {
        return new byte[]{0, (byte) error, (byte) kind};
    }

    /** CMD 26 SetItemTradeState 的包号。 */
    public static final int PACKET_ID_STATE = 26;

    /**
     * REPLY 26 的错误码。
     *
     * <p>依据：A4 官方实抓（sweep_town CMD 26，城里包体全 0、根本没在交易）回 {@code 00 11}；
     * catalog_cmd 的日志模板「命令 26：已拒绝未实现的物品交易状态，物品保持不变」。官方单机不做交易，
     * 对任何 CMD 26 都回这一条；A4 客户端发锁定/确认（00476AA0）后等回包，所以没会话也要回。
     */
    public static final int E_NOT_TRADING = 0x11;

    /** REPLY 26：00, u8 错误码（没有 kind 字节，和 REPLY 10/11 不同）。 */
    public static byte[] stateRejected() {
        return new byte[]{0, (byte) E_NOT_TRADING};
    }

    /** NOTI 7（0042096C kind 1）：u16 邀请人, u8 1, u32 peer, u32 时间戳。 */
    public static byte[] invite(int fromUid, int peer) {
        return new PacketWriter(11).u16(fromUid).u8(KIND_TRADE).u32(peer)
                .u32((int) (System.currentTimeMillis() / 1000)).toArray();
    }

    /** REPLY 11 给接受方（004114AD kind 1）：01, u16 邀请人, u8 1, u8 1。 */
    public static byte[] acceptedReply(int inviterUid) {
        return new PacketWriter(5).u8(1).u16(inviterUid).u8(KIND_TRADE).u8(1).toArray();
    }

    /** NOTI 8 给邀请人（00420C2F）：u16 接受方, u8 1, u8 1, u32 0。 */
    public static byte[] acceptedNoti(int responderUid) {
        return new PacketWriter(8).u16(responderUid).u8(KIND_TRADE).u8(1).u32(0).toArray();
    }

    /** NOTI 15（00422D9B）17 字节：u16 格, u32 itemId（0xFFFF = 清空）, u32 数量, u8 attr, u16 耐久, u32 强化。 */
    public static byte[] item(int tradeSlot, int itemId, long count, int attr, int durability, int enchant) {
        return new PacketWriter(17).u16(tradeSlot).u32(itemId).u32((int) count)
                .u8(attr).u16(durability).u32(enchant).toArray();
    }

    public static byte[] cleared(int tradeSlot) {
        return item(tradeSlot, 0xFFFF, 0, 0, 0, 0);
    }

    /** NOTI 17（00423412）：u16 uid, u8 state。 */
    public static byte[] state(int uid, int state) {
        return new PacketWriter(3).u16(uid).u8(state).toArray();
    }

    /** NOTI 16 / 18：u16 映射条数 0（客户端清临时交易栏，之后重发主背包）。 */
    public static byte[] closed() {
        return new byte[]{0, 0};
    }
}
