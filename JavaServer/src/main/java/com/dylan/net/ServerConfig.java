package com.dylan.net;

import java.nio.file.Path;

public final class ServerConfig {
    private ServerConfig() {
    }

    private static String str(String key, String fallback) {
        String v = System.getProperty("s1a5." + key);
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }

    private static int num(String key, int fallback) {
        try {
            return Integer.parseInt(str(key, Integer.toString(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean flag(String key, boolean fallback) {
        return !"0".equals(str(key, fallback ? "1" : "0"));
    }

    public static final int PORT = num("port", 10011);

    public static final int MY_GROUP = 2;

    public static final int MY_CHANNEL = 10;

    /** A4 频道 NOTI 0 的 16 字节令牌（ASCII 补零）。官方成品服发 "dnf2008-local"。 */
    public static final String A4_CHANNEL_TOKEN = str("a4.token", "dnf2008-local");

    /** A4 频道 NOTI 1 里的组号、频道号。官方成品服 7002 端口回 1/1。 */
    public static final int A4_GROUP = num("a4.group", 1);

    public static final int A4_CHANNEL = num("a4.channel", 1);

    /**
     * A4 频道 → 游戏服端口。官方成品服：1→7002、6→7003、51→7004。
     * 入口服按这张表下发频道列表（协议 18），游戏服逐个监听，NOTI 1 按被连的端口回频道号。
     * 三处必须用同一张表，所以只在这里定义。格式 {@code 频道:端口,...}。
     */
    public static final java.util.Map<Integer, Integer> A4_CHANNEL_PORTS =
            parseChannelPorts(str("a4.channels", "1:7002,6:7003,51:7004"));

    private static java.util.Map<Integer, Integer> parseChannelPorts(String spec) {
        java.util.Map<Integer, Integer> out = new java.util.LinkedHashMap<>();
        for (String part : spec.split(",")) {
            String[] kv = part.trim().split(":");
            if (kv.length != 2) {
                throw new IllegalArgumentException("s1a5.a4.channels 格式是 频道:端口,...，读到 " + spec);
            }
            out.put(Integer.parseInt(kv[0].trim()), Integer.parseInt(kv[1].trim()));
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    /** 按 A4（DNF.exe 1.0.44.1）序列化。频道表给空（-Ds1a5.a4.channels= 不行，用 -Ds1a5.a4=0）就退回 ACT5。 */
    public static final boolean A4 = flag("a4", true) && !A4_CHANNEL_PORTS.isEmpty();

    /**
     * A4 客户端没有、A5 进世界时会发的 NOTI。官方成品服整份日志一次都没出现过：
     * 191 商城币活动、187 游戏选项、207 物品锁、221 副职业。下行编码器直接丢掉，不改发它们的服务。
     * 191 的内容 A4 改用 NOTI 178 发（DailyGrantService.A4_NOTI_OPENFLAG），不是没有这个功能。
     */
    public static final java.util.Set<Integer> A4_DROPPED_NOTI =
            // 208：A4 客户端（0042AD36）一字节不读，只弹固定提示，与 207/221 同类
            parseIds(str("a4.dropNoti", "191,187,207,208,221"));

    private static java.util.Set<Integer> parseIds(String spec) {
        java.util.Set<Integer> out = new java.util.TreeSet<>();
        for (String part : spec.split(",")) {
            if (!part.isBlank()) {
                out.add(Integer.parseInt(part.trim()));
            }
        }
        return java.util.Collections.unmodifiableSet(out);
    }

    /**
     * A4 频道 → 登录回包（CMD 1）第 4 字节"频道类型"。官方成品服 1→2、6→3、51→4，
     * 与 channel_info.etc 频道行第 3 列一致。
     */
    public static final java.util.Map<Integer, Integer> A4_CHANNEL_KINDS =
            parseChannelPorts(str("a4.channelKinds", "1:2,6:3,51:4"));

    /**
     * A4 建角时武器槽（9）的初始武器，按职业。官方成品服每个职业建一次角色实测（probe_jobs.py）：
     * 0 鬼剑士 27900、1 格斗家 29000、2 神枪手 31000、3 魔法师 33300、4 圣职者 35000。
     * 不是一律 *_default.equ（魔法师给的是 pole_apprentice），所以列表写死。格式 {@code 职业:物品,...}，给空就不发。
     */
    public static final java.util.Map<Integer, Integer> A4_STARTER_WEAPONS = starterWeapons(
            str("a4.starterWeapons", "0:27900,1:29000,2:31000,3:33300,4:35000"));

    private static java.util.Map<Integer, Integer> starterWeapons(String spec) {
        return spec.isBlank() ? java.util.Map.of() : parseChannelPorts(spec);
    }

    /**
     * A4 教程最终模块 31 完成后，官方成品服替客户端接下并推进的任务（日志「教程完成任务已激活并可交付
     * quest=1016」：CMD 33 → CMD 35 trigger 0 → NOTI 21）。客户端随后直接发 CMD 35 / 36 交任务，
     * 服务端没接就会回 NOT_CLEARABLE。给 0 关闭。
     */
    public static final int A4_TUTORIAL_QUEST = num("a4.tutorialQuest", 1016);

    public static int a4ChannelKindForPort(int port) {
        return A4_CHANNEL_KINDS.getOrDefault(a4ChannelForPort(port), 2);
    }

    /** 端口对应的频道号；表里没有就退回 {@link #A4_CHANNEL}。 */
    public static int a4ChannelForPort(int port) {
        for (var e : A4_CHANNEL_PORTS.entrySet()) {
            if (e.getValue() == port) {
                return e.getKey();
            }
        }
        return A4_CHANNEL;
    }

    public static final int WORKER_THREADS = num("workerThreads",
            Math.max(1, Runtime.getRuntime().availableProcessors() * 2));

    // ------------------------------------------------------------------ entrance
    // 客户端在「选择频道」界面会无视命令行里的服务器地址，去连一个硬编码的
    // 121.14.107.176:7001（官方登录服）。没有东西应答那个端口，界面就永远停在
    // 「正在接收频道信息」、几秒后客户端自行退出。频道网关（entrance）就在本进程里
    // 顺带监听 7001（单进程双端口：10011 游戏本体 + 7001 网关）。见 com.dylan.net.entrance。
    //
    // 这几个常量放在顶层（s1a5.entrance.*），独立入口 com.dylan.net.entrance.EntranceMain
    // 读的是同一批属性。

    public static final boolean ENTRANCE = flag("entrance.enabled", true);

    public static final int ENTRANCE_PORT = num("entrance.port", 7001);

    /**
     * 网关并发连接上限。每个连接一个线程，没有上限时突发连接可能耗光进程 ——
     * 0 = 不限。
     */
    public static final int ENTRANCE_MAX_CONNECTIONS = num("entrance.maxConnections", 64);

    /**
     * 网关对外宣称的频道号（本版只开一个频道）。
     *
     * 只开 10 号是有意的：游戏服只绑一个端口（{@code s1a5.port}=10011），而频道表里
     * 每行写的端口必须真的有人在听 —— 把 38 行都指向 10011 会让客户端把所有角色放进
     * 同一个地方（"选完频道总显示 10"那个 bug 就是这么来的）。一个频道一个端口，不说假话。
     *
     * 10 也是 {@code MY_CHANNEL} 给直接选角启动用的值，两条入口路径落在同一个频道。
     */
    public static final int ENTRANCE_CHANNEL = num("entrance.channel", MY_CHANNEL);

    public static final boolean DB = flag("db", true);

    public static final int DB_POOL_SIZE = num("dbPoolSize", 1);

    public static final int DB_CONNECTION_TIMEOUT_MS = num("dbConnectionTimeoutMs", 3000);

    public static final int DB_LEAK_DETECTION_MS = num("dbLeakDetectionMs", 10000);

    public static final String DB_URL = str("dbUrl", "jdbc:sqlite:data/s1a5.db");

    public static final boolean DB_AUTO_CREATE_ACCOUNT = flag("dbAutoCreateAccount", false);

    /** 登录方式：password = 原来那样拿 CMD 1 的密码直接比对；token = CMD 1 的密码位填网关发的一次性令牌。 */
    public static final String LOGIN_MODE = str("loginMode", "password").toLowerCase();

    public static final boolean LOGIN_TOKEN_MODE = "token".equals(LOGIN_MODE);

    /**
     * 令牌模式下还允许直接用密码登录（老的 bat / 调试用）。
     * 商业登录器正式上线前必须关掉：-Ds1a5.loginPasswordFallback=0。
     */
    public static final boolean LOGIN_PASSWORD_FALLBACK = flag("loginPasswordFallback", false);

    public static final String DB_DEFAULT_PASSWORD = str("dbDefaultPassword", "123456");

    public static final Path CLIENT_DIR =
            Path.of(str("clientDir", "client"));

    public static final Path PVF_PATH =
            Path.of(str("pvf", CLIENT_DIR.resolve("Script.pvf").toString()));

    public static final boolean PVF = flag("pvf.enabled", true);

    public static final int GREET_DELAY_MS = num("greetDelayMs", 0);

    public static final boolean GREET = flag("greet", true);

    public static final int GREET_NOTI_ID = num("greetNotiId", 1);

    public static final String GREET_BODY = str("greetBody", "noti1");

    public static final String GREET_BODY_HEX = str("greetBodyHex", "");

    public static final String GREET_ACCOUNT = str("greetAccount", "10038");

    public static final int GREET_CIPHER_SEED = num("greetCipherSeed", 0);

    public static final boolean LOGIN_REPLY = flag("loginReply", true);

    public static final String LOGIN_REPLY_HEX = str("loginReplyHex", "");

    public static final int LOGIN_CHANNEL_KIND = num("loginChannelKind", -1);

    public static final int LOGIN_REJECT_CODE = num("loginRejectCode", 0);

    public static final String SELECT_REPLY = str("selectReply", "normal");

    public static final boolean OWN_INFO = flag("ownInfo", true);

    public static final boolean TUTORIAL_DONE = flag("tutorialDone", true);

    public static final boolean CLEAR_QUEST_ALL = flag("clearQuestAll", false);

    public static final int LANDING_TOWN_ID = num("town", 1);

    public static final int LANDING_AREA_ID = num("area", 1);

    public static final int NAME_BADGE_18 = num("nameBadge18", -1);

    public static final int PC_ROOM_NO = num("pcRoomNo", -1);

    public static final int PC_ROOM_PARTY_EXP_RATE = num("pcRoomPartyExpRate", -1);

    public static final int PREMIUM_FLAG = num("premiumFlag", -1);

    public static final int BLACK_POINT_19 = num("blackPoint19", -1);

    public static final int FATIGUE_MAX = num("fatigueMax", 156);

    public static final boolean STAMINA_PENALTY = flag("staminaPenalty", true);

    public static final boolean FATIGUE_ENFORCE = flag("fatigueEnforce", true);

    public static final int FATIGUE_RESET_HOUR = num("fatigueResetHour", 6);

    public static final boolean DAILY_GRANT = flag("dailyGrant", true);

    // 在线收信（2026-10-11）：周期性重扫 postal，把外部进程（网页 GM / 别的玩家）新插进来的邮件
    // 推给正在游戏里的角色 —— 不用再小退重新选角。见 com.dylan.game.MailPoller。
    public static final boolean MAIL_POLL = flag("mailPoll", true);

    public static final int MAIL_POLL_SECONDS = num("mailPollSeconds", 3);

    public static final int DAILY_TICKET_ITEM = num("dailyTicketItem", 4183);

    // A4 官方每天把 4183 补到 5 张（新角色 NOTI 13/0 槽 106 数量 5，满级假人 20 个角色都是 5）；ACT5 是 3
    public static final int DAILY_TICKET_CAP = num("dailyTicketCap", A4 ? 5 : 3);

    public static final int FATIGUE_PREMIUM_RATE = num("fatiguePremiumRate", 0);

    public static final int FATIGUE_PER_ROOM = num("fatiguePerRoom", 1);

    public static final int FATIGUE_PROBE_USED = num("fatigueProbeUsed", -1);

    public static final int SHUTDOWN_WAIT_MS = num("shutdownWaitMs", 30_000);

    public static final int CHECK_CONN_INTERVAL_MS = num("checkConnIntervalMs", 300_000);

    public static final int TOWN_SPAWN_X = num("townSpawnX", 474);

    public static final int TOWN_SPAWN_Y = num("townSpawnY", 234);

    public static final boolean ENTER_COMPLETE = flag("enterComplete", true);

    public static final int ENTER_COMPLETE_DELAY_MS = num("enterCompleteDelayMs", 100);

    public static final int VISIBLE_VALUES = num("visibleValues",
            com.dylan.protocol.body.Noti2OwnCharacterEntry.DEFAULT_VISIBLE);

    public static final int OWN_INFO_PAD = num("ownInfoPad", 0);

    public static final boolean EXIT_ON_DISCONNECT = flag("exitOnDisconnect", false);

    public static final String UPSTREAM_KEY_HEX = str("upstreamKey", "");

    public static final boolean ECHO_CREATED_CHARACTER = flag("echoCreatedCharacter", true);

    public static final int DUMP_LIMIT = num("dumpLimit", 512);

    public static final boolean DUNGEON_ENTRY = flag("dungeonEntry", true);

    public static final boolean TUTORIAL_FLAG_REPLY = flag("tutorialFlagReply", false);

    public static final int FALLBACK_MAP_INDEX = num("dungeonMap", 61000);

    public static final int START_MAP_MODE = num("startMapMode",
            com.dylan.protocol.body.Noti29Body.MODE_PLAIN);

    public static final boolean SPAWN_MONSTERS = flag("spawnMonsters", true);

    /**
     * 伤害测试用沙袋（DNF伤害分析）：{@code 怪物号:等级[:只数]}，空 = 关。打开后每个房间只刷这几只，
     * 站位沿用原刷怪表第一只；房间原本没怪就照旧没怪。只给本机测试用，正式服别开。
     */
    public static final String SANDBAG = str("sandbag", "");

    public static final int ENTITY_EXTRA_FLAG = num("entityExtraFlag", 0);

    /** [special passive object] 的 `[monster]`（深渊封印柱、木桶冒怪）进 NOTI 29。关掉：-Ds1a5.passiveMonsters=0 */
    public static final boolean PASSIVE_MONSTERS = flag("passiveMonsters", true);

    /** A4 深渊恶魔（.mob [hell monster] 1）每只掷几次深渊装备表；官方成品服 hell_drop_roll_times 默认 3。 */
    public static final int HELL_DEMON_DROP_ROLLS = num("hellDemonDropRolls", 3);

    public static final int SEQ_ID_BASE = num("seqIdBase", -1);

    public static final boolean UDP_HOST = flag("udpHost", true);

    public static final boolean EVENT_INFO = flag("eventInfo", true);

    public static final boolean EQUIP_REFRESH = flag("equipRefresh", true);

    public static final boolean ITEM_UPDATE_INCREMENTAL = flag("itemUpdateIncremental", true);

    public static final boolean GM_CHAT_REPLY = flag("gmChatReply", true);

    public static final boolean GOLD_PICKUP_ITEM_UPDATE = flag("goldPickupItemUpdate", false);

    public static final String PEER_UDP_HOST = str("peer.host", "127.0.0.1");

    public static final int PEER_UDP_PORT_A = num("peer.portA", PORT + 5);

    public static final int PEER_UDP_PORT_B = num("peer.portB", PORT + 6);

    public static final boolean PEER_UDP_ENABLED = !PEER_UDP_HOST.isBlank();

    /**
     * 登录回包里的 {@code udp_identity} 是否填账号 id（2026-10-28）。
     *
     * <p>官方成品服这里发 0（它不做组队），A4 客户端就认自己身份 0：外网实抓的中转登记帧是
     * {@code 00 00 0C 00 | 00000000 | 00000000}，MTUPD 头里的 local_identity 也是 0，
     * 收件人没法把包对到自己队伍名单里的那一位。填成账号 id 后与 NOTI 11 每行的 acc 一致。
     * 不想动这个字段时 {@code -Ds1a5.login.udpIdentity=0}（中转侧也会兜底改写 local_identity）。
     */
    public static final boolean LOGIN_UDP_IDENTITY = flag("login.udpIdentity", true);

    public static final int SPAWN_REPEAT = num("spawnRepeat", 1);

    public static final int HACK_PUNISH_POINT = num("hackPunishPoint", 2000);

    public static final boolean HACK_DROP_RATE_PENALTY = flag("hackDropRatePenalty", true);

    public static final int KILL_MONSTER_DAMAGE_LIMIT = num("killMonsterDamageLimit", 700000);

    public static final boolean ITEM_HISTORY = flag("itemHistory", true);

    public static final boolean DIE_MONSTER_REPLY = flag("dieMonsterReply", true);

    public static final boolean DROPS = flag("drops", true);

    public static final boolean QUEST_LIST = flag("questList", true);

    public static final boolean QUEST_DROPS = flag("questDrops", true);

    public static final boolean INSERT_GAME_WORLD = flag("insertGameWorld", true);

    public static final boolean ROSTER_BEFORE_START_GAME = flag("rosterBeforeStartGame", true);

    public static final boolean USER_AREA = flag("userArea", true);

    public static final int USER_AREA_X = num("userAreaX", 0);

    public static final int USER_AREA_Y = num("userAreaY", 0);

    public static final boolean PRISTINE_TOWN_ID = flag("pristineTownId", true);

    public static final boolean TUTORIAL_PERSIST = flag("tutorialPersist", true);

    public static final boolean PARTY_TAIL_PROBE = flag("partyTailProbe", false);

    public static final boolean STAT_PROBE = flag("statProbe", false);

    public static final boolean STAT_FROM_SCRIPT = flag("statFromScript", true);

    public static final boolean INITIAL_SKILLS = flag("initialSkills", true);

    public static final boolean ITEM_LIST = flag("itemList", true);

    public static final boolean CLEAR_REWARD = flag("clearReward", true);

    /** NOTI 34 通关评分。A4 官方 CMD 49 后必发（{@code 2D 00 00 00 00}，首字节 = CMD 49 报的评分档位）。 */
    public static final boolean PLAY_RESULT = flag("playResult", A4);

    public static final boolean REWARD_PROBE = flag("rewardProbe", false);

    public static final int RANK_ROOM_SECONDS = num("rank.roomSeconds", 45);

    public static final int[] RANK_THRESHOLDS =
            nums("rank.thresholds", 105, 95, 85, 75, 65, 55, 45, 35);

    public static final com.dylan.game.ClearRank UNLOCK_CHAMPION_RANK =
            com.dylan.game.ClearRank.parse(str("unlock.championRank", "B"))
                    .orElse(com.dylan.game.ClearRank.B);

    public static final com.dylan.game.ClearRank UNLOCK_KING_RANK =
            com.dylan.game.ClearRank.parse(str("unlock.kingRank", "S"))
                    .orElse(com.dylan.game.ClearRank.S);

    public static final int EXP_RATE_PERCENT = num("expRatePercent", 100);

    public static final String EXP_PENALTY_TABLE = str("expPenalty", "");

    public static final int MAX_CHARACTER_LEVEL = num("maxLevel", 60);

    public static final int CLEAR_CARD_GOLD_SHARE = num("clearCardGoldShare", 3500);

    private static int[] nums(String key, int... fallback) {
        String v = str(key, "");
        if (v.isEmpty()) {
            return fallback;
        }
        String[] parts = v.split(",");
        int[] out = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                out[i] = Integer.parseInt(parts[i].trim());
            }
        } catch (NumberFormatException e) {
            return fallback;
        }
        return out;
    }

    public static final boolean WIRE_TRACE = flag("wireTrace", false);

    public static final boolean LOGIN_AUTH = flag("loginAuth", true);

    public static final int LOGIN_REJECT_CODE_AUTH = num("loginRejectCodeAuth", 2);

    public static final boolean CASH_SHOP = flag("cashShop", true);

    /**
     * 人人可摆摊：NOTI 2/0 的 privateStore 恒为 1（A5 原本要阿拉德商人证 PREMIUM 8）。
     * 客户端 00497570 → 00497490：privateStore 为 1 才建摊位对象，否则开不了摊、也看不见别人的摊（摆摊.md §0）。
     */
    public static final boolean PRIVATE_STORE_ALL = flag("privateStoreAll", true);

    public static final boolean CASH_SHOP_DISCOUNT = flag("cashShopDiscount", false);

}
