package com.dylan.game;

import com.dylan.net.DownstreamPacket;
import com.dylan.net.OutboundCmdReply;
import com.dylan.net.OutboundNoti;
import com.dylan.net.ServerConfig;
import com.dylan.protocol.CmdName;
import com.dylan.protocol.body.CombatStats;
import com.dylan.protocol.body.Noti24Body;
import com.dylan.pvf.GameScripts;
import com.dylan.store.AccountRow;
import com.dylan.store.GameStore;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

import java.util.concurrent.TimeUnit;

public final class GameSession {
    private final ChannelHandlerContext ctx;
    private final GameStore store;
    private final GameScripts scripts;
    private final com.dylan.account.AccountService accounts;
    private final com.dylan.net.SessionExecutor executor;

    public GameSession(ChannelHandlerContext ctx, GameStore store, GameScripts scripts) {
        this(ctx, store, scripts, null, com.dylan.net.SessionExecutor.callerRuns());
    }

    public GameSession(ChannelHandlerContext ctx, GameStore store, GameScripts scripts,
                       com.dylan.account.AccountService accounts) {
        this(ctx, store, scripts, accounts, com.dylan.net.SessionExecutor.callerRuns());
    }

    public GameSession(ChannelHandlerContext ctx, GameStore store, GameScripts scripts,
                       com.dylan.account.AccountService accounts,
                       com.dylan.net.SessionExecutor executor) {
        this.ctx = ctx;
        this.store = store;
        this.scripts = scripts;
        this.accounts = accounts;
        this.executor = executor;
    }

    public com.dylan.net.SessionExecutor executor() {
        return executor;
    }

    public com.dylan.account.AccountService accounts() {
        return accounts;
    }

    public GameStore store() {
        return store;
    }

    public String clientIp() {
        if (ctx == null) {
            return "";
        }
        java.net.SocketAddress address = ctx.channel().remoteAddress();
        if (address instanceof java.net.InetSocketAddress inet && inet.getAddress() != null) {
            return inet.getAddress().getHostAddress();
        }
        return address == null ? "" : address.toString();
    }

    /** 客户端连进来的本地端口。A4 一个频道一个端口，频道号/频道类型由它查表（见 ServerConfig.A4_CHANNEL_PORTS）。 */
    public int localPort() {
        if (ctx != null && ctx.channel().localAddress() instanceof java.net.InetSocketAddress inet) {
            return inet.getPort();
        }
        return com.dylan.net.ServerConfig.PORT;
    }

    public GameScripts scripts() {
        return scripts;
    }

    private AccountRow account;
    private String loginUserId;
    private boolean loginAnswered;
    private String gatewayTokenDigest;

    public void gatewayTokenDigest(String digest) {
        gatewayTokenDigest = digest;
    }

    public String takeGatewayTokenDigest() {
        String digest = gatewayTokenDigest;
        gatewayTokenDigest = null;
        return digest;
    }
    private final AntiBotState antiBot = new AntiBotState();
    private final HackAnalyzer hack = new HackAnalyzer(this);
    private final MonsterKillWindow killWindow = new MonsterKillWindow();
    private int uniqueId = UniqueIdAllocator.UNASSIGNED;
    private int characNo = -1;
    private String characName;
    private CombatStats stats;

    /** 登录回包里的频道类型（A4 决斗频道 = 4）。一条连接一个，别用静态。 */
    private int channelKind = -1;

    public int channelKind() {
        return channelKind;
    }

    public void channelKind(int value) {
        this.channelKind = value;
    }

    /** 这条连接在决斗频道（频道类型 4/5/6/7/…，见 {@link GameChannel}）。 */
    public boolean pvpChannel() {
        return GameChannel.isPvpKind(channelKind);
    }

    public AccountRow account() {
        return account;
    }

    public void account(AccountRow value) {
        this.account = value;
    }

    public String loginUserId() {
        return loginUserId == null ? com.dylan.net.ServerConfig.GREET_ACCOUNT : loginUserId;
    }

    public void loginUserId(String value) {
        this.loginUserId = value;
    }

    public boolean loginTicketed() {
        return loginUserId != null;
    }

    public AntiBotState antiBot() {
        return antiBot;
    }

    public HackAnalyzer hack() {
        return hack;
    }

