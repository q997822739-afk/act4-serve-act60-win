package com.dylan.game;

import com.dylan.protocol.io.PacketReader;
import com.dylan.protocol.io.PacketWriter;
import com.dylan.store.MentorStore;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 师徒（A4）。命令号是客户端发包点，不是官方 opcode 枚举（枚举里没有这几条）：
 * CMD 79 邀请 {@code 009B2406}、CMD 80 答应 {@code 008DC430}/{@code 008DC460}（1 接受 / 2 拒绝）、
 * CMD 81 脱离 {@code 009B246C}、CMD 82 拉名单 {@code 009B226A}（进城时也会发，空名单必须仍是 18 字节）。
 * 回包按 DNF.exe 读包函数。同意/拒绝弹窗是窗口类型 0x4A（工厂 {@code 008973AB}），
 * 邀请弹窗是 NOTI 73（见 requestEnter）。谁当师父按等级定（高的当师父），失败码见 E_* 常量。
 */
public final class MentorService {
    private MentorService() {
    }

    /** 客户端 009E5C90：长度 ≥ 0x32 或 0 都走默认串。名字截到 0x31。 */
    private static final int STR_MAX = 0x31;

    /** 被邀请人角色号 → 邀请人角色号。 */
    private static final ConcurrentHashMap<Integer, Integer> PENDING = new ConcurrentHashMap<>();

    private static MentorStore db(GameSession s) {
        return s.store().mentor();
    }

    private static String str(byte[] body) {
        PacketReader r = new PacketReader(body);
        return r.remaining() < 4 ? "" : r.string(0x32, "师徒名字").trim();
    }

    private static void putStr(PacketWriter w, String text) {
        byte[] raw = text == null ? new byte[0] : text.getBytes(PacketWriter.DEFAULT_CHARSET);
        if (raw.length > STR_MAX) {
            raw = java.util.Arrays.copyOf(raw, STR_MAX);
        }
        w.u32(raw.length).bytes(raw);
    }

    /**
     * REPLY 82（00419301）。结果字节之后：一个师傅槽，u8 徒弟数，再跟同样的槽。
     * 每槽 u8 有人、u8、字符串、u8、u8、u32、u32。后四个填 0（00419340 起，其中一字节会进记录 +0x38）。
     * 没师徒时与官方进城回包一致：{@code 01} 加 17 个 0。
     */
    public static byte[] listBody(GameSession s) {
        if (!s.hasCharacter() || s.store() == null) {
            return emptyList();
        }
        int me = s.characNo();
        MentorStore store = db(s);
        Optional<MentorStore.Person> master = store.masterOf(me);
        MentorStore.Person slot;
        List<MentorStore.Person> apprentices;
        if (master.isPresent()) {
            slot = master.get();
            apprentices = store.apprenticesOf(slot.characNo());
        } else {
            apprentices = store.apprenticesOf(me);
            if (apprentices.isEmpty()) {
                return emptyList();
            }
            slot = new MentorStore.Person(me, s.characName());
        }
        PacketWriter w = new PacketWriter(32 + (1 + apprentices.size()) * 24);
        w.u8(1);
        entry(w, slot.name());
        w.u8(Math.min(apprentices.size(), 255));
        int n = 0;
        for (MentorStore.Person a : apprentices) {
            if (n++ >= 255) {
                break;
            }
            entry(w, a.name());
        }
        return w.toArray();
    }

    private static byte[] emptyList() {
        byte[] body = new byte[18];
        body[0] = 1;
        return body;
    }

    private static void entry(PacketWriter w, String name) {
        w.u8(1).u8(0);
        putStr(w, name);
        w.u8(0).u8(0).u32(0).u32(0);
    }

