package com.dylan.game;

import com.dylan.protocol.body.ItemListType;
import com.dylan.protocol.io.PacketReader;
import com.dylan.protocol.io.PacketWriter;
import com.dylan.store.GuildStore;

import com.dylan.store.StoreException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * 公会（A4）。创建 / 信息 / 公告 / 升级 / 查重 / 退出 / 解散的回包照官方 DF60ACT4 实抓（probe_guild.py），
 * 邀请入会（CMD 162/163，NOTI 159/160/58）官方没有，照 A4 客户端读包函数。见 07_协议检修/社交/公会.md。
 */
public final class GuildService {
    private GuildService() {
    }

    // 错误码（官方实抓）
    static final int E_NOT_CHECKED = 0x02;
    static final int E_NO_GOLD = 0x1f;
    static final int E_NO_RIGHT = 0x22;
    static final int E_NO_EXP = 0x54;
    static final int E_MASTER_CANNOT_LEAVE = 0x57;
    static final int E_NAME_LENGTH = 0x6a;
    static final int E_NAME_TAKEN = 0x6b;
    static final int E_NAME_CHARS = 0x6c;
    static final int E_DOMAIN_LENGTH = 0x6e;
    static final int E_DOMAIN_TAKEN = 0x6f;
    static final int E_DOMAIN_CHARS = 0x70;

    /** 官方建公会扣 300000 金币（S2：50000000 → 49700000）。 */
    static final long CREATE_COST = 300_000;
    /** 官方 Lv1→2 要 2000 公会经验、扣 300000 金币（S3）。 */
    static final int LEVEL_UP_EXP = 2000;
    static final long LEVEL_UP_COST = 300_000;

    private static final Pattern DOMAIN = Pattern.compile("[a-z0-9_]{3,12}");
    /** 自动域名不用下划线：客户端本地校验 {@code 008DE750} 只放行 a-z 和 0x30..0x57。 */
    private static final char[] DOMAIN_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    /** 查重通过的名字 / 域名，CMD 71 用（官方 CMD 71 空包，用最近一次 167/168 通过的）。 */
    private record Pending(String name, String domain) {
    }

    private static final Map<GameSession, Pending> PENDING = new ConcurrentHashMap<>();

    private static GuildStore db(GameSession s) {
        return s.store().guild();
    }

    static Optional<GuildStore.Guild> guildOf(GameSession s) {
        return s.hasCharacter() && s.store() != null ? db(s).guildOf(s.characNo()) : Optional.empty();
    }

    public static long idOf(GameSession s) {
        return guildOf(s).map(GuildStore.Guild::id).orElse(0);
    }

    public static String nameOf(GameSession s) {
        return guildOf(s).map(GuildStore.Guild::name).orElse("");
    }

    static String str(byte[] body) {
        PacketReader r = new PacketReader(body);
        return r.remaining() < 4 ? "" : r.string(256, "公会文本").trim();
    }

    private static byte[] ok() {
        return new byte[]{1};
    }

    /**
     * REPLY 164 成功（00418F81）：结果 1、u8 种类、两段 009E5C90（最长 0x31）。
     * 00401D40 按 snprintf(dest, size, fmt, 第二段, 第一段) 吃。
     * 种类 1 格式化 0x1A2C（自己退），种类 2 格式化 0x1A2D（踢人）。只回 {@code 01} 时客户端会把下一包的字节当成种类。
     */
    private static byte[] secedeOk(int kind, String secondIsFirstFormatArg, String firstIsSecondFormatArg) {
        PacketWriter w = new PacketWriter(48);
        w.u8(1).u8(kind);
        putFit(w, firstIsSecondFormatArg);
        putFit(w, secondIsFirstFormatArg);
        return w.toArray();
    }

    private static void putFit(PacketWriter w, String text) {
        byte[] raw = text == null ? new byte[0] : text.getBytes(PacketWriter.DEFAULT_CHARSET);
        if (raw.length >= 0x32) {
            raw = java.util.Arrays.copyOf(raw, 0x31);
        }
        w.u32(raw.length).bytes(raw);
    }