    public MonsterKillWindow killWindow() {
        return killWindow;
    }

    public boolean loginAnswered() {
        return loginAnswered;
    }

    public void loginAnswered(boolean value) {
        this.loginAnswered = value;
    }

    public int uniqueId() {
        return uniqueId;
    }

    public void uniqueId(int value) {
        this.uniqueId = value;
    }

    public boolean hasCharacter() {
        return characNo >= 0;
    }

    public int characNo() {
        return characNo;
    }

    public void characNo(int value) {
        this.characNo = value;
    }

    public String characName() {
        return characName;
    }

    public void characName(String value) {
        this.characName = value;
    }

    private int characCreateTime;

    public int characCreateTime() {
        return characCreateTime;
    }

    public void characCreateTime(int value) {
        this.characCreateTime = value;
    }

    private com.dylan.store.CharacterRow character;

    public com.dylan.store.CharacterRow character() {
        return character;
    }

    public void character(com.dylan.store.CharacterRow value) {
        this.character = value;
    }

    public CombatStats stats() {
        return stats;
    }

    public void stats(CombatStats value) {
        this.stats = value;
    }

    private long winPoint;

    public long winPoint() {
        return winPoint;
    }

    public void winPoint(long value) {
        this.winPoint = value;
    }

    private long cera;

    public long cera() {
        return cera;
    }

    public void cera(long value) {
        this.cera = value;
    }

    private int townId = ServerConfig.LANDING_TOWN_ID;
    private int areaId = ServerConfig.LANDING_AREA_ID;
    private boolean areaRosterSent;
    private int lastX = -1;
    private int lastY = -1;
    private int lastDirection = Noti24Body.DIRECTION_RIGHT;
    private int landingAreaId = ServerConfig.LANDING_AREA_ID;
    private int landingX = ServerConfig.TOWN_SPAWN_X;
    private int landingY = ServerConfig.TOWN_SPAWN_Y;

    public int townId() {
        return townId;
    }

    public void townId(int value) {
        this.townId = value;
    }

    public int areaId() {
        return areaId;
    }

    public void areaId(int value) {
        this.areaId = value;
    }

    public void landAt(int town, int area, int x, int y) {
        this.townId = town;
        this.areaId = area;
        landingPoint(area, x, y);
        this.areaRosterSent = false;
        this.townReturnX = -1;
        this.townReturnY = -1;
        this.townReturnAreaId = area;
    }

    public void landingPoint(int area, int x, int y) {
        this.landingAreaId = area;
        this.landingX = x;
        this.landingY = y;
    }

    public int landingAreaId() {
        return landingAreaId;
    }

    public int landingX() {
        return landingX;
    }

    public int landingY() {
        return landingY;
    }

    public boolean areaRosterSent() {
        return areaRosterSent;
    }

    public void areaRosterSent(boolean value) {
        this.areaRosterSent = value;
    }

    public int lastX() {
        return lastX;
    }

    public int lastY() {
        return lastY;
    }

    public int lastDirection() {
        return lastDirection;
    }

    public void position(int x, int y, int direction) {
        this.lastX = x;
        this.lastY = y;
        this.lastDirection = direction;
    }

    public boolean hasPosition() {
        return lastX >= 0 && lastY >= 0;
    }

    private boolean dungeonSelectOpened;

    private boolean gmMode;

    private boolean gmQuestFlag;

    private boolean invisible;
    private int townReturnX = -1;
    private int townReturnY = -1;
    private int townReturnAreaId = ServerConfig.LANDING_AREA_ID;

    public DungeonInstance dungeonInstance() {
        Party party = PartyRegistry.partyOf(this);
        return party == null ? null : party.dungeon();
    }

    public DungeonRoom room() {
        DungeonInstance d = dungeonInstance();
        return d == null ? DungeonRoom.empty() : d.room();
    }

    public BattleField battleField() {
        DungeonInstance d = dungeonInstance();
        return d == null ? null : d.field();
    }

    public DungeonRun dungeon() {
        DungeonInstance d = dungeonInstance();
        return d == null ? null : d.run();
    }

    public boolean inDungeon() {
        return dungeonInstance() != null;
    }

    public boolean dungeonSelectOpened() {
        return dungeonSelectOpened;
    }