    /*
     * REPLY 79 失败码（0041964C 跳表 0x41DBDC/0x41DBA0，文本 dnf.str）。不在表里的码一律显示
     * 0x5E3「建立师徒关系时出现错误，错误代码 : %d」——之前固定回 0 就是这句。
     */
    /** 0x5DF 您已有一位师父（发起人自己当徒弟、已有师父）。 */
    static final int E_SELF_HAS_MASTER = 0x34;
    /** 0x5E0 徒弟人数已满（发起人当师父、名额满）。 */
    static final int E_SELF_FULL = 0x2C;
    /** 0x5E1 相同Lv的角色，无法建立师徒关系。 */
    static final int E_SAME_LEVEL = 0x2D;
    /** 0x5E2 需达到Lv18以上才能建立师徒关系。 */
    static final int E_MASTER_LEVEL = 0x2E;
    /** 0x5F8 [%s]已有一位师父。 */
    static final int E_TARGET_HAS_MASTER = 0x2B;
    /** 0x5F9 [%s]已无法再收徒。 */
    static final int E_TARGET_FULL = 0x2A;
    /** 0x5F6 您向不在线角色发出了建立师徒关系请求。 */
    static final int E_OFFLINE = 0x31;
    /** 0x5FA 与对方已是师徒。 */
    static final int E_ALREADY = 0x32;
    /** 落到默认文本（错误代码 48）：给自己发、师徒链会变成三层等没有专用文本的情况。 */
    static final int E_OTHER = 0x30;

    /** dnf.str 1525 师徒系统指南：师父 Lv18 以上；徒弟上限 Lv10~19 5 人、Lv20~29 8 人、Lv30 以上 10 人。 */
    static final int MASTER_MIN_LEVEL = 18;

    static int apprenticeCap(int masterLevel) {
        if (masterLevel < MASTER_MIN_LEVEL) {
            return 0;
        }
        return masterLevel < 20 ? 5 : masterLevel < 30 ? 8 : 10;
    }

    /**
     * 指南「若对方Lv低于自身，则成为徒弟；若对方Lv高于自身，则成为师父」：等级高的当师父。
     * 返回失败码，0 = 可以。{@code self} 是发起人（决定用「您…」还是「[%s]…」那句）。
     */
    static int checkPair(MentorStore store, int selfNo, int selfLevel, int otherNo, int otherLevel) {
        if (selfNo == otherNo) {
            return E_OTHER;
        }
        if (selfLevel == otherLevel) {
            return E_SAME_LEVEL;
        }
        boolean selfMaster = selfLevel > otherLevel;
        int masterNo = selfMaster ? selfNo : otherNo;
        int apprenticeNo = selfMaster ? otherNo : selfNo;
        int masterLevel = Math.max(selfLevel, otherLevel);
        Optional<MentorStore.Person> current = store.masterOf(apprenticeNo);
        if (current.isPresent()) {
            if (current.get().characNo() == masterNo) {
                return E_ALREADY;
            }
            return selfMaster ? E_TARGET_HAS_MASTER : E_SELF_HAS_MASTER;
        }
        if (masterLevel < MASTER_MIN_LEVEL) {
            return E_MASTER_LEVEL;
        }
        // 名单（REPLY 82）只有「一个师父 + 他的徒弟」两层：师父不能同时是别人的徒弟，徒弟不能还带徒弟
        if (store.masterOf(masterNo).isPresent() || store.isMaster(apprenticeNo)) {
            return E_OTHER;
        }
        if (store.apprenticesOf(masterNo).size() >= apprenticeCap(masterLevel)) {
            return selfMaster ? E_SELF_FULL : E_TARGET_FULL;
        }
        return 0;
    }

