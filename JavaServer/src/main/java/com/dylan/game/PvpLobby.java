package com.dylan.game;

import com.dylan.protocol.body.TradePackets;
import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketReader;
import com.dylan.protocol.io.PacketWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A4 决斗频道的房间。建房、进房、邀请入座、全员准备后开打、阵亡和结算。
 * 坐标和技能仍是客户端之间的 UDP，这里不转发，也不发副本的 NOTI 27/28/29。
 *
 * <p>建房 CMD 53（{@code 00918593} 对话框，{@code 0091A1D3} 一键）：
 * u8 模式（0 后面跟房间名，名字是 {@code 00436B60} 的 u32 长度 + 字节；非 0 没有名字），
 * u16 客户端恒写 0，u8 有没有密码，有则再一段同样的字符串，最后 u8
 * （对话框写 0，一键写 1，含义未知）。
 * 成功回包 {@code 00416EC5}：结果非 0 就不再读。失败是结果 0 再加一个 u8。
 *
 * <p>房间列表 NOTI 56（{@code 0042AEB9}）：u8 条数，每条固定 42 字节。
 * {@code 009B3420} 在第 35 字节 ≥ 5 时直接丢掉这条，所以这里写 0。
 * 名字是从 +3 开始的 C 字符串，到 +32 为止（30 字节，含结尾 0）。
 * +33 地图 u16，+36 的低 4 位和高 4 位（再 &amp; 7）分开存，含义未知，写 0。
 * +37 房间号 u32。进房 CMD 54（{@code 00919003}）发的是全局 {@code 0xD608E8} 的低 16 位，
 * 那个全局由列表点击 {@code 00918E36} 写成房间号，所以房间号保持在 1..65535。
 * 第二次 NOTI 56 会再插一行，所以只在新建时发。
 *
 * <p>进房按钮不看 NOTI 56。它用 {@code 00432000} 在 {@code 0xD7F278} 里按房间号找，
 * 找到才亮。那张表由 NOTI 41（{@code 00428A87}）写入：{@code 00538280} 没有就新建。
 * 座位上的 u16 是连接号 {@code uniqueId}。{@code 00428C8B} 对上自己就 {@code 00537EC0}
 * 把 {@code 0xDA3640} 设成这间房。写成角色号对不上，人留在列表外面。
 * {@code 0xFFFF} 在 {@code 0053787E} 里是「人走了」，不能拿来当占座。
 *
 * <p>进房成功 {@code 00416FB8} 再读 8 个 u8，非 0 收成 1，写到 {@code 0xDA3638}。
 * 这 8 个字节是准备标记，不是有没有人。{@code 009654DD} 读 {@code [seat+0xDA3638]}
 * 取反后再发 CMD 56，{@code 005377D0} 把这个字节写回去。有人的座位若写成 1，
 * 人一进来就是准备好了。
 *
 * <p>窗口 {@code 0x35}（{@code 009651ED}，{@code CNRDPVPWarRoomInviteWindow}）是邀请人本地开的，
 * 那个函数本身不发包。决斗频道（频道类型 4）里坐在房间中的人发的是 CMD 10 kind 2
 * （{@code 00917920}：u16 目标、u8 2、u32 0）。接受是 CMD 11，kind 从邀请窗抄回来
 * （{@code 0090F292}：u16、u8、u32；拒绝 {@code 0090F3F7} 再多一个 u16）。
 * 对方弹窗是 NOTI 7 kind 2（{@code 0042096C} 读完 u16、u8、u32 后，kind 不是 0 也不是 1
 * 就 {@code 00420AFF} 开窗口 0x27，不再读）。kind 0 才多读两个 u16，那是城镇组队。
 * 接受后入座，不建城镇队伍，也不用 kind 2 开打。
 * 有密码的房间仍回失败：密码窗 {@code 0x5E} 之后发什么还没对上。
 *
 * <p>开打不看 CMD 15，也不看 CMD 122（{@code 009C8FA6}，回包槽是空的 {@code 0041D586}）。
 * 占着的座位都准备了、并且至少两个人，才给已经入座的人发 NOTI 45（{@code 0042964B}，两个 u8）。
 * {@code 0xDA3640} 为空时 NOTI 45 / 47 会写空指针，所以这两条只发给座位上有自己连接号的人。
 * NOTI 42 的第一个 u8 为 0 会删房间（{@code 0042905E}），为 2 会藏起进房按钮，这里只在房主离开时拿来删房间。
 *
 * <p>退出按钮 {@code 009652FE} 发 CMD 55，座位是自己的、状态 {@code 0xFE}。
 * 客户端要看见这个座位的连接号变成 {@code 0xFFFF}（{@code 0053787E}）才会 {@code 00537EC0(0)} 关掉房间窗。
 * 房主退出时先把每个有人的座位都写成 {@code 0xFFFF}，再发 NOTI 42 删列表。只发 NOTI 42 人还停在房间里。
 *
 * <p>模式按钮 {@code 00964FC0} 起发 CMD 57，一个 u8，五个按钮分别是 0、1、2、3、4。
 * 这个值写在房间 +0x88。{@code 00961650} 靠它换按钮。写成 5 会藏起进房按钮，所以只收 0 到 4。
 * 已经在房间里的人用 NOTI 43 头上那个字节改（条数 0，不动座位）。后面的 NOTI 41 / 43 必须带同一个值，
 * 否则 {@code 00429200} 发现不一样就发 CMD 56 把准备清掉，再把模式盖回去。
 *
 * <p>A4 的模式是台服 {@code PVP_BATTLE_MODE} 减 1：0 个人、1 组队、2 大将战、3 个人死斗、4 组队死斗
 * （{@code 00961650} 的 3/4 要看 +0x80 地图；字符串 {@code 0xC37EC4} "pvp deathmatch single play"）。
 * 流程照台服 {@code PvP_Room}（{@code E:\1_工具源码AI\2_台服源码\rednf\remake\source\DNFServer\GameServer\Game\PvP_Room\}）：
 *
 * <ul>
 * <li>座位上的第一个字节是队伍，不是「有没有人」（{@code 00537830} 写 +0xC，{@code 00537990} 的跳转表：
 *     0 个人战各自一边、1 红、2 蓝、3/4 观战）。空位 {@code 0xFF}、房主关掉的 {@code 0xFE}（{@code 00964EDC}）。
 *     旧版恒写 1，两人同队，开打后谁也打不到谁。</li>
 * <li>建房默认组队（官方 {@code reset} 模式 2），房主队伍 1（{@code create_room}）。进来的人按
 *     {@code join_room} 分队：个人 0，组队补人少的一边。切模式按 {@code set_pvp_mode} 改每个座位的队伍，
 *     再用 NOTI 43 把整间房发一遍（{@code make_seat_info(0xffff)}）。</li>
 * <li>开打 {@code start_pvp}：请求的地图是 0（随机）就按 {@code SelectRandomMap} 从 pvpmapbasicdata 里
 *     「一般决斗场显示」的图抽一张。NOTI 45 的地图字节是这张表的下标（{@code 00961870}），
 *     第 0 条是「50000 随机」，map.lst 没有，发 0 客户端就加载了一张不存在的图。之后 NOTI 42 状态 2。</li>
 * <li>阵亡 CMD 58 是死的那个人自己发，u16 是凶手（{@code 004B873F}）。分出胜负后先 NOTI 49
 *     （{@code pvp_request_pvp_rank}），客户端回 CMD 61（{@code 0042AAD7}）；都回了或 10 秒后发 NOTI 47。
 *     CMD 59 只有房主发（{@code 009C8E30}），8 个 u32 是各座位受到的伤害（{@code 004C8CFB}），少的赢。</li>
 * <li>关结算窗 CMD 60 后客户端停在 5，要 NOTI 42 状态 1（{@code 00428FAD} → {@code 0041E1F0} 置 +0x105）
 *     才回房间。都关了或第一个关窗后 10 秒（{@code end_pvp_result}），房主交给赢的人。</li>
 * </ul>
 */
public final class PvpLobby {

    static final int NOTI_LIST = 56;

    /** 进房按钮用的房间表。{@code 00428A87}。 */
    static final int NOTI_REGISTER = 41;

    /** 座位状态。{@code 00429176}。房间还没登记时它只读了 4 字节头就结束，不能发给没见过 NOTI 41 的人。 */
    static final int NOTI_SEAT = 43;

    /** 准备。{@code 004295E4}：u8 座位、u8 准备。 */
    static final int NOTI_READY = 44;

    /** 开打。{@code 0042964B}：u8 写到房间 +0x84，u8 交给 {@code 00537AA0}（+0x88，不能是 5）。 */
    static final int NOTI_START = 45;