    private static byte[] fail(int code) {
        return new byte[]{0, (byte) code};
    }

    // ---- CMD 167 / 168 查重 ----
    public static void checkName(GameSession s, byte[] body) {
        String name = str(body);
        int bytes = name.getBytes(PacketWriter.DEFAULT_CHARSET).length;
        int err = bytes < 2 || bytes > 14 ? E_NAME_LENGTH
                : name.contains(" ") || name.contains("_") ? E_NAME_CHARS
                : db(s).nameTaken(name) ? E_NAME_TAKEN : 0;
        if (err != 0) {
            s.reply(167, fail(err));
            return;
        }
        Pending prev = PENDING.get(s);
        String domain = prev == null ? null : prev.domain();
        PENDING.put(s, new Pending(name, domain));
        s.reply(167, ok());
        // 创建按钮（008DF160）要名字标志 d7f2db 和网址标志 d7f2dc 都是 1，
        // 而且输入框文本还得等于点查重时抄下来的那份。d7f2dc 只有 REPLY 168 成功
        // （00418AF8）才置 1。网址框客户端不可编辑，玩家发不出 168。
        // 名字通过且还没有域名时补一发 01：保存的域名（0089BCDA 清成 0）和空输入框相等，按钮才会发 CMD 71。
        if (domain == null) {
            s.reply(168, ok());
        }
    }

    public static void checkDomain(GameSession s, byte[] body) {
        String d = str(body);
        int err = d.length() < 3 || d.length() > 12 ? E_DOMAIN_LENGTH
                : !DOMAIN.matcher(d).matches() ? E_DOMAIN_CHARS
                : db(s).domainTaken(d) ? E_DOMAIN_TAKEN : 0;
        if (err == 0) {
            PENDING.merge(s, new Pending(null, d), (a, b) -> new Pending(a.name(), d));
        }
        s.reply(168, err == 0 ? ok() : fail(err));
    }

