package com.dylan.game;

import com.dylan.net.ServerConfig;
import com.dylan.protocol.io.PacketReader;
import com.dylan.protocol.io.PacketWriter;
import com.dylan.store.FriendStore;

import java.util.List;

/**
 * 好友（A4）。官方枚举没有这几条，单机成品服也不处理；命令号照 A21 ADD_FRIEND 60 / REMOVE_FRIEND 61 整体 +3
 * （和师徒 76→79 同一规律），包体照台服 DisPatcher_AddFriend::read（u32 长度 + 名字），回包全部按 DNF.exe 读包函数：
 * <ul>
 * <li>CMD 63 加好友（2026-10-28 测试服实抓 {@code 06 00 00 00 + 名字}）。REPLY 63（{@code 0041740C}）成功：
 *     str 名字(≤0x1F)、u8 等级、u8 职业、u8 转职（低 4 位 / 高 3 位分开存）、u32 uid、u8、u8 频道（FF = 不在线）。
 *     读完 {@code 009B3420} 入表、提示 806「[%s]已被添加到好友名单」。
 *     失败 u8：4 好友已满（807）、0x12 已在名单（808）、0x15 找不到名字（809）；1 只关等待框（台服加自己回 1）。</li>
 * <li>CMD 64 删好友。REPLY 64（{@code 00417654}）成功 str 名字 → {@code 009B2CC0} 出表；失败 0x15「对方不在好友名单中」（811）。</li>
 * <li>NOTI 56 好友名单（{@code 0042AEB9}）：u8 n，每条 42 字节：u8 频道(FF 不在线)、u8 状态、u8、名字 30 字节(+3)、
 *     u16 等级(+33)、u8 职业(+35，≥5 丢弃)、u8 转职(+36)、u32 uid(+37)、u8(+41)。离开世界时客户端 {@code 0043AA10} 清表。</li>
 * <li>NOTI 54 好友上线 / 换频道（{@code 0042AFFF}）：u8 频道、str 名字、u8 状态 → 「[%s]进入%d频道」（665）。</li>
 * <li>NOTI 55 好友下线（{@code 0042B086}）：u8、str 名字、u8 状态 → 频道改 -1、「[%s]已退出游戏」（667）。</li>
 * </ul>
 * 单向：A 加 B 不用 B 同意，只进 A 的名单（客户端成功分支直接入表，没有等待对方的窗口）。
 */
public final class FriendService {
    private FriendService() {
    }

    static final int MAX_FRIENDS = 50;

    static final int E_CLOSE = 1;
    static final int E_FULL = 4;
    static final int E_ALREADY = 0x12;
    static final int E_NOT_FOUND = 0x15;

    static final int OFFLINE = 0xFF;

    /** 客户端 009E5C90 上限 0x1F（含结尾 0）。 */
    private static final int NAME_MAX = 0x1E;

    private static final int RECORD = 42;

    private static FriendStore db(GameSession s) {
        return s.store().friend();
    }

    private static String name(byte[] body) {
        PacketReader r = new PacketReader(body);
        return r.remaining() < 4 ? "" : r.string(0x20, "好友名字").trim();
    }

    private static byte[] raw(String text) {
        byte[] b = text == null ? new byte[0] : text.getBytes(PacketWriter.DEFAULT_CHARSET);
        return b.length > NAME_MAX ? java.util.Arrays.copyOf(b, NAME_MAX) : b;
    }

    private static GameSession online(int characNo) {
        GameSession[] found = {null};
        SessionRegistry.forEachInWorld((o, p) -> {
            if (found[0] == null && p.characNo() == characNo && o.hasCharacter()) {
                found[0] = o;
            }
        });
        return found[0];
    }

    private static int channelOf(GameSession o) {
        return o == null ? OFFLINE : ServerConfig.a4ChannelForPort(o.localPort()) & 0xFF;
    }

    private static int uidSeenBy(GameSession me, GameSession o) {
        // uid 只在同一频道有意义（客户端 00539C30 按 uid 找本频道的角色）
        return o != null && o.localPort() == me.localPort() ? o.uniqueId() : 0;
    }