    public void dungeonSelectOpened(boolean value) {
        this.dungeonSelectOpened = value;
    }

    public boolean gmMode() {
        return gmMode;
    }

    public void gmMode(boolean value) {
        this.gmMode = value;
    }

    public boolean gmQuestFlag() {
        return gmQuestFlag;
    }

    public void gmQuestFlag(boolean value) {
        this.gmQuestFlag = value;
    }

    public boolean invisible() {
        return invisible;
    }

    public void invisible(boolean value) {
        this.invisible = value;
    }

    public int townReturnX() {
        return townReturnX;
    }

    public int townReturnY() {
        return townReturnY;
    }

    public int townReturnAreaId() {
        return townReturnAreaId;
    }

    public boolean hasTownReturnPosition() {
        return townReturnX >= 0 && townReturnY >= 0;
    }

    public void rememberTownPosition() {
        if (!hasPosition()) {
            return;
        }
        this.townReturnX = lastX;
        this.townReturnY = lastY;
        this.townReturnAreaId = areaId;
    }

    private final java.util.List<com.dylan.protocol.body.Noti2CharacterEntry> memoryCharacters =
            new java.util.ArrayList<>();
    private long selectAnsweredAtNanos;

    public java.util.List<com.dylan.protocol.body.Noti2CharacterEntry> memoryCharacters() {
        return memoryCharacters;
    }

    public void markSelectAnswered() {
        this.selectAnsweredAtNanos = System.nanoTime();
    }

    private com.dylan.store.CharacterQuests quests;
    private long lastQuestFinishAt;
    private int questFinishesInWindow;

    private int lastClearedMapIndex;

    public com.dylan.store.CharacterQuests quests() {
        return quests;
    }

    public void quests(com.dylan.store.CharacterQuests value) {
        this.quests = value;
    }

    public int lastClearedMapIndex() {
        return lastClearedMapIndex;
    }

    public void lastClearedMapIndex(int value) {
        this.lastClearedMapIndex = value;
    }

    public long lastQuestFinishAt() {
        return lastQuestFinishAt;
    }

    public void resetQuestFinishWindow(long now) {
        this.lastQuestFinishAt = now;
        this.questFinishesInWindow = 0;
    }

    public int countQuestFinish() {
        return ++questFinishesInWindow;
    }

    private final TutorialFlags tutorials = new TutorialFlags();

    public TutorialFlags tutorials() {
        return tutorials;
    }

    private boolean tutorialSkipable;

    public boolean tutorialSkipable() {
        return tutorialSkipable;
    }

    public void tutorialSkipable(boolean value) {
        this.tutorialSkipable = value;
    }

    private int level = 1;
    private long exp;

    public int level() {
        return level;
    }

    public long exp() {
        return exp;
    }

    public void levelAndExp(int level, long exp) {
        this.level = level;
        this.exp = exp;
        if (character != null) {
            character = character.withLev(level);
        }
    }

    private int emotion;

    public int emotion() {
        return emotion;
    }

    public void emotion(int emotion) {
        this.emotion = emotion;
    }

    private final DungeonRunSummary runSummary = new DungeonRunSummary();

    public DungeonRunSummary runSummary() {
        return runSummary;
    }

    private int deathTowerIndex = DeathTowerRegistry.NOT_IN_TOWER;

    public int deathTowerIndex() {
        return deathTowerIndex;
    }

    void deathTowerIndex(int value) {
        this.deathTowerIndex = value;
    }

    public DeathTower deathTower() {
        return DeathTowerRegistry.byIndex(deathTowerIndex);
    }

    private final ConnectionCheckService connectionCheck = new ConnectionCheckService(this);

    public ConnectionCheckService connectionCheck() {
        return connectionCheck;
    }

    private volatile long lastActionAtNanos;
    private volatile int lastActionPacketId = -1;
    private volatile int actionCount;

    private volatile int heartbeatReplies;
    private volatile long lastHeartbeatAtNanos;

    private volatile boolean hasChecksum;
    private volatile int lastInventoryChecksum;
    private volatile int lastSkillChecksum;

    private volatile int checksumChanges;
    private volatile int checksumChangesWithoutAction;