    /** 8 位 [a-z0-9]。3..12 且不含下划线，和 CMD 168 的长度、字符规则一致。 */
    private static String randomDomain(GuildStore db) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 32; attempt++) {
            char[] chars = new char[8];
            for (int i = 0; i < chars.length; i++) {
                chars[i] = DOMAIN_ALPHABET[random.nextInt(DOMAIN_ALPHABET.length)];
            }
            String domain = new String(chars);
            if (!db.domainTaken(domain)) {
                return domain;
            }
        }
        return null;
    }

    // ---- CMD 71 建公会 ----
    public static void create(GameSession s) {
        Pending p = PENDING.get(s);
        if (guildOf(s).isPresent() || p == null || p.name() == null || db(s).nameTaken(p.name())) {
            s.reply(71, fail(E_NOT_CHECKED));
            return;
        }
        String domain = p.domain();
        if (domain == null || domain.isEmpty() || db(s).domainTaken(domain)) {
            domain = randomDomain(db(s));
        }
        if (domain == null) {
            s.reply(71, fail(E_NOT_CHECKED));
            return;
        }
        if (s.inventory().money() < CREATE_COST) {
            s.reply(71, fail(E_NO_GOLD));
            return;
        }
        long before = s.inventory().money();
        s.inventory().setMoney(before - CREATE_COST);
        s.inventory().save();
        int id;
        try {
            id = db(s).create(p.name(), domain, s.characNo());
        } catch (StoreException e) {
            s.inventory().setMoney(before);
            s.inventory().save();
            s.reply(71, fail(E_NOT_CHECKED));
            return;
        }
        PENDING.remove(s);
        // 官方顺序：REPLY 71 → NOTI 13/0 → NOTI 192 → NOTI 71 → NOTI 2/0 → NOTI 70 → NOTI 139 01
        s.reply(71, ok());
        s.inventory().sendItemList(ItemListType.LIST_MAIN, "建公会扣金币");
        byte[] name = p.name().getBytes(PacketWriter.DEFAULT_CHARSET);
        s.send(71, new PacketWriter(8 + name.length).u32(id).u32(name.length).bytes(name).toArray());
        CharacterService.resendOwnCharacterInfo(s, "建公会");
        s.send(70, noti70(p.name()));
        s.send(139, new byte[]{1});
        s.flush();
    }

    /** NOTI 70：str 公会名, u32 0, u8 1, 再 21 字节 0（官方名字 6 字节时共 36 字节）。 */
    private static byte[] noti70(String name) {
        byte[] n = name.getBytes(PacketWriter.DEFAULT_CHARSET);
        return new PacketWriter(30 + n.length).u32(n.length).bytes(n).u32(0).u8(1).bytes(new byte[21]).toArray();
    }

    // ---- CMD 70 / 150 公会信息 ----
    public static void info(GameSession s, int cmd) {
        Optional<GuildStore.Guild> g = guildOf(s);
        if (g.isEmpty()) {
            s.reply(cmd, new byte[]{0});
            return;
        }
        List<GuildStore.Member> members = db(s).members(g.get().id());
        byte[] name = g.get().name().getBytes(PacketWriter.DEFAULT_CHARSET);
        PacketWriter w = new PacketWriter(16 + name.length + members.size() * 30);
        // REPLY 70（00418796）：u8 1, u32 公会号, str 名, u32 0, u16 成员数, u8 成员数, n × 成员
        w.u8(1).u32(g.get().id()).u32(name.length).bytes(name).u32(0).u16(members.size()).u8(members.size());
        for (GuildStore.Member m : members) {
            byte[] mn = m.name().getBytes(PacketWriter.DEFAULT_CHARSET);
            boolean online = SessionRegistry.findByName(m.name()) != null;
            // 成员：str 名, str 空, u16 等级, u8 职业, u8 转职, u8 是自己(01 / ff), u8 0, u8 在线, u8 会长(1/0), u32 角色号
            // （S4 会长看：会长 01 00 01 01、成员 ff 00 00 00；S5 成员看：会长 ff 00 00 01、成员 01 00 01 00）
            boolean self = m.characNo() == s.characNo();
            w.u32(mn.length).bytes(mn).u32(0).u16(m.level()).u8(m.job()).u8(m.growType())
                    .u8(self ? 1 : 0xFF).u8(0).u8(online ? 1 : 0)
                    .u8(m.grade() == GuildStore.GRADE_MASTER ? 1 : 0).u32(m.characNo());
        }
        s.reply(cmd, w.toArray());
    }

    // ---- CMD 165 公告 ----
    public static void setNotice(GameSession s, byte[] body) {
        Optional<GuildStore.Guild> g = guildOf(s);
        if (g.isEmpty() || g.get().master() != s.characNo()) {
            s.reply(165, fail(E_NO_RIGHT));
            return;
        }
        String notice = str(body);
        db(s).setNotice(g.get().id(), notice);
        s.reply(165, ok());
        byte[] n = notice.getBytes(PacketWriter.DEFAULT_CHARSET);
        byte[] noti = new PacketWriter(4 + n.length).u32(n.length).bytes(n).toArray();
        for (GameSession o : online(g.get().id(), s)) {
            o.sendNow(153, noti);
        }
    }

    // ---- CMD 76 升级 ----
    public static void levelUp(GameSession s) {
        Optional<GuildStore.Guild> g = guildOf(s);
        if (g.isEmpty() || g.get().master() != s.characNo()) {
            s.reply(76, fail(E_NO_RIGHT));
            return;
        }
        if (g.get().exp() < LEVEL_UP_EXP * g.get().level() || s.inventory().money() < LEVEL_UP_COST) {
            s.reply(76, fail(E_NO_EXP));
            return;
        }
        s.inventory().setMoney(s.inventory().money() - LEVEL_UP_COST);
        s.inventory().save();
        db(s).setLevel(g.get().id(), g.get().level() + 1);
        s.reply(76, ok());
        s.inventory().sendItemList(ItemListType.LIST_MAIN, "公会升级扣金币");
        s.flush();
    }

    /** 公会在线成员（含 self）。 */
    static List<GameSession> online(int guildId, GameSession self) {
        List<GameSession> out = new java.util.ArrayList<>();
        for (GameSession o : SessionRegistry.playing()) {
            if (o == self || (o.isActive() && idOf(o) == guildId)) {
                out.add(o);
            }
        }
        return out;
    }

    // ---- CMD 164 退出 / 踢人（str 名字 = 踢人，4 字节 0 = 自己退；官方会长退、踢人都回 57） ----
    public static void secede(GameSession s, byte[] body) {
        Optional<GuildStore.Guild> g = guildOf(s);
        if (g.isEmpty()) {
            s.reply(164, fail(E_NO_RIGHT));
            return;
        }
        String target = str(body);
        boolean master = g.get().master() == s.characNo();
        if (target.isEmpty() || target.equals(s.characName())) {
            if (master) {
                s.reply(164, fail(E_MASTER_CANNOT_LEAVE));
                return;
            }
            db(s).leave(s.characNo());
            // 第二段是名字（格式串第一个 %s），第一段是公会名。
            s.reply(164, secedeOk(1, s.characName(), g.get().name()));
            CharacterService.resendOwnCharacterInfo(s, "退出公会");
            for (GameSession o : online(g.get().id(), s)) {
                if (o != s) {
                    ChatService.system(o, s.characName() + " 离开了公会");
                }
            }
            s.flush();
            return;
        }
        GuildStore.Member victim = db(s).members(g.get().id()).stream()
                .filter(m -> m.name().equals(target)).findFirst().orElse(null);
        if (!master || victim == null || victim.characNo() == s.characNo()) {
            // 0x57 的提示是「会长不能退」。名字不在会里不要用这个码。
            // 错误码 0：减 3 之后 00419075 的无符号比较落在表外，客户端丢掉、不读穿。
            s.reply(164, fail(0));
            return;
        }
        db(s).leave(victim.characNo());
        // 第二段是踢人者，第一段是被踢的人。
        s.reply(164, secedeOk(2, s.characName(), victim.name()));
        CharacterService.resendOwnCharacterInfo(s, "请出成员");
        GameSession v = SessionRegistry.findByName(target);
        if (v != null) {
            CharacterService.resendOwnCharacterInfo(v, "被请出公会");
            ChatService.system(v, "你被请出了公会");
            v.flush();
        }
        for (GameSession o : online(g.get().id(), s)) {
            if (o != s && o != v) {
                ChatService.system(o, victim.name() + " 被请出了公会");
            }
        }
        s.flush();
    }

    // ---- CMD 166 转让会长 ----
    public static void appoint(GameSession s, byte[] body) {
        Optional<GuildStore.Guild> g = guildOf(s);
        String target = str(body);
        GuildStore.Member to = g.isEmpty() ? null : db(s).members(g.get().id()).stream()
                .filter(m -> m.name().equals(target)).findFirst().orElse(null);
        if (g.isEmpty() || g.get().master() != s.characNo() || to == null || to.characNo() == s.characNo()) {
            s.reply(166, fail(E_NO_RIGHT));
            return;
        }
        db(s).setMaster(g.get().id(), s.characNo(), to.characNo());
        // REPLY 166 成功（004190AD）只有结果 1，提示 0x1A2E。失败才再读一个 u8（0x22 / 0x24 / 0x56）。
        s.reply(166, ok());
        CharacterService.resendOwnCharacterInfo(s, "转让会长");
        GameSession next = SessionRegistry.findByName(target);
        if (next != null) {
            CharacterService.resendOwnCharacterInfo(next, "成为会长");
            ChatService.system(next, "你成为了会长");
            next.flush();
        }
        s.flush();
    }

    // ---- CMD 194 解散：NOTI 188 00 00 00 00 01 00 → NOTI 2/0 → NOTI 139 05；没公会 NOTI 188 01 00 00 00 ----
    public static void dismiss(GameSession s) {
        Optional<GuildStore.Guild> g = guildOf(s);
        if (g.isEmpty() || g.get().master() != s.characNo()) {
            s.sendNow(188, new byte[]{1, 0, 0, 0});
            return;
        }
        List<GameSession> members = online(g.get().id(), s);
        db(s).dismiss(g.get().id());
        s.send(188, new byte[]{0, 0, 0, 0, 1, 0});
        // 每个成员重发自己的 NOTI 2/0（公会号清 0），announceIdentity 会发给同区域所有人
        for (GameSession o : members) {
            CharacterService.resendOwnCharacterInfo(o, "公会解散");
            o.flush();
        }
        s.send(139, new byte[]{5});
        s.flush();
    }

    // ---- 邀请入会（官方没有；CMD 162 → NOTI 159 → CMD 163 → NOTI 160 + NOTI 58） ----
    private static final Map<GameSession, GameSession> INVITES = new ConcurrentHashMap<>();

    /** REPLY 162 失败码（004191A5 跳表 0041db54）：3 不在游戏中 0x19b1、0x24 无权限 0x19af、0x27 已有公会 0x19b3。 */
    static final int E_INVITE_OFFLINE = 3;
    static final int E_INVITE_NO_RIGHT = 0x24;
    static final int E_INVITE_HAS_GUILD = 0x27;

    public static void invite(GameSession s, byte[] body) {
        Optional<GuildStore.Guild> g = guildOf(s);
        GameSession target = SessionRegistry.findByName(str(body));
        if (g.isEmpty() || g.get().master() != s.characNo()) {
            s.reply(162, fail(E_INVITE_NO_RIGHT));
            return;
        }
        if (target == null || target == s) {
            s.reply(162, fail(E_INVITE_OFFLINE));
            return;
        }
        if (guildOf(target).isPresent()) {
            s.reply(162, fail(E_INVITE_HAS_GUILD));
            return;
        }
        INVITES.put(target, s);
        s.reply(162, ok());
        // NOTI 159（0042BFFB）：str 邀请人名, str 公会名
        byte[] who = s.characName().getBytes(PacketWriter.DEFAULT_CHARSET);
        byte[] gn = g.get().name().getBytes(PacketWriter.DEFAULT_CHARSET);
        target.sendNow(159, new PacketWriter(8 + who.length + gn.length)
                .u32(who.length).bytes(who).u32(gn.length).bytes(gn).toArray());
    }

    public static void answer(GameSession s, byte[] body) {
        GameSession inviter = INVITES.remove(s);
        boolean yes = body.length > 0 && body[0] == 1;
        Optional<GuildStore.Guild> g = inviter == null ? Optional.empty() : guildOf(inviter);
        if (inviter == null || !inviter.isActive() || g.isEmpty()) {
            return;
        }
        byte[] me = s.characName().getBytes(PacketWriter.DEFAULT_CHARSET);
        if (!yes || guildOf(s).isPresent()) {
            // NOTI 160（0042C095）：u8 0, str 名字 → 0x19ab"玩家[%s]拒绝了您的公会邀请"
            inviter.sendNow(160, new PacketWriter(5 + me.length).u8(0).u32(me.length).bytes(me).toArray());
            return;
        }
        db(s).join(s.characNo(), g.get().id());
        inviter.sendNow(160, new PacketWriter(5 + me.length).u8(1).u32(me.length).bytes(me).toArray());
        // NOTI 58（0042B65D）：u32 公会号, u32 角色号, str 名字, str 公会名 → "[%s]已加入[%s]公会"
        byte[] gn = g.get().name().getBytes(PacketWriter.DEFAULT_CHARSET);
        byte[] noti58 = new PacketWriter(16 + me.length + gn.length).u32(g.get().id()).u32(s.characNo())
                .u32(me.length).bytes(me).u32(gn.length).bytes(gn).toArray();
        for (GameSession o : online(g.get().id(), s)) {
            o.sendNow(58, noti58);
        }
        CharacterService.resendOwnCharacterInfo(s, "加入公会");
        s.flush();
    }

    /** 公会聊天收件人：CMD 132 频道 9（页签 6）。 */
    public static List<GameSession> chatRecipients(GameSession s) {
        long id = idOf(s);
        return id == 0 ? List.of() : online((int) id, s);
    }

    public static void onLeaveWorld(GameSession s) {
        PENDING.remove(s);
        INVITES.remove(s);
        INVITES.values().removeIf(v -> v == s);
    }
}