    /** 阵亡。{@code 00429F7C}：u8 座位。房间指针为空就停，不会写穿。 */
    static final int NOTI_DIE = 46;

    /**
     * 结算。{@code 00429FF0}。头 11 字节，每人 27 字节，尾 9 字节。
     * 头上的段位和胜点按收包人自己填，因为它们写的是本地角色。
     */
    static final int NOTI_RESULT = 47;

    /** 要本局统计。{@code 0042A8E9}，空包。客户端回 CMD 61（官方 {@code pvp_request_pvp_rank} 的 0x31）。 */
    static final int NOTI_RANK = 49;

    /**
     * 房间状态。{@code 00428FAD}，7 字节：u16 房间、u8 状态、u8 房主座位、u16 地图、u8 模式（官方 {@code make_state_info}）。
     * 状态 0 时 {@code 005381D0} 删掉房间，不碰 {@code 0xDA3640}。1 大厅、2 决斗中（进行中那一页，进房按钮藏起）。
     */
    static final int NOTI_ROOM_STATE = 42;

    static final int MODE_SINGLE = 0;
    static final int MODE_TEAM = 1;
    static final int MODE_RELAY = 2;
    static final int MODE_DM_SINGLE = 3;
    static final int MODE_DM_TEAM = 4;

    static final int TEAM_SOLO = 0;
    static final int TEAM_RED = 1;
    static final int TEAM_BLUE = 2;
    /** 空位。官方 {@code reset} / {@code leave_room} 写 0xFF，{@code join_room} 只找这种座位。 */
    static final int TEAM_OPEN = 0xFF;
    /** 房主关掉的座位（{@code 00964EDC} 在 FE / FF 之间切）。 */
    static final int TEAM_CLOSED = 0xFE;

    /** 官方 {@code insert_timer_recv_pvp_rank} 10 秒；{@code GetEndPvPResultTimeout} 10 秒。 */
    private static final long RANK_TIMEOUT_MS = 10_000;
    private static final long END_TIMEOUT_MS = 10_000;

    /** REPLY 56 的失败码（{@code 0041717D}）：0x16 还有人没准备，0x0E 两队人数不一样。 */
    private static final int E_NOT_READY = 0x16;
    private static final int E_TEAM_UNEVEN = 0x0E;
    private static final int E_AUTH = 0x08;
    private static final int E_STATE = 0x13;

    private static final int NAME_ROOM = 29;

    /** 胜方加的胜点。数额客户端没有常量，取 10，让「获得%d点决斗胜点」不是 0。负和平局加 0。 */
    private static final int WIN_GAIN = 10;