    public void noteUpstream(int packetId) {
        if (packetId == com.dylan.protocol.body.Cmd0CheckConnectionRequest.PACKET_ID
                || packetId == ANTIBOT_PACKET_ID || packetId == DPROTO_PACKET_ID) {
            return;
        }
        lastActionPacketId = packetId;
        lastActionAtNanos = System.nanoTime();
        actionCount++;
    }

    private static final int ANTIBOT_PACKET_ID = 280;

    private static final int DPROTO_PACKET_ID = 282;

    public void noteHeartbeatReply(com.dylan.protocol.body.Cmd0CheckConnectionRequest checksums) {
        heartbeatReplies++;
        lastHeartbeatAtNanos = System.nanoTime();
        if (checksums == null) {
            return;
        }
        int actionsNow = actionCount;
        boolean changed = hasChecksum
                && (checksums.inventoryChecksum() != lastInventoryChecksum
                    || checksums.skillChecksum() != lastSkillChecksum);
        if (changed) {
            checksumChanges++;
            if (actionsNow == actionCountAtLastHeartbeat) {
                checksumChangesWithoutAction++;
            }
        }
        hasChecksum = true;
        lastInventoryChecksum = checksums.inventoryChecksum();
        lastSkillChecksum = checksums.skillChecksum();
        actionCountAtLastHeartbeat = actionsNow;
    }

    private volatile int actionCountAtLastHeartbeat;

    public long silenceMs() {
        long at = lastActionAtNanos;
        return at == 0 ? -1 : (System.nanoTime() - at) / 1_000_000;
    }

    public int lastActionPacketId() {
        return lastActionPacketId;
    }

    public int checksumChangesWithoutAction() {
        return checksumChangesWithoutAction;
    }

    private final FatigueService fatigue = new FatigueService(this);

    public FatigueService fatigue() {
        return fatigue;
    }

    private final StaminaService stamina = new StaminaService(this);

    public StaminaService stamina() {
        return stamina;
    }

    private final DungeonClearService dungeonClear = new DungeonClearService(this);

    public DungeonClearService dungeonClear() {
        return dungeonClear;
    }

    private final InventoryService inventory = new InventoryService(this);

    public InventoryService inventory() {
        return inventory;
    }

    private final ItemLockService itemLock = new ItemLockService(this);

    public ItemLockService itemLock() {
        return itemLock;
    }

    private final SkillService skills = new SkillService(this);

    public SkillService skills() {
        return skills;
    }

    private final ExpertJobService expertJob = new ExpertJobService(this);

    public ExpertJobService expertJob() {
        return expertJob;
    }

    private final EnchantService enchant = new EnchantService(this);

    public EnchantService enchant() {
        return enchant;
    }

    private final GameOptionService gameOption = new GameOptionService(this);

    public GameOptionService gameOption() {
        return gameOption;
    }

    private final MailService mail = new MailService(this);

    public MailService mail() {
        return mail;
    }

    private final CashShopService cashShop = new CashShopService(this);

    public CashShopService cashShop() {
        return cashShop;
    }

    private final PremiumService premium = new PremiumService(this);

    public PremiumService premium() {
        return premium;
    }

    private final UpgradeService upgrades = new UpgradeService(this);

    public UpgradeService upgrades() {
        return upgrades;
    }

    private final DisjointService disjoints = new DisjointService(this);

    public DisjointService disjoints() {
        return disjoints;
    }

    private final ResetItemAttrService resetItemAttr = new ResetItemAttrService(this);

    public ResetItemAttrService resetItemAttr() {
        return resetItemAttr;
    }

    private final IncreaseStatusService increaseStatus = new IncreaseStatusService(this);

    public IncreaseStatusService increaseStatus() {
        return increaseStatus;
    }

    private final ConsumableService consumables = new ConsumableService(this);

    public ConsumableService consumables() {
        return consumables;
    }

    private final LotteryService lottery = new LotteryService(this);

    public LotteryService lottery() {
        return lottery;
    }

    private final CompoundItemService compoundItem = new CompoundItemService(this);

    public CompoundItemService compoundItem() {
        return compoundItem;
    }

    private final MultiBoxService multiBox = new MultiBoxService(this);

    public MultiBoxService multiBox() {
        return multiBox;
    }

    private final BoosterService booster = new BoosterService(this);

    public BoosterService booster() {
        return booster;
    }