    /** CMD 79。成功 004195C4：结果 1 加一段名字。失败还要再读字符串和 u8，不能只回 {@code 00}。 */
    public static void requestEnter(GameSession s, byte[] body) {
        String name = str(body);
        if (name.isEmpty() || name.equals(s.characName()) || s.store() == null) {
            s.reply(79, fail79(name, E_OTHER));
            return;
        }
        MentorStore store = db(s);
        Optional<MentorStore.Person> target = store.byName(name);
        GameSession other = SessionRegistry.findByName(name);
        if (target.isEmpty() || other == null || other == s || !other.isActive() || !other.hasCharacter()) {
            s.reply(79, fail79(name, E_OFFLINE));
            return;
        }
        int them = target.get().characNo();
        int code = checkPair(store, s.characNo(), s.level(), them, other.level());
        if (code != 0) {
            s.reply(79, fail79(target.get().name(), code));
            return;
        }
        PENDING.put(them, s.characNo());
        PacketWriter ok = new PacketWriter(24);
        ok.u8(1);
        putStr(ok, target.get().name());
        s.reply(79, ok.toArray());
        // NOTI 73（0042C24A）：u16 等级、string 请求人名字 → 客户端开窗口 0x4A（同意/拒绝，按钮发 CMD 80 01/02）。
        // 依据：NOTI 73 handler 读 u16 + 字符串，然后 push 名字、push u16、push 0x4A、call 0x86F320；
        // 窗口 0x4A 的 OnCreate（008DC540）用 text 0x5E8 格式化 "%s…%d" 两个参数（名字 + 数字）。
        // 台服 PacketEnum：NOTI 73 = ENUM_NOTIPACKET_REQUEST_MEMBER_ENTER_TO_RESPONSER（同一编号）。
        other.sendNow(NOTI_REQUEST, noti73(s.level(), s.characName()));
        // 不再顺带 NOTI 74：它首字节 0 时客户端打 0x5E7「[%s]拒绝与您成为师徒关系」（0042C33A），
        // 邀请一发出去对方就看到一句「拒绝」。NOTI 74 只在 CMD 80 回答后发给邀请人。
        // 死路（2026-10-28 IDA 已证）：通用邀请窗 0x27（00420AFF）按 NOTI 7 的 kind 分派，窗口的 OnCreate
        // （CNRDResponsePeerWindow vtable+12 = 0x90F4A0）只认 case 0 组队 / 1 交易 / 2 决斗 / 3、6、7 一行字，
        // 别的 kind（含 4、5）直接 return 0，窗口建完立刻销毁。留这个开关只为别再试一次：默认 0 关掉。
        int kind = Integer.getInteger("s1a5.mentor.noti7Kind", 0);
        if (kind > 0 && kind <= 0xFF) {
            other.sendNow(com.dylan.protocol.body.TradePackets.NOTI_REQUEST_PEER, new PacketWriter(11)
                    .u16(s.uniqueId()).u8(kind).u32(0).u16(0).u16(0).toArray());
        }
        s.flush();
    }

    /** 失败：结果 0、一段字符串（[%s] 那句用）、u8 失败码（见 E_* 常量）。 */
    private static byte[] fail79(String name, int code) {
        PacketWriter w = new PacketWriter(16);
        w.u8(0);
        putStr(w, name == null ? "" : name);
        w.u8(code);
        return w.toArray();
    }

    /**
     * CMD 80。成功 00419798：u8 种类、u8、名字（邀请人）。种类 1 → 0x5E5「[%s]已成为您的师父」、
     * 2 → 0x5E6「[%s]已成为您的徒弟」，别的值 → 0x5F4「已拒绝师徒关系邀请」。失败 00419886 不再读。
     * NOTI 74（0042C2A9）给邀请人：u8 结果、u8 种类、u8、名字（回答人），结果 1 同上两句、0 → 0x5E7 拒绝。
     * 种类都是「名字那个人是你的什么」。
     */
    public static void answer(GameSession s, byte[] body) {
        int kind = body.length == 0 ? 0 : (body[0] & 0xFF);
        Integer inviterNo = PENDING.remove(s.characNo());
        if (inviterNo == null || (kind != 1 && kind != 2) || s.store() == null) {
            s.reply(80, new byte[]{0});
            s.flush();
            return;
        }
        MentorStore store = db(s);
        Optional<MentorStore.Person> inviter = store.byId(inviterNo);
        if (inviter.isEmpty()) {
            s.reply(80, new byte[]{0});
            s.flush();
            return;
        }
        GameSession from = online(inviterNo);
        if (kind != 1) {
            PacketWriter no = new PacketWriter(24);
            no.u8(1).u8(0).u8(0);
            putStr(no, inviter.get().name());
            s.reply(80, no.toArray());
            if (from != null) {
                from.sendNow(74, noti74(0, 0, s.characName()));
            }
            s.flush();
            return;
        }
        // 弹窗到点同意之间双方等级 / 关系可能变了，按 CMD 79 同一套规则再查一遍（邀请人那边视角）
        if (from == null || checkPair(store, inviterNo, from.level(), s.characNo(), s.level()) != 0) {
            s.reply(80, new byte[]{0});
            s.flush();
            return;
        }
        boolean inviterIsMaster = from.level() > s.level();
        if (inviterIsMaster) {
            store.bind(inviterNo, s.characNo());
        } else {
            store.bind(s.characNo(), inviterNo);
        }
        PacketWriter ok = new PacketWriter(24);
        ok.u8(1).u8(inviterIsMaster ? 1 : 2).u8(0);
        putStr(ok, inviter.get().name());
        s.reply(80, ok.toArray());
        // 第一字节 1：第二字节 == 1 用 0x5E5，否则 0x5E6，并 009B2210(1) 再要一次名单。
        from.sendNow(74, noti74(1, inviterIsMaster ? 2 : 1, s.characName()));
        s.flush();
    }

