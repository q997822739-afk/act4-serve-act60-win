package com.dylan.game;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 7001 上的 P2P 中转（UDP 走不通时客户端改走 TCP）。外网实抓（2026-10-28，两家宽带组队）：
 *
 * <pre>
 *   连上先发  00 00 0C 00 | identity | identity              （12 字节登记，不回）
 *   之后每包  01 00 [u16 整长] [u32 发送者 identity] [u32 对方 identity] [P2P 包体]  （头 12 字节）
 * </pre>
 *
 * 头 12 字节就是 MTUPD 头（2006 JP：u16 type / u16 length / u32 local_identity / u32 peer_identity），
 * 客户端填的 length 是整帧长度，所以 offset+2 当 u32 读也是同一个数（≤ 65535）。
 * 照台服 Relay（rednf remake DNFServer/GameServer/Relay，TCPHandlerRelay::dispatch / RelayService::relayToTCP）：
 * type 1 按 +8 找对方连接，把 type 改成 3 原样转发，+8 不动。
 *
 * <p>2026-10-28 外网实抓里两端登记帧都是 {@code 0C 00 | 00000000 | 00000000}、数据帧 +4 也是 0：
 * 客户端认自己身份 0（登录回包 udp_identity 发的是 0）。收件人要靠 +4 认人，所以要
 * <b>转发时把 +4 改写成发送者账号 id</b>，与 NOTI 11 每行的 acc 对齐；登录回包现在也填了账号 id，
 * 正常情况下客户端自己就会填对。台服登记帧 +4 带 acc_id；A4 填 0 时仍按来源 IP 对到游戏会话、
 * 再在"和对方同队 / 同决斗房"的会话里认出发送者。
 */
public final class P2PRelay {

    public static final int HEADER = 12;

    private static final Set<Conn> CONNS = ConcurrentHashMap.newKeySet();

    private P2PRelay() {
    }

    public static final class Conn {
        final String ip;
        final OutputStream out;
        public volatile int acc;
        public volatile String lastLog;

        Conn(String ip, OutputStream out) {
            this.ip = ip;
            this.out = out;
        }
    }

    public static Conn open(String ip, OutputStream out) {
        Conn c = new Conn(ip, out);
        CONNS.add(c);
        return c;
    }

    public static void close(Conn c) {
        if (c != null) {
            CONNS.remove(c);
        }
    }

    /** @return 转出去了没有（日志用） */
    public static String forward(Conn from, byte[] frame) {
        if (frame.length < HEADER || frame[0] != 1) {
            // 登记帧 00 00 0C 00 | identity | identity（2006 JP sub_65D590 同格式）。
            // 官方服登录回包 identity 是 0（外网实抓就是 0），现在登录回包填账号 id，
            // 这里就能直接按身份找对方，不用只靠 IP 猜。
            int acc = u32(frame, 4);
            if (acc == 0) {
                acc = u32(frame, 8);
            }
            if (acc != 0) {
                from.acc = acc;
            }
            return "登记帧 acc=" + acc;
        }
        int dest = u32(frame, 8);
        int sender = u32(frame, 4);
        if (from.acc == 0) {
            from.acc = sender != 0 ? sender : senderAcc(from.ip, dest);
        }
        if (from.acc == 0) {
            return "找不到发送者（ip " + from.ip + " 没有和 acc " + dest + " 同队的会话）";
        }
        Conn to = target(from, dest);
        if (to == null) {
            return "acc " + dest + " 还没连中转";
        }
        byte[] copy = frame.clone();
        copy[0] = 3;
        // 收件人靠 +4 认出「是哪个队员发来的」：客户端自己填 0 时补成账号 id（与 NOTI 11 每行 acc 一致）
        putU32(copy, 4, from.acc);
        try {
            synchronized (to.out) {
                to.out.write(copy);
                to.out.flush();
            }
        } catch (IOException e) {
            close(to);
            return "写给 acc " + dest + " 失败：" + e;
        }
        return null;
    }

    private static Conn target(Conn from, int dest) {
        Conn byIp = null;
        String destIp = ipOf(dest);
        for (Conn c : CONNS) {
            if (c == from) {
                continue;
            }
            if (c.acc == dest) {
                return c;
            }
            if (c.acc == 0 && c.ip.equals(destIp)) {
                byIp = c;
            }
        }
        if (byIp != null) {
            byIp.acc = dest;
        }
        return byIp;
    }

    private static String ipOf(int acc) {
        for (GameSession s : SessionRegistry.all()) {
            if (accOf(s) == acc) {
                return s.clientIp();
            }
        }
        return "";
    }

    private static int senderAcc(String ip, int dest) {
        for (GameSession s : SessionRegistry.all()) {
            int acc = accOf(s);
            if (acc == 0 || acc == dest || !ip.equals(s.clientIp())) {
                continue;
            }
            for (GameSession m : peers(s)) {
                if (accOf(m) == dest) {
                    return acc;
                }
            }
        }
        return 0;
    }

    private static List<GameSession> peers(GameSession s) {
        List<GameSession> all = new ArrayList<>(PartyInviteService.members(s));
        Party party = s.party().current();
        if (party != null) {
            all.addAll(party.view().members());
        }
        all.addAll(PvpLobby.roomMates(s));
        return all;
    }

    private static int accOf(GameSession s) {
        return s.account() == null ? 0 : (int) s.account().mId();
    }

    private static int u32(byte[] b, int o) {
        return (b[o] & 0xFF) | (b[o + 1] & 0xFF) << 8 | (b[o + 2] & 0xFF) << 16 | (b[o + 3] & 0xFF) << 24;
    }

    private static void putU32(byte[] b, int o, int v) {
        b[o] = (byte) v;
        b[o + 1] = (byte) (v >>> 8);
        b[o + 2] = (byte) (v >>> 16);
        b[o + 3] = (byte) (v >>> 24);
    }
}