    private final CompoundAvatarService compoundAvatar = new CompoundAvatarService(this);

    public CompoundAvatarService compoundAvatar() {
        return compoundAvatar;
    }

    private final PartyService party = new PartyService(this);

    public PartyService party() {
        return party;
    }

    public void send(int notiId, byte[] body) {
        ctx.write(OutboundNoti.of(notiId, body));
    }

    public void sendNow(int notiId, byte[] body) {
        ctx.writeAndFlush(OutboundNoti.of(notiId, body));
    }

    public void reply(int packetId, byte[] body) {
        ctx.write(OutboundCmdReply.of(packetId, body));
    }

    public void replyNow(int packetId, byte[] body) {
        ctx.writeAndFlush(OutboundCmdReply.of(packetId, body));
    }

    /**
     * 回包后关连接，并让 FIN 和回包尾巴<b>在同一个 TCP 段</b>里到客户端。
     *
     * <p>CMD 3（换频道）靠这个：客户端收到 REPLY 3 就自己 closesocket（{@code 004107E3}，+0x1b4 置 -1），
     * 然后要等这条连接的 <b>FD_CLOSE</b>（{@code 009E52F1} → {@code 009E555F} → {@code 009BEC10}）才起 1 秒计时器、
     * 去连新选的频道（{@code 009BF8C0}）。FD_CLOSE 是 WSAAsyncSelect 窗口消息，FIN 必须在客户端 closesocket
     * 之前到，消息才排得进队列；晚到就没有这条消息，10 秒后 {@code 009BFA21} 报 0x850 超时 = 黑屏后退回频道 1。
     *
     * <p>原来 writeAndFlush + CLOSE 在测试服抓包是两个段（数据 [P.] 和 [F.] 相隔约 70 µs，
     * {@code probe_exit_fin.py}）。局域网两段几乎同时到，外网抖一下就分开：用户本机点「索西雅的决斗场列表」
     * 进决斗场每次都黑屏，网络近的人能进（2026-10-28）。官方成品服也是回完立刻 FIN（命令 3 日志模板）。
     *
     * <p>做法：关 Nagle，先单独发第 1 个字节（马上出去、未确认），剩下的字节被 Nagle 压住留在发送队列；
     * 随后 close() 的 FIN 由内核挂到这个还没发的段上（Linux tcp_send_fin），客户端一次收到「回包尾巴 + FIN」，
     * FD_READ 和 FD_CLOSE 同时排进消息队列。
     */
    public void replyThenClose(int packetId, byte[] body) {
        io.netty.buffer.ByteBuf frame = ctx.alloc().buffer();
        com.dylan.net.DownstreamPacketEncoder.encodeTo(ctx, OutboundCmdReply.of(packetId, body), frame);
        ctx.channel().config().setOption(io.netty.channel.ChannelOption.TCP_NODELAY, false);
        if (frame.readableBytes() > 1) {
            ctx.writeAndFlush(frame.readRetainedSlice(1));
        }
        ctx.writeAndFlush(frame).addListener(ChannelFutureListener.CLOSE);
    }

    public void write(DownstreamPacket packet) {
        ctx.write(packet);
    }

    public void flush() {
        ctx.flush();
    }

    public boolean isActive() {
        return ctx.channel().isActive();
    }

    public void schedule(Runnable task, long delayMs) {
        ctx.executor().schedule(() -> run(task), delayMs, TimeUnit.MILLISECONDS);
    }

    public void run(Runnable task) {
        if (executor.inSessionThread()) {
            task.run();
            return;
        }
        if (!ctx.channel().isActive()) {
            return;
        }
        executor.submit(task);
    }

    public void fail(String why) {
        System.err.println("[断开] " + why);
        ctx.close();
    }

    public void fail(String why, Throwable cause) {
        System.err.println("[断开] " + why);
        cause.printStackTrace();
        ctx.close();
    }

    public void disconnect(String why) {
        System.err.println("[断开] " + why);
        ctx.close();
    }

    public static String dump(byte[] body) {
        return ServerConfig.WIRE_TRACE
                ? com.dylan.net.Hex.dumpBlock(body, ServerConfig.DUMP_LIMIT) : "";
    }

    public static String describe(int packetId) {
        return CmdName.describe(packetId);
    }
}