    // ---- CMD 63 ----
    public static void add(GameSession s, byte[] body) {
        String who = name(body);
        if (who.isEmpty()) {
            s.reply(63, new byte[]{0, E_NOT_FOUND});
            return;
        }
        if (who.equals(s.characName())) {
            s.reply(63, new byte[]{0, E_CLOSE});
            return;
        }
        FriendStore db = db(s);
        var target = db.byName(who);
        if (target.isEmpty()) {
            s.reply(63, new byte[]{0, E_NOT_FOUND});
            return;
        }
        FriendStore.Person f = target.get();
        if (db.has(s.characNo(), f.characNo())) {
            s.reply(63, new byte[]{0, E_ALREADY});
            return;
        }
        if (db.count(s.characNo()) >= MAX_FRIENDS) {
            s.reply(63, new byte[]{0, E_FULL});
            return;
        }
        db.add(s.characNo(), f.characNo());
        GameSession o = online(f.characNo());
        byte[] n = raw(f.name());
        PacketWriter w = new PacketWriter(16 + n.length);
        w.u8(1).u32(n.length).bytes(n)
                .u8(Math.min(f.level(), 0xFF)).u8(f.job()).u8(f.growType())
                .u32(uidSeenBy(s, o)).u8(0).u8(channelOf(o));
        s.reply(63, w.toArray());
    }

    // ---- CMD 64 ----
    public static void remove(GameSession s, byte[] body) {
        String who = name(body);
        FriendStore db = db(s);
        var target = who.isEmpty() ? java.util.Optional.<FriendStore.Person>empty() : db.byName(who);
        if (target.isEmpty() || !db.has(s.characNo(), target.get().characNo())) {
            s.reply(64, new byte[]{0, E_NOT_FOUND});
            return;
        }
        db.remove(s.characNo(), target.get().characNo());
        byte[] n = raw(target.get().name());
        s.reply(64, new PacketWriter(5 + n.length).u8(1).u32(n.length).bytes(n).toArray());
    }

    /** 进世界：发自己的名单（NOTI 56），再告诉加了我的在线玩家「我上线了」（NOTI 54）。 */
    public static void onEnterWorld(GameSession s) {
        if (!ServerConfig.A4 || !s.hasCharacter() || s.store() == null) {
            return;
        }
        List<FriendStore.Person> list = db(s).friendsOf(s.characNo());
        int n = Math.min(list.size(), 0xFF);
        PacketWriter w = new PacketWriter(1 + n * RECORD);
        w.u8(n);
        for (int i = 0; i < n; i++) {
            FriendStore.Person f = list.get(i);
            GameSession o = online(f.characNo());
            byte[] nm = java.util.Arrays.copyOf(raw(f.name()), 30);
            w.u8(channelOf(o)).u8(0).u8(0).bytes(nm)
                    .u16(Math.min(f.level(), 0xFFFF)).u8(f.job()).u8(f.growType())
                    .u32(uidSeenBy(s, o)).u8(0);
        }
        s.send(56, w.toArray());
        byte[] me = raw(s.characName());
        byte[] up = new PacketWriter(6 + me.length).u8(channelOf(s)).u32(me.length).bytes(me).u8(0).toArray();
        notifyOwners(s, 54, up);
    }

    /** 离开世界（回选角 / 换频道 / 断线）：NOTI 55。换频道时新连接随后会再发 NOTI 54「进入 N 频道」。 */
    public static void onLeaveWorld(GameSession s) {
        if (!ServerConfig.A4 || !s.hasCharacter() || s.store() == null) {
            return;
        }
        byte[] me = raw(s.characName());
        byte[] down = new PacketWriter(6 + me.length).u8(channelOf(s)).u32(me.length).bytes(me).u8(0).toArray();
        notifyOwners(s, 55, down);
    }

    private static void notifyOwners(GameSession s, int noti, byte[] body) {
        List<Integer> owners;
        try {
            owners = db(s).ownersOf(s.characNo());
        } catch (RuntimeException e) {
            return;
        }
        for (int owner : owners) {
            GameSession o = online(owner);
            if (o != null && o != s) {
                o.sendNow(noti, body);
            }
        }
    }
}