    private static final int PHASE_LOBBY = 0;
    private static final int PHASE_FIGHT = 1;
    private static final int PHASE_RESULT = 2;

    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    /** 排名、关窗的超时。房间锁里改状态，回调先比 timerKey，房间重置过的旧定时器直接作废。 */
    private static final java.util.concurrent.ScheduledExecutorService TIMERS =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "pvp-timer");
                t.setDaemon(true);
                return t;
            });

    /** 监听端口 → 房间号 → 房间。一个端口一个频道。 */
    private static final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Room>> BY_PORT =
            new ConcurrentHashMap<>();

    /** 被邀请人 → 还没答应的房间邀请。和城镇组队的那张表分开。 */
    private static final ConcurrentHashMap<GameSession, Pending> INVITES = new ConcurrentHashMap<>();

    private PvpLobby() {
    }

    public static void make(GameSession s, byte[] body) {
        Created created;
        try {
            created = readCreate(s, body);
        } catch (PacketFormatException e) {
            s.reply(53, fail());
            s.flush();
            return;
        }
        if (created == null) {
            s.reply(53, fail());
            s.flush();
            return;
        }
        Room room = created.room();
        rooms(room.port).put(room.id, room);
        publish(room, true);
        // 进房的 NOTI 41 在 0xDA3640 写上之前就套了模式，00961650 整段跳过，模式按钮一直是灰的。
        armModeButtons(s, room);
        s.reply(53, new byte[] {1});
        s.flush();
    }

    public static void enter(GameSession s, byte[] body) {
        if (body == null || body.length < 3) {
            s.reply(54, fail());
            s.flush();
            return;
        }
        PacketReader r = new PacketReader(body);
        int id = r.u16();
        r.u8();
        Room room = rooms(s.localPort()).get(id);
        boolean fresh = room != null && room.seatIndex(s.characNo()) < 0;
        if (room == null || !room.password.isEmpty() || room.phase != PHASE_LOBBY || !room.seat(s.characNo())) {
            s.reply(54, fail());
            s.flush();
            return;
        }
        // 先登记再回 54。NOTI 41 里自己的连接号会把人拉进房间（00537EC0）。
        shareRoster(room);
        publish(room, false);
        tellReady(room);
        if (fresh) {
            armModeButtons(s, room);
        }
        s.reply(54, entered(room));
        s.flush();
    }

    /**
     * CMD 55（{@code 00537C9B}）：u8 座位、u8 新状态。官方 {@code OnSetPVPSeatState} → {@code set_seat_state}：
     * 只有这个座位上的人或房主（{@code check_authority}），决斗中不收（0x13）。
     * <ul>
     * <li>{@code 0xFE}：自己的座位是退出（{@code 009652FE}）；房主点别人是踢人（→ {@code out_from_pvp}）；
     *     房主点空位是关座位（{@code is_closeable_seat_state}）。</li>
     * <li>{@code 0xFF}：房主把关掉的空位打开（{@code 00964EE3}）。</li>
     * <li>其余是自己选边（{@code 00965413}：组队按钮 1/2/3，个人按钮 0/4），写进队伍，{@code change_team}。</li>
     * </ul>
     * 每次都用 NOTI 43（{@code make_seat_info}）把这一座发给同端口的人。
     * 决斗中自己退出仍按阵亡处理，不然人会被留在决斗里（官方这里回 0x13，人只能断线）。
     */
    public static void seatState(GameSession s, byte[] body) {
        Room room = seated(s);
        if (room == null || body == null || body.length < 2) {
            replyFail(s, 55);
            return;
        }
        int seat = body[0] & 0xFF;
        int state = body[1] & 0xFF;
        synchronized (room) {
            if (seat >= Room.SEATS) {
                replyFail(s, 55);
                return;
            }
            boolean self = room.seats[seat] == s.characNo();
            boolean host = room.hostCharacNo == s.characNo();
            if (!self && !host) {
                replyFail(s, 55, E_AUTH);
                return;
            }
            if (self && state == TEAM_CLOSED) {
                leaveLocked(s, room, seat);
                return;
            }
            if (room.phase != PHASE_LOBBY) {
                replyFail(s, 55, E_STATE);
                return;
            }
            if (state == TEAM_OPEN) {
                if (!host || room.seats[seat] > 0) {
                    replyFail(s, 55, E_AUTH);
                    return;
                }
                room.team[seat] = TEAM_OPEN;
            } else if (state == TEAM_CLOSED) {
                if (room.seats[seat] > 0) {
                    kickLocked(room, seat);
                    replyOk(s, 55);
                    return;
                }
                if (!room.closeable()) {
                    replyFail(s, 55, E_STATE);
                    return;
                }
                room.team[seat] = TEAM_CLOSED;
            } else {
                if (!self || state > MODE_DM_TEAM) {
                    replyFail(s, 55, E_AUTH);
                    return;
                }
                room.team[seat] = state;
            }
            tellSeat(room, seat);
        }
        replyOk(s, 55);
    }

    /** 房主踢人。官方 {@code set_seat_state} 房主分支清座位，再 {@code out_from_pvp} 把他送回角斗场。 */
    private static void kickLocked(Room room, int seat) {
        int victim = room.seats[seat];
        GameSession target = findCharac(victim);
        dropSeat(room, seat, victim);
        if (target != null) {
            INVITES.remove(target);
            NeighborService.replayAreaToSelf(target);
        }
    }

    /**
     * 自己退出。调用时已经拿着房间锁。
     * 官方 {@code PvP_Room::leave_room}：只清自己的座位；房主走了就 {@code select_new_manager}，
     * 房间里还有人就不拆房。空了才删。
     * 然后 {@code GameWorld::out_from_pvp} 把人送回角斗场。
     * 决斗中先按阵亡（人还坐着，NOTI 46 才发得到）；清座位之后再看胜负（官方 {@code leave_room} 里的 {@code check_winner}）。
     */
    private static void leaveLocked(GameSession s, Room room, int seat) {
        boolean fighting = room.phase == PHASE_FIGHT && room.win[seat];
        if (fighting) {
            room.win[seat] = false;
            tellDie(room, seat);
        }
        dropSeat(room, seat, s.characNo());
        replyOk(s, 55);
        NeighborService.replayAreaToSelf(s);
    }

    /**
     * CMD 56（{@code 009654DD}）：1 字节，是取反之后的新准备值。官方 {@code set_ready_state}：
     * 队员只改自己的准备，发 NOTI 44。房主点准备才是开场：{@code check_start_state} 要每个还坐着、
     * 不是观战的人都准备了，{@code check_team_pvp} 要组队两边人数一样（不一样回 0x0E，房主那一位不清）；
     * 有人没准备就把房主的准备清回 0、回 0x16。都过了才 NOTI 44 + {@code start_pvp}。
     * 官方 {@code start_pvp} 不到两个人就什么都不做；这里同样要两个能打的人，不够回 0x16。
     */
    public static void ready(GameSession s, byte[] body) {
        Room room = seated(s);
        if (room == null) {
            replyFail(s, 56);
            return;
        }
        synchronized (room) {
            int seat = room.seatIndex(s.characNo());
            if (seat < 0 || room.phase != PHASE_LOBBY) {
                replyFail(s, 56, E_STATE);
                return;
            }
            boolean on = body == null || body.length < 1 ? room.ready[seat] == 0 : (body[0] & 0xFF) != 0;
            if (seat != room.hostSeat) {
                room.ready[seat] = on ? 1 : 0;
                tellReadyOne(room, seat);
                replyOk(s, 56);
                return;
            }
            if (!on) {
                replyOk(s, 56);
                return;
            }
            room.ready[seat] = 1;
            if (!room.allFightersReady() || room.fighters() < 2) {
                room.ready[seat] = 0;
                replyFail(s, 56, E_NOT_READY);
                return;
            }
            if (!room.teamsEven()) {
                replyFail(s, 56, E_TEAM_UNEVEN);
                return;
            }
            tellReadyOne(room, seat);
            startPvp(s, room);
        }
        replyOk(s, 56);
    }

    /**
     * CMD 57（{@code 00964FC0}）：房主的一个 u8，五个模式按钮是 0 到 4。5 会藏起进房按钮，丢掉。
     * 官方 {@code set_pvp_mode}：个人 / 个人死斗把每个人的队伍写成 0；组队、组队死斗按座位顺序 1、2 交替
     * （从大将战切到组队时保留原来的队伍）；大将战从组队切来保留，从个人切来交替。
     * 然后 {@code make_seat_info(0xffff)}：NOTI 43 头上带新模式、整间房每个人一条。准备全清。
     */
    public static void team(GameSession s, byte[] body) {
        Room room = hosted(s);
        if (room != null && body != null && body.length >= 1) {
            synchronized (room) {
                int mode = body[0] & 0xFF;
                if (room.phase == PHASE_LOBBY && mode <= MODE_DM_TEAM) {
                    setMode(room, mode);
                    for (int i = 0; i < Room.SEATS; i++) {
                        room.ready[i] = 0;
                    }
                    tellSeatsAll(room);
                    tellReady(room);
                }
            }
        }
        replyOk(s, 57);
    }

    private static void setMode(Room room, int mode) {
        int old = room.teamMode;
        room.teamMode = mode;
        boolean alternate;
        if (mode == MODE_SINGLE || mode == MODE_DM_SINGLE) {
            for (int i = 0; i < Room.SEATS; i++) {
                if (room.seats[i] > 0) {
                    room.team[i] = TEAM_SOLO;
                }
            }
            return;
        } else if (mode == MODE_RELAY) {
            alternate = old != MODE_TEAM;
        } else if (mode == MODE_TEAM) {
            alternate = old != MODE_RELAY;
        } else {
            alternate = true;
        }
        // 官方从个人死斗切到大将战时不改队伍，人都留在 0。这里补成交替，不然开打谁也不分边。
        if (!alternate && !room.hasTeamSides()) {
            alternate = true;
        }
        if (alternate) {
            int next = TEAM_RED;
            for (int i = 0; i < Room.SEATS; i++) {
                if (room.seats[i] > 0) {
                    room.team[i] = next;
                    next = next == TEAM_RED ? TEAM_BLUE : TEAM_RED;
                }
            }
        }
    }

    /**
     * CMD 62（{@code 00962C05}）：房主的 u16 地图（pvpmapbasicdata 下标，0 随机）。
     * 官方 {@code DisPatcher_SetPVPMapIndex}：{@code CheckPvPMapMode} 按死斗图改模式，
     * {@code SetRequestedMapNo}，再把 {@code make_state_info}（NOTI 42 状态 1）发给同端口所有人。
     * 已在房间里的人发现 +0x80 / +0x88 变了会自己发 CMD 56 0（{@code 0042908C}），和官方一样。
     */
    public static void map(GameSession s, byte[] body) {
        Room room = hosted(s);
        if (room != null && body != null && body.length >= 2) {
            synchronized (room) {
                if (room.phase == PHASE_LOBBY) {
                    int index = new PacketReader(body).u16();
                    int before = room.teamMode;
                    checkMapMode(room, maps(s).isDeathMatch(index));
                    room.map = index;
                    tellState(room);
                    if (room.teamMode != before) {
                        tellSeatsAll(room);
                    }
                }
            }
        }
        replyOk(s, 62);
    }

    /** 官方 {@code CheckPvPMapMode} / {@code ChangePvPMode}：死斗图把个人、组队换成死斗，普通图换回来。 */
    private static void checkMapMode(Room room, boolean deathMatch) {
        int mode = room.teamMode;
        if (deathMatch) {
            if (mode == MODE_SINGLE || mode == MODE_RELAY) {
                setMode(room, MODE_DM_SINGLE);
            } else if (mode == MODE_TEAM) {
                room.teamMode = MODE_DM_TEAM;
            }
        } else if (mode == MODE_DM_SINGLE) {
            setMode(room, MODE_SINGLE);
        } else if (mode == MODE_DM_TEAM) {
            room.teamMode = MODE_TEAM;
        }
    }

    /**
     * CMD 58（{@code 004B873F}）：死的那个本地角色自己发，2 字节是凶手的连接号（vtable+0x2F4 → +0x30C），没有凶手是 0xFFFF。
     * 官方 {@code die_charac}：发包人的座位 P_WIN 清 0，NOTI 46 给座位上的人，{@code check_winner} 分出胜负就
     * {@code pvp_request_pvp_rank}（NOTI 49）。成功只回结果字节（{@code 00417271}）。
     */
    public static void death(GameSession s, byte[] body) {
        Room room = seated(s);
        if (room == null) {
            replyFail(s, 58, E_STATE);
            return;
        }
        synchronized (room) {
            int seat = room.seatIndex(s.characNo());
            if (room.phase != PHASE_FIGHT || room.decided || seat < 0) {
                replyFail(s, 58, E_STATE);
                return;
            }
            if (!room.win[seat]) {
                replyFail(s, 58, 0x12);
                return;
            }
            room.win[seat] = false;
            int killer = body != null && body.length >= 2 ? room.seatOfWire(new PacketReader(body).u16()) : -1;
            if (killer >= 0) {
                room.kills[killer]++;
            }
            tellDie(room, seat);
            if (checkWinner(room)) {
                requestRank(room);
            }
        }
        replyOk(s, 58);
    }

    /**
     * CMD 59：房主（{@code 009C8E30} 比 +0x7C 和自己）在决斗时间到时发，8 个 u32 是各座位这局受到的伤害
     * （{@code 004C8CFB} → {@code 009C8850} 往 +0xD4 加 HP 差）。官方 {@code time_out}：8 个数都一样是平局；
     * 组队按队伍加起来，个人按座位，最小的赢。观战的队伍不算（官方会算进去，0 伤害的观战队会白赢）。
     */
    public static void timeout(GameSession s, byte[] body) {
        Room room = seated(s);
        if (room != null) {
            synchronized (room) {
                if (room.phase == PHASE_FIGHT && !room.decided) {
                    int[] hurt = new int[Room.SEATS];
                    PacketReader r = new PacketReader(body == null ? new byte[0] : body);
                    for (int i = 0; i < Room.SEATS && r.remaining() >= 4; i++) {
                        hurt[i] = r.u32();
                    }
                    judgeTimeout(room, hurt);
                    requestRank(room);
                }
            }
        }
        replyOk(s, 59);
    }

    /** CMD 61（{@code 0042AAD7}，回 NOTI 49 的统计）。官方 {@code SavePvpRank} → {@code set_recv_pvp_rank_flag}，都到了就结算。 */
    public static void rankReceived(GameSession s) {
        Room room = seated(s);
        if (room != null) {
            synchronized (room) {
                int seat = room.seatIndex(s.characNo());
                if (seat >= 0 && room.rankAsked && room.phase == PHASE_FIGHT) {
                    room.rankRecv[seat] = true;
                    if (room.allRecv(room.rankRecv)) {
                        sendPvpEnd(room);
                    }
                }
            }
        }
        replyOk(s, 61);
    }

    /**
     * CMD 60（{@code 009CC267}，空包）：结算窗关了。官方 {@code DisPatcher_EndPvPResult}：
     * 都关了就 {@code end_pvp_result}；第一个关的时候插一个 10 秒定时器（{@code insert_timer_recv_pvp_end_result}）。
     * 回 {@code 01}（{@code 0041733D} 只看结果字节）。
     */
    public static void resultClosed(GameSession s) {
        Room room = seated(s);
        if (room != null) {
            synchronized (room) {
                int seat = room.seatIndex(s.characNo());
                if (seat >= 0 && room.phase == PHASE_RESULT) {
                    room.endRecv[seat] = true;
                    if (room.allRecv(room.endRecv)) {
                        endPvpResult(room);
                    } else if (room.countRecv(room.endRecv) == 1) {
                        schedule(room, END_TIMEOUT_MS, PHASE_RESULT, () -> endPvpResult(room));
                    }
                }
            }
        }
        replyOk(s, 60);
    }

    /**
     * 坐在决斗房间里的 CMD 10/11。决斗频道里的按钮是 kind 2（{@code 00917920}），
     * 城镇那条 kind 0（{@code 0053B663}）坐在房间里时也算房间邀请。
     * 返回 true 时调用方不要再走城镇组队。人还没入座时返回 false。
     * kind 2 只负责邀请，不开打。
     */
    public static boolean onPeer(GameSession s, int packetId, TradePackets.PeerRequest req) {
        if (packetId == 10) {
            Room room = seated(s);
            if (room == null) {
                return false;
            }
            invite(s, room, req);
            return true;
        }
        Pending pending = INVITES.get(s);
        if (pending == null || pending.inviterUid != req.uid()) {
            return false;
        }
        answer(s, pending, req);
        return true;
    }

    public static void onEnterWorld(GameSession s) {
        List<Room> list = new ArrayList<>(rooms(s.localPort()).values());
        if (list.isEmpty()) {
            // 决斗频道：官方副本（单机，没有决斗场）在这里什么房间包都不发；客户端切到 kind 4 频道、
            // 进了决斗场大厅之后要等房间列表，等不到就一直黑屏（2026-10-28 外网实机）。
            // 至少把「0 间房」的表发下去，列表窗有东西可用；普通频道保持原样不发。
            sendEmptyLobbyIfEmpty(s);
            return;
        }
        s.send(NOTI_REGISTER, registerBody(list));
        s.send(NOTI_LIST, listBody(list));
    }

    /**
     * 决斗频道进了大厅（NOTI 23/24 之后）再补一次空房间表。
     *
     * <p>客户端是在「进区域」之后才建决斗场大厅那套窗的，世界入口那一条可能太早被丢掉。
     * 只在**没有房间**时补：有房间时再发一遍列表会让客户端再插一行（NOTI 56 是追加语义）。
     */
    public static void sendEmptyLobbyIfEmpty(GameSession s) {
        if (!s.pvpChannel()) {
            return;
        }
        if (!rooms(s.localPort()).isEmpty()) {
            return;
        }
        s.send(NOTI_REGISTER, registerBody(List.of()));
        s.send(NOTI_LIST, listBody(List.of()));
    }

    public static void onLeaveWorld(GameSession s) {
        INVITES.remove(s);
        int uid = s.uniqueId();
        INVITES.entrySet().removeIf(e -> e.getValue().inviterUid == uid);
        int no = s.characNo();
        if (no <= 0) {
            return;
        }
        for (Room room : new ArrayList<>(rooms(s.localPort()).values())) {
            int seat = room.seatIndex(no);
            if (seat < 0) {
                continue;
            }
            synchronized (room) {
                if (room.phase == PHASE_FIGHT && room.win[seat]) {
                    room.win[seat] = false;
                    tellDie(room, seat);
                }
                // 连接已经没了，不用再给自己发回角斗场的包。
                dropSeat(room, seat, no);
            }
        }
    }

    /**
     * 清一个座位。房主离开且还有别人时，房主交给下一个还坐着的人（座位号写进 NOTI 41 的 +0x7C）。
     * 一个人都不剩才发 NOTI 42 删房间，并且不再发 NOTI 41。
     * 还有人时再看一遍决斗：官方 {@code leave_room} 在决斗中 {@code check_winner}；
     * 等着的 CMD 61 / CMD 60 少了这个人可能已经齐了。
     */
    private static void dropSeat(Room room, int seat, int characNo) {
        boolean host = room.hostCharacNo == characNo;
        boolean keep = host && transferHost(room, seat);
        room.clearSeat(seat);
        // 这个人的所有「自己→别人」/「别人→自己」PvP 中转腿都关掉，别让旧端口继续收包。
        UdpPairRelay.closeForCharac(characNo);
        tellVacateOne(room, seat);
        if (host && !keep) {
            room.timerKey++;
            close(room);
            rooms(room.port).remove(room.id, room);
            return;
        }
        publish(room, false);
        if (host && room.phase == PHASE_LOBBY) {
            GameSession next = findCharac(room.hostCharacNo);
            if (next != null) {
                armModeButtons(next, room);
            }
        }
        if (room.phase == PHASE_FIGHT && !room.decided && checkWinner(room)) {
            requestRank(room);
        } else if (room.phase == PHASE_FIGHT && room.rankAsked && room.allRecv(room.rankRecv)) {
            sendPvpEnd(room);
        } else if (room.phase == PHASE_RESULT && room.allRecv(room.endRecv)) {
            endPvpResult(room);
        }
    }

    /** 官方 {@code select_new_manager}：下一个还坐着的人当房主，他的准备清掉。 */
    private static boolean transferHost(Room room, int leftSeat) {
        for (int i = 0; i < Room.SEATS; i++) {
            if (i != leftSeat && room.seats[i] > 0) {
                room.hostCharacNo = room.seats[i];
                room.hostSeat = i;
                room.ready[i] = 0;
                return true;
            }
        }
        return false;
    }

    private static void invite(GameSession s, Room room, TradePackets.PeerRequest req) {
        int kind = req.kind();
        if (!room.password.isEmpty() || room.phase != PHASE_LOBBY) {
            replyGone(s, 10, kind);
            return;
        }
        GameSession target = SessionRegistry.findInWorld(req.uid());
        if (target == null || target == s || !target.hasCharacter() || target.localPort() != room.port) {
            replyGone(s, 10, kind);
            return;
        }
        if (seated(target) != null) {
            s.reply(10, TradePackets.fail(0x12, kind));
            s.flush();
            return;
        }
        if (room.full()) {
            s.reply(10, TradePackets.fail(4, kind));
            s.flush();
            return;
        }
        INVITES.put(target, new Pending(room.port, room.id, s.uniqueId()));
        s.reply(10, TradePackets.ok());
        s.flush();
        // NOTI 7（0042096C）。kind 2：u16 邀请人, u8 2, u32 peer，然后 00420AFF 开窗口 0x27。
        // kind 0 还要两个 u16（00420A0D）。不发 kind 1，那个开的是交易窗。
        PacketWriter noti = new PacketWriter().u16(s.uniqueId()).u8(kind).u32(req.peer());
        if (kind == TradePackets.KIND_PARTY) {
            noti.u16(0).u16(0);
        }
        target.sendNow(TradePackets.NOTI_REQUEST_PEER, noti.toArray());
    }

    /** 接受就入座、补 NOTI 2/1 和 NOTI 11，再重发 NOTI 41。拒绝（多一个 u16）和组队一样不回。不发 NOTI 8/9/165。 */
    private static void answer(GameSession s, Pending pending, TradePackets.PeerRequest req) {
        INVITES.remove(s, pending);
        if (req.refuse() != 0) {
            return;
        }
        Room room = rooms(pending.port).get(pending.roomId);
        boolean fresh = room != null && room.seatIndex(s.characNo()) < 0;
        if (room == null || !room.password.isEmpty() || room.phase != PHASE_LOBBY || !room.seat(s.characNo())) {
            return;
        }
        shareRoster(room);
        publish(room, false);
        tellReady(room);
        if (fresh) {
            armModeButtons(s, room);
        }
    }

    /**
     * 官方 {@code PvP_Room::join_room}：入座时互发 NOTI 2 子类型 1（{@code make_basic_info}）和 NOTI 11（P2P 地址）。
     * A4 只有 NOTI 2 子类型 1/3（{@code 0041EF56} → {@code 006FA8B0}、{@code 00539690}）会给别人建角色对象，
     * 写到邀请名单那条用户记录的 +0x174。开打后 {@code 009C9B30} 按座位逐个取 +0x174，
     * 没收过这条的座位是空指针（{@code 009C9B7C} / {@code 009C9C0A}）。
     * 座位清成 {@code 0xFFFF} 时 {@code 005378B9} 会把角色对象拆掉，所以每次入座都重发。
     * 自己那条进世界时已经发过，不再发给自己。NOTI 11（{@code 00421CFF}）只写用户记录的地址字段，不碰队伍栏。
     */
    private static void shareRoster(Room room) {
        List<GameSession> seated = members(room);
        if (seated.size() < 2) {
            return;
        }
        for (GameSession to : seated) {
            for (GameSession other : seated) {
                if (other == to) {
                    continue;
                }
                SessionRegistry.Presence presence = SessionRegistry.presenceOf(other);
                if (presence == null || presence.detail() == null) {
                    continue;
                }
                to.sendNow(com.dylan.protocol.body.Noti2Body.PACKET_ID,
                        com.dylan.protocol.body.Noti2Body.partyRoster(List.of(presence.detail())).toBytes());
            }
        }
        PartyInviteService.sendPvpIpInfo(seated);
    }

    /** CMD 2 报上 UDP 端点后，房间里的人要拿到新地址。没坐在房间里返回空表。 */
    static List<GameSession> roomMates(GameSession s) {
        Room room = seated(s);
        return room == null ? List.of() : members(room);
    }

    private static com.dylan.pvf.script.PvpMapTable maps(GameSession s) {
        var scripts = s == null ? null : s.scripts();
        return scripts == null ? com.dylan.pvf.script.PvpMapTable.empty() : scripts.pvpMaps();
    }

    /**
     * 官方 {@code start_pvp}：选地图，每个座位 P_WIN = 1，给座位上的人发 NOTI 45（地图下标、模式），
     * 状态改成 2。{@code process_52} 再把 {@code make_state_info}（NOTI 42 状态 2）发给所有人，
     * 列表里这间房移到「进行中」，进房按钮藏起。
     */
    private static void startPvp(GameSession host, Room room) {
        room.playMap = pickMap(host, room);
        for (int i = 0; i < Room.SEATS; i++) {
            room.win[i] = room.seats[i] > 0;
            room.kills[i] = 0;
            room.rankRecv[i] = false;
            room.endRecv[i] = false;
        }
        room.phase = PHASE_FIGHT;
        room.decided = false;
        room.rankAsked = false;
        room.winTeam = -1;
        room.timerKey++;
        tellStart(room);
        tellState(room);
    }

    /**
     * 官方 {@code start_pvp} / {@code SelectRandomMap}：请求的下标不在 1..表长-1 里就随机。
     * {@code GetCandidateMapIndexList} 从 1 开始，只要「一般决斗场显示」的图，大将战不要死斗图；
     * {@code DrawRandomMapIndex} 先挑普通图；{@code ChangePvPMode} 抽到死斗图把个人 / 组队换成死斗。
     * 第 0 条是「50000 随机」，不能原样发给客户端。表没读到时只能按请求的发，并打一行日志。
     */
    private static int pickMap(GameSession host, Room room) {
        var table = maps(host);
        int count = table.size();
        if (room.map >= 1 && room.map < count) {
            return room.map;
        }
        List<Integer> normal = new ArrayList<>();
        List<Integer> death = new ArrayList<>();
        for (int i = 1; i < count; i++) {
            var e = table.get(i);
            if (!e.normal() || (e.deathMatch() && room.teamMode == MODE_RELAY)) {
                continue;
            }
            (e.deathMatch() ? death : normal).add(i);
        }
        List<Integer> pool = normal.isEmpty() ? death : normal;
        if (pool.isEmpty()) {
            System.err.println("[决斗] pvpmapbasicdata 没有能随机的图（共 " + count + " 条），按请求的 " + room.map + " 开打");
            return room.map;
        }
        int pick = pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));
        checkMapMode(room, table.isDeathMatch(pick));
        return pick;
    }

    /**
     * 官方 {@code check_winner}，阵亡之后调。组队 / 大将战：只剩一队还有 P_WIN 的人，那一队全体 P_WIN = 1。
     * 个人：不是观战、P_WIN 还在的只剩一个人。死斗（{@code CDeathMatchBattleMgr}，按击杀数、会复活）A4 没有
     * 复活的包（NOTI 120 在 A4 是别的），按个人 / 组队的规则判。全倒了算平局。
     */
    private static boolean checkWinner(Room room) {
        if (room.decided) {
            return false;
        }
        java.util.Set<Integer> living = new java.util.HashSet<>();
        int fighters = 0;
        for (int i = 0; i < Room.SEATS; i++) {
            if (room.seats[i] <= 0 || Room.observer(room.team[i])) {
                continue;
            }
            fighters++;
            if (room.win[i]) {
                living.add(room.sideOf(i));
            }
        }
        if (fighters == 0 || living.size() > 1) {
            return false;
        }
        int side = living.isEmpty() ? -1 : living.iterator().next();
        awardSide(room, side);
        return true;
    }

    /** 这一边（个人是座位号，组队是队伍）的人 P_WIN = 1，其余 0。-1 是平局，谁都不赢。 */
    private static void awardSide(Room room, int side) {
        room.winTeam = side;
        for (int i = 0; i < Room.SEATS; i++) {
            room.win[i] = side >= 0 && room.seats[i] > 0 && !Room.observer(room.team[i]) && room.sideOf(i) == side;
        }
        room.decided = true;
    }

    /**
     * 官方 {@code time_out}：8 个数都一样，谁都不赢。组队把同一队的数加起来，个人看每个座位，最小的那一边赢，
     * 一样小取前面那个。个人模式只看不是观战的座位。
     */
    private static void judgeTimeout(Room room, int[] hurt) {
        boolean same = true;
        for (int i = 1; i < Room.SEATS; i++) {
            if (hurt[i] != hurt[0]) {
                same = false;
                break;
            }
        }
        if (same) {
            awardSide(room, -1);
            return;
        }
        java.util.Map<Integer, Long> total = new java.util.LinkedHashMap<>();
        for (int i = 0; i < Room.SEATS; i++) {
            if (room.seats[i] > 0 && !Room.observer(room.team[i])) {
                total.merge(room.sideOf(i), (long) hurt[i] & 0xFFFF_FFFFL, Long::sum);
            }
        }
        int best = -1;
        long low = Long.MAX_VALUE;
        for (var e : total.entrySet()) {
            if (e.getValue() < low) {
                low = e.getValue();
                best = e.getKey();
            }
        }
        awardSide(room, best);
    }

    /**
     * 官方 {@code pvp_request_pvp_rank}：只发一次 NOTI 49（空包）给座位上的人，等 CMD 61。
     * 官方是收到第一条 CMD 61 才插 10 秒定时器；这里发 NOTI 49 时就插，一个都不回也会结算。
     */
    private static void requestRank(Room room) {
        if (room.rankAsked) {
            return;
        }
        room.rankAsked = true;
        java.util.Arrays.fill(room.rankRecv, false);
        eachMember(room, other -> other.sendNow(NOTI_RANK, new byte[0]));
        schedule(room, RANK_TIMEOUT_MS, PHASE_FIGHT, () -> sendPvpEnd(room));
    }

    /**
     * 官方 {@code send_pvp_end} → {@code update_pvp_point} / {@code pvp_reward}：每人一条 NOTI 47。
     * 头座位是第一个 P_WIN 不是观战的座位，平局 0xFF。房间仍是 2，等 CMD 60。
     */
    private static void sendPvpEnd(Room room) {
        if (room.phase != PHASE_FIGHT) {
            return;
        }
        room.phase = PHASE_RESULT;
        java.util.Arrays.fill(room.endRecv, false);
        int shown = 0xFF;
        for (int i = 0; i < Room.SEATS; i++) {
            if (room.seats[i] > 0 && room.win[i] && !Room.observer(room.team[i])) {
                shown = i;
                break;
            }
        }
        for (GameSession member : members(room)) {
            int seat = room.seatIndex(member.characNo());
            boolean win = seat >= 0 && room.win[seat];
            boolean lose = seat >= 0 && !win && !Room.observer(room.team[seat]) && shown != 0xFF;
            int gain = win ? WIN_GAIN : 0;
            long next = Math.min(0xFFFF_FFFFL, member.winPoint() + gain);
            member.winPoint(next);
            try {
                if (member.store() != null && member.characNo() > 0) {
                    member.store().pvp().recordMatch(member.characNo(), win ? 1 : 0, lose ? 1 : 0, next);
                }
            } catch (RuntimeException ignored) {
                // 结算包照发。库写失败不该把客户端停在决斗里。
            }
            member.sendNow(NOTI_RESULT, resultBody(room, member, shown, gain, next));
        }
    }

    /**
     * 官方 {@code end_pvp_result} + {@code battle_reset}：{@code change_manager_to_winner}，状态回 1，
     * {@code make_state_info} 发给所有人。结算窗关了的客户端（+0x104）收到状态 1 才回房间（{@code 00428FAD}）。
     * 准备清 0 后再按座位发 NOTI 44，客户端的 {@code 0xDA3638} 跟着清，下一次点准备才是 1。
     */
    private static void endPvpResult(Room room) {
        if (room.phase != PHASE_RESULT) {
            return;
        }
        room.timerKey++;
        int oldHost = room.hostCharacNo;
        changeManagerToWinner(room);
        room.phase = PHASE_LOBBY;
        room.decided = false;
        room.rankAsked = false;
        for (int i = 0; i < Room.SEATS; i++) {
            room.ready[i] = 0;
            room.win[i] = false;
            room.rankRecv[i] = false;
        }
        tellState(room);
        tellReady(room);
        GameSession next = room.hostCharacNo != oldHost ? findCharac(room.hostCharacNo) : null;
        if (next != null && room.endRecv[room.hostSeat]) {
            // 新房主的模式按钮要 00961650 在人已经在房间里时跑一次（同 armModeButtons）。还没关结算窗的不发，免得决斗里改模式。
            armModeButtons(next, room);
        }
        java.util.Arrays.fill(room.endRecv, false);
    }

    /** 官方 {@code change_manager_to_winner}：房主那一边赢了不换；不然交给第一个赢的座位。平局不换。 */
    private static void changeManagerToWinner(Room room) {
        if (room.winTeam < 0 || room.sideOf(room.hostSeat) == room.winTeam) {
            return;
        }
        for (int i = 0; i < Room.SEATS; i++) {
            if (room.seats[i] > 0 && room.win[i] && room.sideOf(i) == room.winTeam) {
                room.hostCharacNo = room.seats[i];
                room.hostSeat = i;
                return;
            }
        }
    }

    /** 房间的定时器。房间重置（timerKey 变了）或阶段不对就不跑。 */
    private static void schedule(Room room, long delayMs, int phase, Runnable task) {
        int key = room.timerKey;
        TIMERS.schedule(() -> {
            try {
                synchronized (room) {
                    if (room.timerKey == key && room.phase == phase) {
                        task.run();
                    }
                }
            } catch (RuntimeException e) {
                System.err.println("[决斗] 定时器出错：" + e);
            }
        }, delayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * NOTI 47（{@code 00429FF0}）。
     * 头：u8 胜者座位（{@code 00968B60} 把它交进去，平局写 0xFF）、u32 未知写 0、
     * u8 自己的 {@code pvp_grade}（写本地 +0xCC，不能无故写成 0）、u32 自己的胜点总数
     * （行是自己时再进 {@code 0041E240}）、u8 人数。
     * 每人：u16 连接号、u32 未知（胜 1 负 0）、u8 那个人的段位、5 个 u32 未知写 0。
     * 尾：u16 本局获得的胜点（{@code 0041E5C0}）、u16 MVP 连接号（0xFFFF 跳过 MVP 窗）、
     * u32 经验写 0（{@code 0041E5B0}）、u8 0（{@code 0041E5D0}）。
     */
    private static byte[] resultBody(Room room, GameSession self, int shown, int gain, long total) {
        int rows = 0;
        for (int i = 0; i < Room.SEATS; i++) {
            if (room.seats[i] > 0) {
                rows++;
            }
        }
        PacketWriter w = new PacketWriter();
        w.u8(shown);
        w.u32(0);
        w.u8(gradeOf(self));
        w.u32((int) Math.min(total, 0xFFFF_FFFFL));
        w.u8(rows);
        int mvp = 0xFFFF;
        for (int i = 0; i < Room.SEATS; i++) {
            if (room.seats[i] <= 0) {
                continue;
            }
            int id = wireId(room.seats[i]);
            if (shown == i && id > 0) {
                mvp = id;
            }
            w.u16(id);
            w.u32(room.win[i] ? 1 : 0);
            w.u8(gradeOf(room.seats[i]));
            for (int n = 0; n < 5; n++) {
                w.u32(0);
            }
        }
        w.u16(gain);
        w.u16(mvp);
        w.u32(0);
        w.u8(0);
        return w.toArray();
    }

    private static int gradeOf(GameSession s) {
        return s == null ? 0 : gradeOf(s.characNo(), s);
    }

    private static int gradeOf(int characNo) {
        GameSession s = findCharac(characNo);
        return gradeOf(characNo, s);
    }

    private static int gradeOf(int characNo, GameSession s) {
        if (s == null || s.store() == null || characNo <= 0) {
            return 0;
        }
        try {
            return s.store().pvp().grade(characNo) & 0xFF;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static void close(Room room) {
        byte[] body = new PacketWriter().u16(room.id).u8(0).u8(0).u16(room.map).u8(0).toArray();
        eachOnPort(room, other -> other.sendNow(NOTI_ROOM_STATE, body));
    }

    private static void tellStart(Room room) {
        // 官方 start_pvp：u8 m_playMapNo（选好的 pvpmapbasicdata 下标，不是请求的 0）+ u8 模式。5 会藏起进房按钮。
        byte[] body = new byte[] {(byte) (room.playMap & 0xFF), (byte) modeByte(room)};
        eachMember(room, other -> other.sendNow(NOTI_START, body));
    }

    /** NOTI 42（官方 {@code make_state_info}）：u16 房间、u8 状态（1 大厅 / 2 决斗）、u8 房主座位、u16 地图、u8 模式。同端口所有人。 */
    private static void tellState(Room room) {
        byte[] body = new PacketWriter().u16(room.id).u8(room.listState()).u8(room.hostSeat)
                .u16(room.map).u8(modeByte(room)).toArray();
        eachOnPort(room, other -> other.sendNow(NOTI_ROOM_STATE, body));
    }

    private static void tellDie(Room room, int seat) {
        byte[] body = new byte[] {(byte) seat};
        eachMember(room, other -> other.sendNow(NOTI_DIE, body));
    }

    private static void tellReady(Room room) {
        for (int i = 0; i < Room.SEATS; i++) {
            if (room.seats[i] > 0) {
                tellReadyOne(room, i);
            }
        }
    }

    private static void tellReadyOne(Room room, int seat) {
        byte[] body = new byte[] {(byte) seat, (byte) room.ready[seat]};
        eachMember(room, other -> other.sendNow(NOTI_READY, body));
    }

    /**
     * NOTI 43 一座（官方 {@code make_seat_info(seat)}）：u16 房间、u8 模式、u8 1、u8 座位、u8 队伍、u16 连接号。
     * 空位是 队伍 FF / FE（关）、连接号 {@code 0xFFFF}。A4 这条没有官方后来多出来的那个 u8（{@code 004292E6}）。
     */
    private static void tellSeat(Room room, int seat) {
        PacketWriter w = new PacketWriter().u16(room.id).u8(modeByte(room)).u8(1);
        appendSeat(w, room, seat, true);
        byte[] body = w.toArray();
        eachOnPort(room, other -> other.sendNow(NOTI_SEAT, body));
    }

    /** NOTI 43 整间房（官方 {@code make_seat_info(0xffff)}，CMD 57 之后发）：头上新模式，有人的座位每人一条。 */
    private static void tellSeatsAll(Room room) {
        PacketWriter w = new PacketWriter().u16(room.id).u8(modeByte(room));
        int n = 0;
        for (int i = 0; i < Room.SEATS; i++) {
            if (room.seats[i] > 0) {
                n++;
            }
        }
        w.u8(n);
        for (int i = 0; i < Room.SEATS; i++) {
            if (room.seats[i] > 0) {
                appendSeat(w, room, i, true);
            }
        }
        byte[] body = w.toArray();
        eachOnPort(room, other -> other.sendNow(NOTI_SEAT, body));
    }

    /** 座位：u8 队伍、u16 连接号（NOTI 43 前面再加 u8 座位号）。人没连着时按空位写。 */
    private static void appendSeat(PacketWriter w, Room room, int seat, boolean withIndex) {
        int id = wireId(room.seats[seat]);
        if (withIndex) {
            w.u8(seat);
        }
        if (id > 0) {
            w.u8(room.team[seat]).u16(id);
        } else {
            w.u8(room.seats[seat] > 0 ? TEAM_OPEN : room.team[seat]).u16(0xFFFF);
        }
    }

    /** 人走了：这一座清成空位（队伍 FF、连接号 FFFF）。只清这一座，别的人还留在房间里。 */
    private static void tellVacateOne(Room room, int seat) {
        tellSeat(room, seat);
    }

    /**
     * 刚坐下的人，模式和房主座位是在 {@code 0xDA3640} 写上之前套的，{@code 00961650} 直接返回。
     * 先发一个不同的模式再发回真正的模式，这时人已经在房间里，按钮才会亮。只发给刚坐下的这个人。
     */
    private static void armModeButtons(GameSession s, Room room) {
        int real = modeByte(room);
        int temp = real == 0 ? 1 : 0;
        s.sendNow(NOTI_SEAT, modeOnly(room, temp));
        s.sendNow(NOTI_SEAT, modeOnly(room, real));
    }

    private static byte[] modeOnly(Room room, int mode) {
        return new PacketWriter().u16(room.id).u8(mode).u8(0).toArray();
    }

    /** 房间 +0x88。5 会藏起进房按钮，只允许 0 到 4。 */
    private static int modeByte(Room room) {
        int mode = room.teamMode;
        return mode >= 0 && mode <= 4 ? mode : 0;
    }

    private static void replyOk(GameSession s, int packetId) {
        s.reply(packetId, new byte[] {1});
        s.flush();
    }

    private static void replyFail(GameSession s, int packetId) {
        replyFail(s, packetId, 0);
    }

    /** {@code 00} + 失败码。房间回包（0041717D 等）认得的码才弹提示，0 不弹。 */
    private static void replyFail(GameSession s, int packetId, int code) {
        s.reply(packetId, new byte[] {0, (byte) code});
        s.flush();
    }

    private static void replyGone(GameSession s, int packetId, int kind) {
        s.reply(packetId, TradePackets.fail(TradePackets.E_GONE, kind));
        s.flush();
    }

    private static Created readCreate(GameSession s, byte[] body) {
        PacketReader r = new PacketReader(body == null ? new byte[0] : body);
        if (r.remaining() < 1) {
            return null;
        }
        int mode = r.u8();
        String name = "";
        if (mode == 0) {
            name = r.string(0x40, "决斗房间名").trim();
        }
        if (r.remaining() < 3) {
            return null;
        }
        r.u16();
        int hasPassword = r.u8();
        String password = "";
        if (hasPassword != 0) {
            password = r.string(0x40, "决斗房间密码");
        }
        int tail = r.remaining() >= 1 ? r.u8() : 0;
        if (name.isEmpty()) {
            name = s.characName() == null || s.characName().isBlank() ? "房间" : s.characName();
        }
        int id = NEXT_ID.getAndUpdate(n -> n >= 0xFFFF ? 1 : n + 1);
        return new Created(new Room(id, s.localPort(), s.characNo(), name, password, mode, tail));
    }

    private static Room hosted(GameSession s) {
        int no = s.characNo();
        for (Room room : rooms(s.localPort()).values()) {
            if (room.hostCharacNo == no) {
                return room;
            }
        }
        return null;
    }

    private static Room seated(GameSession s) {
        if (s == null || s.characNo() <= 0) {
            return null;
        }
        for (Room room : rooms(s.localPort()).values()) {
            if (room.seatIndex(s.characNo()) >= 0) {
                return room;
            }
        }
        return null;
    }

    private static ConcurrentHashMap<Integer, Room> rooms(int port) {
        return BY_PORT.computeIfAbsent(port, k -> new ConcurrentHashMap<>());
    }

    private static List<GameSession> members(Room room) {
        List<GameSession> out = new ArrayList<>();
        eachMember(room, out::add);
        return out;
    }

    private static void eachOnPort(Room room, java.util.function.Consumer<GameSession> fn) {
        for (GameSession other : SessionRegistry.playing()) {
            if (other.localPort() == room.port && other.isActive()) {
                fn.accept(other);
            }
        }
    }

    private static void eachMember(Room room, java.util.function.Consumer<GameSession> fn) {
        for (GameSession other : SessionRegistry.playing()) {
            if (other.localPort() == room.port && other.isActive() && room.seatIndex(other.characNo()) >= 0) {
                fn.accept(other);
            }
        }
    }

    private static GameSession findCharac(int characNo) {
        if (characNo <= 0) {
            return null;
        }
        for (GameSession other : SessionRegistry.playing()) {
            if (other.characNo() == characNo) {
                return other;
            }
        }
        return null;
    }

    /** 同端口已进世界的人。{@code withList} 为真时连 NOTI 56 一起发（只在新建时用）。 */
    private static void publish(Room room, boolean withList) {
        byte[] registered = registerBody(List.of(room));
        byte[] list = withList ? listBody(List.of(room)) : null;
        eachOnPort(room, other -> {
            other.sendNow(NOTI_REGISTER, registered);
            if (list != null) {
                other.sendNow(NOTI_LIST, list);
            }
        });
    }

    /**
     * 座位上的 u16。客户端用它认人（{@code 00428C90}）。
     * 没有对应连接时返回 0，登记包再把空位写成 {@code 0xFFFF}（人走了，不能当成一个坐下的人）。
     */
    private static int wireId(int characNo) {
        GameSession other = findCharac(characNo);
        if (other == null) {
            return 0;
        }
        int id = other.uniqueId();
        if (id > 0 && id < 0xFFFF) {
            return id;
        }
        return 0;
    }

    private static byte[] fail() {
        return new byte[] {0, 0};
    }

    private static byte[] listBody(List<Room> rooms) {
        int n = Math.min(rooms.size(), 255);
        PacketWriter w = new PacketWriter();
        w.u8(n);
        for (int i = 0; i < n; i++) {
            w.bytes(record(rooms.get(i)));
        }
        return w.toArray();
    }

    /**
     * NOTI 41（{@code 00428A87}）：u16 条数，然后每条
     * u16 房间号、u8 0、字符串名字（{@code 009E5C90}，最长 0x3F）、
     * u8 列表页（房间 +0x58。{@code 00919DC0} 只显示 1；2 是进行中，{@code 00918F8B} 藏起进房按钮。0 整行丢掉）、
     * u8 0（{@code 00537B30} 存到 +0x7C）、u16 地图、
     * u8 模式（写到 +0x88，5 会藏起按钮）、8×(u8 有人, u16 连接号)、u8 密码标志。
     * 空位连接号是 {@code 0xFFFF}：{@code 0053787E} 才走「人走了」，写成 0 会按新坐下的人去查。
     * 连接号对上自己时 {@code 00537EC0} 直接进房。密码标志为 0 时列表上的进房发 CMD 54。
     */
    private static byte[] registerBody(List<Room> rooms) {
        int n = Math.min(rooms.size(), 0xFFFF);
        PacketWriter w = new PacketWriter();
        w.u16(n);
        for (int i = 0; i < n; i++) {
            appendRegistered(w, rooms.get(i));
        }
        return w.toArray();
    }

    private static void appendRegistered(PacketWriter w, Room room) {
        byte[] name = room.name.getBytes(PacketWriter.DEFAULT_CHARSET);
        if (name.length > 0x3F) {
            name = java.util.Arrays.copyOf(name, 0x3F);
        }
        w.u16(room.id);
        w.u8(0);
        w.u32(name.length).bytes(name);
        w.u8(room.listState());
        // +0x7C 是房主座位号（00537B30）。必须指着一个还有人的座位，空位会在已经进房的人那里空指针。
        w.u8(room.hostSeat);
        w.u16(room.map);
        w.u8(modeByte(room));
        // 每座 u8 队伍（00537830 写 +0xC，官方 make_room_info 的 P_TEAMS）+ u16 连接号。空位 FF/FE + FFFF。
        for (int seat = 0; seat < Room.SEATS; seat++) {
            appendSeat(w, room, seat, false);
        }
        w.u8(room.password.isEmpty() ? 0 : 1);
    }

    /** REPLY 54：01 再加 8 个准备标记（{@code 0xDA3638}）。没点准备之前是 0。 */
    private static byte[] entered(Room room) {
        PacketWriter w = new PacketWriter();
        w.u8(1);
        for (int seat = 0; seat < Room.SEATS; seat++) {
            w.u8(room.ready[seat]);
        }
        return w.toArray();
    }

    /** 42 字节。布局见类注释。第 35 字节必须 &lt; 5，否则客户端不放进列表。 */
    private static byte[] record(Room room) {
        byte[] raw = new byte[42];
        byte[] name = cstring(room.name);
        System.arraycopy(name, 0, raw, 3, name.length);
        raw[33] = (byte) (room.map & 0xFF);
        raw[34] = (byte) ((room.map >> 8) & 0xFF);
        raw[37] = (byte) (room.id & 0xFF);
        raw[38] = (byte) ((room.id >> 8) & 0xFF);
        raw[39] = (byte) ((room.id >> 16) & 0xFF);
        raw[40] = (byte) ((room.id >> 24) & 0xFF);
        return raw;
    }

    /** 最多 29 字节加结尾 0，不把 GBK 双字节从中间切开。 */
    private static byte[] cstring(String text) {
        byte[] encoded = (text == null ? "" : text).getBytes(PacketWriter.DEFAULT_CHARSET);
        int n = 0;
        while (n < encoded.length && n < NAME_ROOM) {
            int b = encoded[n] & 0xFF;
            if (b >= 0x81 && b <= 0xFE) {
                if (n + 1 >= encoded.length || n + 1 >= NAME_ROOM) {
                    break;
                }
                n += 2;
            } else if (b == 0) {
                break;
            } else {
                n++;
            }
        }
        byte[] out = new byte[30];
        System.arraycopy(encoded, 0, out, 0, n);
        return out;
    }

    private record Created(Room room) {
    }

    private record Pending(int port, int roomId, int inviterUid) {
    }

    private static final class Room {
        final int id;
        final int port;
        int hostCharacNo;
        /** NOTI 41 写到房间 +0x7C 的房主座位。换房主时跟着改，不能指着空位。 */
        int hostSeat;
        final String name;
        final String password;
        final int mode;
        final int tail;
        volatile int map;
        volatile int teamMode;
        volatile int phase = PHASE_LOBBY;
        /** 座位上的角色号。0 是空位。建房时房主在下标 0，换房主后看 hostSeat。 */
        final int[] seats = new int[SEATS];
        /** 准备。0/1，对应 {@code 0xDA3638}。 */
        final int[] ready = new int[SEATS];
        /** 官方 P_TEAMS：0 个人、1 红、2 蓝、3/4 观战，空位 FF、关掉 FE。 */
        final int[] team = new int[SEATS];
        /** 官方 P_WIN：决斗中是「还活着」，分出胜负后改成「赢了」。 */
        final boolean[] win = new boolean[SEATS];
        final int[] kills = new int[SEATS];
        /** 官方 m_recvFlagB4（CMD 61）/ m_recvFlagBC（CMD 60）。 */
        final boolean[] rankRecv = new boolean[SEATS];
        final boolean[] endRecv = new boolean[SEATS];
        /** NOTI 45 发出去的地图下标（官方 m_playMapNo）。{@link #map} 是请求的（0 随机）。 */
        int playMap;
        /** 分出胜负了（官方 m_battleEndFlag / m_fieldC0），之后的 CMD 58 / 59 不再判。 */
        boolean decided;
        /** NOTI 49 发过了（官方 m_rankRequested）。 */
        boolean rankAsked;
        /** 赢的那一边：个人是座位号，组队是队伍。-1 平局（官方 m_userTable.SetWinTeam）。 */
        int winTeam = -1;
        /** 定时器作废用。开打、结算完、拆房都加一。 */
        int timerKey;

        static final int SEATS = 8;

        Room(int id, int port, int hostCharacNo, String name, String password, int mode, int tail) {
            this.id = id;
            this.port = port;
            this.hostCharacNo = hostCharacNo;
            this.name = name;
            this.password = password == null ? "" : password;
            this.mode = mode;
            this.tail = tail;
            // 官方 reset() 模式 2（A4 的 1，组队），座位队伍 0xFF；create_room 房主队伍 1。
            this.teamMode = MODE_TEAM;
            java.util.Arrays.fill(team, TEAM_OPEN);
            if (hostCharacNo > 0) {
                seats[0] = hostCharacNo;
                team[0] = TEAM_RED;
                hostSeat = 0;
            }
        }

        static boolean observer(int team) {
            return team == 3 || team == 4;
        }

        /** 判胜负时的一边：个人模式是座位号，组队、大将战是队伍。 */
        int sideOf(int seat) {
            boolean teams = teamMode == MODE_TEAM || teamMode == MODE_RELAY || teamMode == MODE_DM_TEAM;
            return teams ? team[seat] : seat;
        }

        /** NOTI 41 / 42 的状态字节：1 大厅，2 决斗中（结算窗也算，官方 end_pvp_result 才回 1）。 */
        int listState() {
            return phase == PHASE_LOBBY ? 1 : 2;
        }

        int seatOf(int index) {
            return seats[index];
        }

        int seatIndex(int characNo) {
            if (characNo <= 0) {
                return -1;
            }
            for (int i = 0; i < seats.length; i++) {
                if (seats[i] == characNo) {
                    return i;
                }
            }
            return -1;
        }

        /** 连接号优先，对不上再看是不是角色号。 */
        int seatOfWire(int wire) {
            if (wire <= 0) {
                return -1;
            }
            for (int i = 0; i < seats.length; i++) {
                if (seats[i] <= 0) {
                    continue;
                }
                if (wireId(seats[i]) == wire || seats[i] == wire) {
                    return i;
                }
            }
            return -1;
        }

        /** 官方 {@code check_joinable}：没有「空着并且队伍 FF」的座位就是满了，关掉的座位不算。 */
        boolean full() {
            for (int i = 0; i < SEATS; i++) {
                if (seats[i] == 0 && team[i] == TEAM_OPEN) {
                    return false;
                }
            }
            return true;
        }

        /** 不是观战的人数。 */
        int fighters() {
            int n = 0;
            for (int i = 0; i < SEATS; i++) {
                if (seats[i] > 0 && !observer(team[i])) {
                    n++;
                }
            }
            return n;
        }

        /** 官方 {@code check_start_state}：坐着又不是观战的人都准备了。 */
        boolean allFightersReady() {
            for (int i = 0; i < SEATS; i++) {
                if (seats[i] > 0 && !observer(team[i]) && ready[i] == 0) {
                    return false;
                }
            }
            return true;
        }

        /** 官方 {@code check_team_pvp}：组队、大将战、组队死斗，红蓝人数要一样。 */
        boolean teamsEven() {
            if (teamMode != MODE_TEAM && teamMode != MODE_RELAY && teamMode != MODE_DM_TEAM) {
                return true;
            }
            int red = 0;
            int blue = 0;
            for (int i = 0; i < SEATS; i++) {
                if (seats[i] > 0 && !observer(team[i])) {
                    if (team[i] == TEAM_RED) {
                        red++;
                    } else if (team[i] == TEAM_BLUE) {
                        blue++;
                    }
                }
            }
            return red == blue;
        }

        boolean hasTeamSides() {
            for (int i = 0; i < SEATS; i++) {
                if (seats[i] > 0 && (team[i] == TEAM_RED || team[i] == TEAM_BLUE)) {
                    return true;
                }
            }
            return false;
        }

        /** 官方 {@code is_closeable_seat_state}：只剩一个能打的人、空位又不到两个时不能再关。 */
        boolean closeable() {
            int fighters = fighters();
            int open = 0;
            for (int i = 0; i < SEATS; i++) {
                if (team[i] == TEAM_OPEN) {
                    open++;
                }
            }
            return !(fighters == 1 && open < 2);
        }

        boolean allRecv(boolean[] flags) {
            for (int i = 0; i < SEATS; i++) {
                if (seats[i] > 0 && !flags[i]) {
                    return false;
                }
            }
            return true;
        }

        int countRecv(boolean[] flags) {
            int n = 0;
            for (int i = 0; i < SEATS; i++) {
                if (seats[i] > 0 && flags[i]) {
                    n++;
                }
            }
            return n;
        }

        /**
         * 已经在房间里就不动。满员返回 false。官方 {@code join_room}：找第一个「空着并且队伍 FF」的座位；
         * 个人、个人死斗队伍 0，其余补人少的那一边（一样多给蓝）。
         */
        boolean seat(int characNo) {
            if (characNo <= 0) {
                return false;
            }
            synchronized (this) {
                for (int who : seats) {
                    if (who == characNo) {
                        return true;
                    }
                }
                for (int i = 0; i < seats.length; i++) {
                    if (seats[i] == 0 && team[i] == TEAM_OPEN) {
                        seats[i] = characNo;
                        ready[i] = 0;
                        win[i] = false;
                        team[i] = joinTeam();
                        return true;
                    }
                }
            }
            return false;
        }

        private int joinTeam() {
            if (teamMode == MODE_SINGLE || teamMode == MODE_DM_SINGLE) {
                return TEAM_SOLO;
            }
            int red = 0;
            int blue = 0;
            for (int i = 0; i < SEATS; i++) {
                if (seats[i] > 0 && team[i] == TEAM_RED) {
                    red++;
                } else if (seats[i] > 0 && team[i] == TEAM_BLUE) {
                    blue++;
                }
            }
            return blue <= red ? TEAM_BLUE : TEAM_RED;
        }

        /** 官方 {@code leave_room}：队伍写回 0xFF，准备、P_WIN 清掉。 */
        void clearSeat(int index) {
            seats[index] = 0;
            ready[index] = 0;
            team[index] = TEAM_OPEN;
            win[index] = false;
            kills[index] = 0;
            rankRecv[index] = false;
            endRecv[index] = false;
        }
    }
}