    /** CMD 81。成功 00419517 一段名字。失败 004195B9 只有结果 0。 */
    public static void secede(GameSession s, byte[] body) {
        if (s.store() == null) {
            s.reply(81, new byte[]{0});
            s.flush();
            return;
        }
        String name = str(body);
        MentorStore store = db(s);
        int me = s.characNo();
        Optional<MentorStore.Person> myMaster = store.masterOf(me);
        List<MentorStore.Person> mine = store.apprenticesOf(me);
        if (name.isEmpty() || name.equals(s.characName())) {
            if (myMaster.isPresent()) {
                store.unbindApprentice(me);
                s.reply(81, ok81(s.characName()));
                tell(myMaster.get().characNo(), s.characName());
            } else if (!mine.isEmpty()) {
                for (MentorStore.Person a : mine) {
                    store.unbindApprentice(a.characNo());
                }
                s.reply(81, ok81(s.characName()));
                for (MentorStore.Person a : mine) {
                    tell(a.characNo(), s.characName());
                }
            } else {
                s.reply(81, new byte[]{0});
            }
            s.flush();
            return;
        }
        MentorStore.Person hit = null;
        for (MentorStore.Person a : mine) {
            if (a.name().equals(name)) {
                hit = a;
                break;
            }
        }
        if (hit == null) {
            s.reply(81, new byte[]{0});
            s.flush();
            return;
        }
        store.unbindApprentice(hit.characNo());
        s.reply(81, ok81(hit.name()));
        tell(hit.characNo(), hit.name());
        s.flush();
    }

    private static byte[] ok81(String name) {
        PacketWriter w = new PacketWriter(24);
        w.u8(1);
        putStr(w, name);
        return w.toArray();
    }

    /** NOTI 77（0042C38F）：一段名字，格式化 0x5E9，再 009B2210(1) 重拉名单。 */
    private static void tell(int characNo, String name) {
        GameSession o = online(characNo);
        if (o == null) {
            return;
        }
        PacketWriter w = new PacketWriter(24);
        putStr(w, name);
        o.sendNow(77, w.toArray());
    }

    private static byte[] noti74(int first, int second, String name) {
        PacketWriter w = new PacketWriter(24);
        w.u8(first).u8(second).u8(0);
        putStr(w, name);
        return w.toArray();
    }

    /** 收徒邀请弹窗：{@code u16 等级, string 请求人名字}（见 requestEnter 里的依据）。 */
    static final int NOTI_REQUEST = 73;

    private static byte[] noti73(int level, String name) {
        PacketWriter w = new PacketWriter(24);
        w.u16(Math.max(0, Math.min(0xFFFF, level)));
        putStr(w, name);
        return w.toArray();
    }

    private static GameSession online(int characNo) {
        for (GameSession o : SessionRegistry.playing()) {
            if (o.isActive() && o.hasCharacter() && o.characNo() == characNo) {
                return o;
            }
        }
        return null;
    }

    public static void onLeaveWorld(GameSession s) {
        if (!s.hasCharacter()) {
            return;
        }
        int me = s.characNo();
        PENDING.remove(me);
        PENDING.values().removeIf(v -> v == me);
    }
}
