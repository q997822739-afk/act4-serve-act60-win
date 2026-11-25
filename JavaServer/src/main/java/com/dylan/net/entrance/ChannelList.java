package com.dylan.net.entrance;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 频道表 —— 移植自 A12 参考实现 {@code server/src/channel_list.cpp}。
 *
 * <p>客户端的 {@code Script.pvf} 里有 {@code etc/channel_info.etc}，那是一份纯文本，
 * 含 11 个 {@code [server]} 块（组号 1..9 / 99 / 98）。网关要把它**规范化**成一份
 * "频道脚本"发给客户端（协议 9），并另外给两张表（协议 1 服务器表、协议 17 组表）。
 *
 * <h2>规范化规则（照 A12 的 {@code LoadScript}）</h2>
 * <ul>
 *   <li>去掉 {@code //} 之后的注释；去掉空行；去掉行首尾空白</li>
 *   <li>所有 {@code [dungeon]…[/dungeon]} 块**原样保留**</li>
 *   <li>{@code [server]} 块**只保留一个**（A12 保留第一个；本实现按组号挑，见下）</li>
 *   <li>输出用 {@code \n} 换行，每行都以 {@code \n} 结尾</li>
 * </ul>
 *
 * <h2>为什么可以只保留一个 {@code [server]} 块</h2>
 * A12 的 {@code ReadServerGroup} 在归一化后的脚本里**只接受恰好一个** {@code [server]} 块
 * （出现第二个就返回 false），所以一份脚本就是"一个服务器组"。
 *
 * <h2>与 A12 的两处有意偏差</h2>
 * <ol>
 *   <li>A12 固定选**第一个** {@code [server]} 块；本实现按**组号**挑（默认取
 *       {@code ServerConfig.MY_GROUP} = 2 = 디레지에），挑不到再退回第一个。
 *       理由：客户端的 {@code argv[5]} 选的就是组，启动器注释写着
 *       "1 = diregie = channel_info.etc group 2"。</li>
 *   <li>A12 还会跑 {@code pvpRules}（另一个文件）和 {@code ParseChannelBonus} 两道校验，
 *       本实现**没做** —— 它们只影响决斗场加成与校验，不影响脚本字节。缺这两样
 *       不会让客户端少显示频道。</li>
 * </ol>
 */
public final class ChannelList {

    /** A12 的 {@code ChannelList::GroupCount}。 */
    public static final int GROUP_COUNT = 109;

    private static final String[] KOREAN_GROUPS = {
            "cain", "diregie", "siroco", "prey", "casillas", "hilder", "ruke", "seria"
    };

    private static final String[] BUILTIN_SCRIPT = {
            "[dungeon]",
            "`[elven_guard]`",
            "<4::channel_info_dname_0>",
            "1",
            "2",
            "[/dungeon]",
            "",
            "[dungeon]",
            "`[granfloris]`",
            "<4::channel_info_dname_1>",
            "3",
            "4",
            "5",
            "6",
            "7",
            "8",
            "9",
            "[/dungeon]",
            "",
            "[server]",
            "1",
            "1\t<4::channel_info_cname_1>\t2\t`[elven_guard]`\t10\t21\t22\t23\t24\t25\t26\t27\t28\t29\t30",
            "6\t<4::channel_info_cname_6>\t3\t`[none]`\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0",
            "10\t<4::channel_info_cname_11>\t0\t`[granfloris]`\t5\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0",
            "11\t<4::channel_info_cname_12>\t0\t`[granfloris]`\t5\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0",
            "12\t<4::channel_info_cname_13>\t0\t`[granfloris]`\t5\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0",
            "13\t<4::channel_info_cname_14>\t0\t`[granfloris]`\t5\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0",
            "14\t<4::channel_info_cname_15>\t0\t`[granfloris]`\t5\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0",
            "[/server]",
    };

    private final int[] channelTypes = new int[256];
    private final List<Integer> channelIds = new ArrayList<>();
    private final List<Integer> channelPorts = new ArrayList<>();

    private String host = "127.0.0.1";
    private int port = 10011;
    private String channelName = "Local Channel 1";
    private byte[] channelScript = new byte[0];
    private int serverGroup;

    public void configure(String host, int port, String channelName) {
        if (host != null && !host.isBlank()) {
            this.host = host.trim();
        }
        if (port > 0 && port <= 0xFFFF) {
            this.port = port;
        }
        if (channelName != null && !channelName.isBlank()) {
            this.channelName = channelName;
        }
    }

    /**
     * 选定要对外开放的频道，并给每个频道定下端点。
     *
     * <h2>这一版只开一个频道</h2>
     * 默认只开 <b>频道 10</b>（{@code -Ds1a5.entrance.channel=10}），指向
     * {@code s1a5.entrance.gamePort}（= {@code s1a5.port}，10011）。
     *
     * <p>理由是**表里写的端口必须真的有人在听**。本版游戏服只绑一个端口，
     * 所以只敢承诺一个频道。把 38 行都写成 10011 的话，客户端会把所有角色都
     * 放进同一个地方 —— 这正是「选完频道总显示 10」那个 bug 的来源。
     * 一行一个真实端口，不说假话。
     *
     * <h2>怎么开多频道（本版未实现，留作参考）</h2>
     * 完整做法见 {@code docs/频道号固定10修复-20261011.md}，要点是**三处一起改**，
     * 缺一处就会出现"频道号对不上"：
     *
     * <ol>
     *   <li><b>游戏服绑定多个端口</b>：登录走 10011，其余频道各占一个，
     *       约定 {@code 20000 + 频道号}（11→20011、15→20015、6→20006）。
     *       需要一个 {@code ChannelEndpoints} 之类的类读映射表并逐个 bind，
     *       任一端口绑不上就清理已开的、整体退出。</li>
     *   <li><b>网关按同一张表下发端口</b>：把下面 {@code channelPorts} 的
     *       单一端口换成"按频道号查表"，表放在
     *       {@code channel-ports-<游戏端口>-<基址>.properties}，
     *       形如 {@code 10=10011} / {@code 11=20011}。</li>
     *   <li><b>会话按本地监听端口回填频道号</b>：{@code GameSession} 要在连接建立时
     *       根据自己被连的端口确定频道号并保持住，Noti1 开场包与商城票据都用它。
     *       <b>不能用进程常量</b>（那就等于所有玩家都是同一个频道），
     *       <b>也不能用"最后一次选择"</b>（并发玩家会互相踩）。</li>
     * </ol>
     *
     * <p>换言之：本类只负责"表里写哪些行、每行什么端口"，而
     * <b>让那些端口真的存在</b>是游戏服那边的事。两者必须用同一份映射。
     *
     * <p>另外保留了 {@code -Ds1a5.entrance.limit=N}（只取前 N 个频道）——
     * 那是做减法实验用的，注意它按**脚本顺序**截断，而脚本顺序是
     * {@code [1, 6, 10, 11, …]}，所以 {@code limit=1} 拿到的是频道 <b>1</b> 而不是 10。
     * 要精确挑一个频道请用 {@code s1a5.entrance.channel}。
     */
    public void fillChannelPorts() {
        // ⓪ A4：按客户端本地 channel_info.etc 的**完整频道顺序**下发，不能只留 1/6/51。
        //    客户端是按行下标去对本地 38 个频道的：只发 3 行时，51 在本地排第 38 位，
        //    客户端找不到它的端点，就把决斗频道过滤掉（实机频道选择只剩 ch01/ch06）。
        //    1→7002、6→7003、51→7004；其余频道没有独立游戏服，填默认端口兜底。
        if (!com.dylan.net.ServerConfig.A4_CHANNEL_PORTS.isEmpty()) {
            Integer pvpPort = com.dylan.net.ServerConfig.A4_CHANNEL_PORTS.get(51);
            channelPorts.clear();
            for (int id : channelIds) {
                Integer mapped = com.dylan.net.ServerConfig.A4_CHANNEL_PORTS.get(id);
                if (mapped != null) {
                    channelPorts.add(mapped);
                    continue;
                }
                // 52/53/55/59 也是决斗频道（type 4/5/6）。客户端的“索西雅的决斗场列表”
                // 会把它们画成多条“初级修炼场”，点哪一条都要连到同一个 7004。
                int type = channelTypes[id];
                channelPorts.add((type == 4 || type == 5 || type == 6) && pvpPort != null
                        ? pvpPort : port);
            }
            return;
        }
        // ① 如果显式指定了频道号，就只留那一个（默认 10）。
        //    放在 limit 之前：limit 是实验开关，指定频道才是正式选择方式。
        int wanted = intProperty("s1a5.entrance.channel", 0);
        if (wanted > 0) {
            if (!channelIds.contains(wanted)) {
                throw new IllegalArgumentException("频道 " + wanted
                        + " 不在这份脚本里；可选：" + channelIds);
            }
            int at = channelIds.indexOf(wanted);
            // 连同端口一起收敛到这一个频道，保持两个列表等长。
            channelIds.subList(0, at).clear();
            if (channelIds.size() > 1) {
                channelIds.subList(1, channelIds.size()).clear();
            }
        }

        // ② 实验用的截断（按脚本顺序取前 N 个）。
        int limit = limitFromProperty();
        if (limit > 0 && channelIds.size() > limit) {
            channelIds.subList(limit, channelIds.size()).clear();
        }

        // ③ 定端点。本版所有频道共用同一个 gameserver 端点 —— 因为游戏服
        //    确实只监听一个端口。多频道时这里换成"按频道号查映射表"。
        channelPorts.clear();
        for (int i = 0; i < channelIds.size(); i++) {
            channelPorts.add(port);
        }
    }

    /** 读一个整数系统属性；缺失/非法都返回缺省值。 */
    private static int intProperty(String key, int fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** {@code s1a5.entrance.limit}：0 或未设 = 不限。非法值按不限处理。 */
    private static int limitFromProperty() {
        String value = System.getProperty("s1a5.entrance.limit");
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            int n = Integer.parseInt(value.trim());
            return n > 0 ? n : 0;
        } catch (NumberFormatException e) {
            System.out.println("[entrance] s1a5.entrance.limit 不是数字，按不限处理：" + value);
            return 0;
        }
    }

    public byte[] channelScript() {
        return channelScript;
    }

    /**
     * 把要发给客户端的脚本换成**原文**（不再归一化）。
     *
     * <p>见 {@code EntranceMain} 里那段注释：A12 的 {@code LoadScript} 只保留一个
     * {@code [server]} 块，而它读的是磁盘上一份自定义副本；我们读的是客户端那份完整
     * 文件（11 个组），只发一个块会让客户端找不到别的组。
     *
     * <p>{@link #serverGroup()} 仍然按归一化时选定的块，不受这里影响。
     */
    public void useRawScript(byte[] raw) {
        if (raw != null && raw.length > 0) {
            channelScript = raw.clone();
        }
    }

    public List<Integer> channelIds() {
        return channelIds;
    }

    public int serverGroup() {
        return serverGroup;
    }

    public int channelType(int channel) {
        return channelTypes[channel & 0xFF];
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    /** A12 的 {@code ChannelList::BuiltinChannelScript()}：pvf 读不出来时的兜底。 */
    public static byte[] builtinChannelScript() {
        // ISO-8859-1 = 一字符对一字节，与 A12 直接操作原始字节的口径一致（这份内容是纯 ASCII）。
        return String.join("\n", BUILTIN_SCRIPT).concat("\n")
                .getBytes(StandardCharsets.ISO_8859_1);
    }

    /**
     * 在原始 {@code channel_info.etc} 文本里找出所有 {@code [server]} 块的组号
     * （按出现顺序）。返回 组号 → 块序号。
     */
    public static List<Integer> scanServerGroups(String text) {
        List<Integer> groups = new ArrayList<>();
        boolean afterServer = false;
        for (String raw : text.split("\r?\n", -1)) {
            String line = stripComment(raw).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.equals("[server]")) {
                afterServer = true;
                continue;
            }
            if (line.equals("[/server]") || line.equals("[dungeon]") || line.equals("[/dungeon]")) {
                afterServer = false;
                continue;
            }
            if (afterServer) {
                Integer value = firstUnsigned(line);
                groups.add(value == null ? 0 : value);
                afterServer = false;
            }
        }
        return groups;
    }

    /**
     * 归一化频道脚本并抽出频道号 / 频道类型。
     *
     * @param text          原始 {@code channel_info.etc} 文本
     * @param wantedGroup   想要的服务器组号；{@code <= 0} 表示"取第一个块"
     * @param log           日志回调（可为 null）
     * @return 成功返回 true（{@link #channelScript()} 即为要发给客户端的字节）
     */
    public boolean loadScript(String text, int wantedGroup, java.util.function.Consumer<String> log) {
        List<Integer> found = scanServerGroups(text);
        if (log != null) {
            log.accept("channel_info.etc 里的 [server] 块组号（按出现顺序）：" + found);
        }
        int selectedBlock = -1;
        if (wantedGroup > 0) {
            selectedBlock = found.indexOf(wantedGroup);
        }
        if (selectedBlock < 0) {
            if (wantedGroup > 0 && log != null) {
                log.accept("组号 " + wantedGroup + " 不在文件里，退回第一个 [server] 块");
            }
            selectedBlock = found.isEmpty() ? -1 : 0;
        }
        if (selectedBlock < 0) {
            if (log != null) {
                log.accept("文件里一个 [server] 块都没有");
            }
            return false;
        }
        serverGroup = found.get(selectedBlock);
        if (log != null) {
            log.accept("选用第 " + (selectedBlock + 1) + " 个 [server] 块，组号 = " + serverGroup);
        }

        channelIds.clear();
        java.util.Arrays.fill(channelTypes, 0);
        StringBuilder normalized = new StringBuilder();
        String section = "";
        int serverIndex = -1;
        boolean selected = false;
        boolean expectGroup = false;

        for (String raw : text.split("\r?\n", -1)) {
            String line = stripComment(raw);
            int firstNonWs = 0;
            while (firstNonWs < line.length()
                    && (line.charAt(firstNonWs) == ' ' || line.charAt(firstNonWs) == '\t'
                        || line.charAt(firstNonWs) == '\r')) {
                firstNonWs++;
            }
            if (firstNonWs >= line.length()) {
                continue;
            }
            int last = line.length();
            while (last > firstNonWs
                    && (line.charAt(last - 1) == ' ' || line.charAt(last - 1) == '\t'
                        || line.charAt(last - 1) == '\r')) {
                last--;
            }
            line = line.substring(firstNonWs, last);

            if (line.equals("[dungeon]") || line.equals("[server]")) {
                if (!section.isEmpty()) {
                    if (log != null) {
                        log.accept("嵌套段落（上一个 " + section + " 没闭合）就遇到 " + line);
                    }
                    return false;
                }
                section = line;
                if (line.equals("[server]")) {
                    serverIndex++;
                    selected = serverIndex == selectedBlock;
                    expectGroup = true;
                }
            } else if (line.equals("[/dungeon]") || line.equals("[/server]")) {
                if (line.equals("[/dungeon]") && !section.equals("[dungeon]")) {
                    return false;
                }
                if (line.equals("[/server]") && (!section.equals("[server]") || expectGroup)) {
                    return false;
                }
                if (section.equals("[dungeon]") || selected) {
                    normalized.append(line).append('\n');
                }
                section = "";
                selected = false;
                continue;
            } else if (section.isEmpty()) {
                if (log != null) {
                    log.accept("段落外的内容：" + line);
                }
                return false;
            } else if (section.equals("[server]")) {
                Integer value = firstUnsigned(line);
                if (value == null || value == 0 || value > 0xFF) {
                    if (log != null) {
                        log.accept("频道行开头不是一个合法编号：" + line);
                    }
                    return false;
                }
                if (expectGroup) {
                    expectGroup = false;
                } else if (selected) {
                    String[] tokens = line.split("[ \t]+");
                    if (tokens.length < 3) {
                        if (log != null) {
                            log.accept("频道行字段不足：" + line);
                        }
                        return false;
                    }
                    Integer type = parseUnsigned(tokens[2]);
                    if (type == null || type > 0xFF) {
                        if (log != null) {
                            log.accept("认不出频道分类：" + line);
                        }
                        return false;
                    }
                    channelTypes[value] = type;
                    channelIds.add(value);
                }
            }
            if (section.equals("[dungeon]") || selected) {
                normalized.append(line).append('\n');
            }
        }

        if (!section.isEmpty()) {
            if (log != null) {
                log.accept("文件结束时 " + section + " 还没闭合");
            }
            return false;
        }
        if (channelIds.isEmpty()) {
            if (log != null) {
                log.accept("选中的 [server] 块里一个频道都没有");
            }
            return false;
        }
        /*
         * 用 ISO-8859-1 而不是 US-ASCII 落盘：一字符对一字节，**字节保真**。
         * A12 是把文件原始字节直接当 std::string 处理的；用 US-ASCII 的话，
         * 万一有非 ASCII 字符漏过 `//` 注释剥离，会被替换成 '?' 而悄悄改坏脚本。
         */
        channelScript = normalized.toString().getBytes(StandardCharsets.ISO_8859_1);
        int group = readServerGroup(channelScript);
        if (log != null) {
            log.accept("归一化脚本 " + channelScript.length + " 字节，"
                    + channelIds.size() + " 个频道 " + channelIds
                    + "，ReadServerGroup = " + group);
        }
        return true;
    }

    /** A12 的 {@code ChannelList::ReadServerGroup}：归一化脚本里恰好一个 {@code [server]} 块。 */
    public static int readServerGroup(byte[] script) {
        String text = new String(script, StandardCharsets.ISO_8859_1);
        boolean found = false;
        int group = 0;
        String[] tokens = text.split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            if (!tokens[i].equals("[server]")) {
                continue;
            }
            if (found || i + 1 >= tokens.length) {
                return 0;
            }
            Integer value = parseUnsigned(tokens[i + 1]);
            if (value == null || value == 0 || value > 0xFF) {
                return 0;
            }
            group = value;
            found = true;
        }
        return found ? group : 0;
    }

    /** A12 的 {@code ChannelList::Encode()}：协议 1 的服务器表。 */
    public byte[] encode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4 + GROUP_COUNT * 72);
        writeU32(out, GROUP_COUNT);
        for (int i = 0; i < GROUP_COUNT; i++) {
            String groupName = i < KOREAN_GROUPS.length ? KOREAN_GROUPS[i] : "china_" + (i - 7);
            write(out, EntranceFrame.asciiFixed(groupName, 20));
            writeU32(out, 1);                                  // Active
            write(out, EntranceFrame.asciiFixed(channelName, 20));
            writeU32(out, 100);                                // LoadPercent
            writeU32(out, 1);                                  // Online
            write(out, EntranceFrame.asciiFixed(host, 16));
            writeU32(out, port);
        }
        return out.toByteArray();
    }

    /**
     * 正确的「频道信息」应答体 —— 照官方频道服 {@code df_channel_r} 的 DWARF 结构体复原。
     *
     * <h2>为什么原来那版会把客户端弄崩</h2>
     * A12 的 {@code BuildGroupTable} 产出的是 {@code u16 groupIndex + u32 rows + N×48字节}，
     * 而官方二进制里的 {@code tagSC_ASK_CHANNEL_INFO} 是：
     * <pre>
     *   tagPacketHeader (11 字节)  +  u32 server_group_count  +  N × tServerGroupInfo(24 字节)
     * </pre>
     * 客户端拿 {@code u32 rows=38} 当成"**38 个服务器组**"，再按 24 字节一组去读我们
     * 48 字节的行 —— 越读越歪，直接崩。空表（count=0）不崩但也没有频道，正好吻合。
     *
     * <h2>结构体（DWARF 原文）</h2>
     * <pre>
     *   tServerGroupInfo (24B)：char server_group_name[20]; u32 server_count;
     *   tpServerInfo     (48B)：char channel_name[20]; u32 max_user_num; u32 cur_user_num;
     *                           char server_ip[16];    u32 port;
     * </pre>
     */
    public byte[] buildChannelInfoReply() {
        /*
         * ★ 这就是 A12 的 `BuildGroupTable` 布局（6 字节头 + N×48）—— 版本对齐的参考实现
         *   ServerS4A12（C# 服务端，端口/频道号与本项目完全一致）在
         *   SelfTests/FreeDuelChannelSelfTest.cs:154-182 里**逐字节断言**了它：
         *
         *     const int headerSize = 6, channelBlockSize = 48, channelPortOffset = 44;
         *     plaintext.Length == headerSize + channelBlockSize * N
         *     && BitConverter.ToInt32(plaintext, 2) == N            // 偏移 2 = u32 频道数
         *     && ReadFixedAscii(plaintext, off, 20) == "#ch.68"     // ★ 频道名形如 "#ch.<号>"
         *     && BitConverter.ToInt32(plaintext, off + 44) == port
         *
         * ⚠️ 我上一轮照台服 df_channel_r 的 DWARF 改成"u32 组数 + 24字节组 + 48字节频道"是**错的**：
         *    那是**频道服↔bridge 的内部消息**（CB/BC_GET_CHANNEL_INFO），不是发给客户端的。
         *    客户端要的一直是 A12 这个 6 + N×48。
         *
         * 另外两个关键差异（很可能就是"闪退"的原因）：
         *   - 频道名必须是 `#ch.<频道号>`（客户端拿它当键去查显示名，查不到会崩）；
         *   - max/cur 人数参考值是 500 / 0（原来写 1000 / 1）。
         */
        ByteArrayOutputStream out = new ByteArrayOutputStream(6 + channelIds.size() * 48);
        /*
         * ★ 最小复现（-Ds1a5.entrance.minimal=1）：只发参考实现自检断言过的那 4 个频道。
         *
         *   ServerS4A12 的 GameNetworkConfig.cs:9-21 + FreeDuelChannelSelfTest.cs:158-182：
         *     11 → 10011（常规）  100 → 10161  68 → 10068（自由决斗）  200 → 10200（突袭）
         *     载荷长 = 6 + 48*4 = 198，频道名 "#ch.<号>"，max=500，cur=0
         *
         * 这是**唯一被自检逐字节断言过**的配置，变量最少；而且注意它发的 100/200
         * **并不在我们那份 channel_info.etc 的组里** —— 反过来说明客户端的频道列表是
         * **以这张表为准**渲染的（脚本只提供类型/名字引用等附加信息），
         * 这也和实测"回 0 行就没频道"吻合。
         */
        boolean minimal = "1".equals(System.getProperty("s1a5.entrance.minimal", "0"));
        int[] minIds = {11, 100, 68, 200};
        int[] minPorts = {10011, 10161, 10068, 10200};
        int count = minimal ? minIds.length : channelIds.size();

        /*
         * ★ 三个字段改成可配置 —— 值来自对 A12 真实应答的解密结果（2026-10-10 抓包）。
         *
         * 抓到的 A12 协议 18 明文（zlib 解开 1840 字节 = 6 + 38*48 + 10 尾巴）里，
         * 每一行都是：
         *     "Channel <频道号>"     ← 不是 "#ch.<号>"
         *     max_user_num = 1000    ← 不是 500
         *     cur_user_num = 1       ← 不是 0
         * 而 A12 源码 channel_list.cpp:239-242 正是
         *     _snprintf_s(..., "Channel %u", kIds[r]); out.U32(1000); out.U32(1);
         *
         * 之前那版照 ServerS4A12（另一个参考实现）用 "#ch.<号>" + 500/0，
         * 与 A12 实际行为不一致 —— 这就是保留这几个开关的原因：
         * 默认值改成 A12 实测值，但随时能切回去做对照实验。
         */
        String nameFormat = System.getProperty("s1a5.entrance.channelNameFormat", "Channel %d");
        int maxUser = Integer.getInteger("s1a5.entrance.maxUser", 1000);
        int curUser = Integer.getInteger("s1a5.entrance.curUser", 1);

        writeU16(out, 0);
        writeU32(out, count);
        for (int i = 0; i < count; i++) {
            int id = minimal ? minIds[i] : channelIds.get(i);
            int port = minimal ? minPorts[i] : channelPorts.get(i);
            write(out, EntranceFrame.asciiFixed(String.format(nameFormat, id), 20));
            writeU32(out, maxUser);                         // max_user_num
            writeU32(out, curUser);                         // cur_user_num
            write(out, EntranceFrame.asciiFixed(host, 16));  // server_ip
            writeU32(out, port);                            // port
        }
        return out.toByteArray();
    }

    /** 服务器组名（官方那 8 个：cain/diregie/siroco/prey/casillas/hilder/ruke/seria）。 */
    public String groupName() {
        String[] names = {"cain", "diregie", "siroco", "prey",
                "casillas", "hilder", "ruke", "seria"};
        int index = serverGroup - 1;
        return index >= 0 && index < names.length ? names[index] : "cain";
    }

    /**
     * 套用 A12 的 {@code pvp_channels.etc} 规则，改写决斗频道的脚本行。
     *
     * <p><b>为什么必须做</b>：A12 的生产日志写着 {@code channel script: 3173 bytes}，
     * 而我们直接发的归一化脚本是 <b>3163</b> —— 差的 10 字节正是这 5 行被改写过。
     * A12 的 {@code channel_list.cpp:117-122} 把命中的行从「id 名字 分类 tag bonus + 10 个区间比」
     * 改成「id 名字 分类 tag bonus + <b>8 个规则字段</b> + 原字段[13] 原字段[14]」，
     * 而且 {@code :129-131} 有硬校验（{@code ParseChannelBonus} 不过就整份脚本作废）。
     *
     * <p>参考文件原文（{@code ACT12_Server/pvp_channels.etc}，8 个字段：
     * {@code channel min_rank max_rank type reinforcement min_level max_level mode team_size}）：
     * <pre>
     *   51 0 4  0 0 10 39 0 0
     *   52 0 10 1 1 10 70 0 0
     *   53 10 15 2 1 10 70 0 0
     *   55 0 29 4 1 10 70 0 0
     *   59 0 29 5 0 10 70 0 0
     * </pre>
     */
    public static String applyPvpRules(String text) {
        String[][] rules = {
                {"51", "0", "4", "0", "0", "10", "39", "0", "0"},
                {"52", "0", "10", "1", "1", "10", "70", "0", "0"},
                {"53", "10", "15", "2", "1", "10", "70", "0", "0"},
                {"55", "0", "29", "4", "1", "10", "70", "0", "0"},
                {"59", "0", "29", "5", "0", "10", "70", "0", "0"},
        };
        java.util.Map<String, String[]> byId = new java.util.HashMap<>();
        for (String[] r : rules) {
            byId.put(r[0], r);
        }
        StringBuilder out = new StringBuilder(text.length() + 64);
        int rewritten = 0;
        for (String line : text.split("\n", -1)) {
            String[] f = line.split("\t", -1);
            String[] rule = f.length >= 15 ? byId.get(f[0].trim()) : null;
            if (rule == null) {
                out.append(line).append('\n');
                continue;
            }
            // id 名字 分类 tag bonus + 8 个规则字段 + 原字段[13] 原字段[14]
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i <= 4; i++) {
                sb.append(f[i]).append('\t');
            }
            for (int i = 1; i < rule.length; i++) {
                sb.append(rule[i]).append('\t');
            }
            sb.append(f[13]).append('\t').append(f[14]);
            out.append(sb).append('\n');
            rewritten++;
        }
        return rewritten == 0 ? text : out.toString();
    }

    /**
     * ACT5 reads a u32 channel count at payload offset 0, followed by 48-byte rows.
     * DNF.exe 0xB16175..0xB161D9 reads the count and advances exactly four bytes.
     * A12's extra u16 group index must not be sent to this client.
     */
    public byte[] buildAct5Table(int rows) {
        byte[] a12 = buildGroupTable(0, rows, 48, 0);
        if (a12.length < 6) {
            throw new IllegalStateException("Channel endpoints are incomplete");
        }
        return java.util.Arrays.copyOfRange(a12, 2, a12.length);
    }

    public byte[] buildGroupTable(int groupIndex, int rows) {
        return buildGroupTable(groupIndex, rows, 48, 0);
    }

    /**
     * 组表，带**行宽**与**末尾补零**两个可调参数 —— 用来做行格式搜索。
     *
     * <p>动机：实测"38 行真实表 → 客户端崩；空表 → 不崩也不显示频道"。
     * 如果客户端是按一个**比 48 字节更宽**的行去解析，它就会读越界并崩溃。
     * 在末尾补一大段 0 既能验证这个假设，也能从"它列出几个频道"反推行宽。
     *
     * @param rowWidth  每行写多少字节（≥48；多出来的补 0）
     * @param padBytes  整个表末尾再补多少 0
     */
    public byte[] buildGroupTable(int groupIndex, int rows, int rowWidth, int padBytes) {
        int idCount = channelIds.size();
        if (channelPorts.size() != idCount) {
            return new byte[0];
        }
        for (int p : channelPorts) {
            if (p == 0) {
                return new byte[0];
            }
        }
        int width = Math.max(48, rowWidth);
        int count = Math.min(rows, idCount);
        /*
         * ★ 逐行端口递增开关（-Ds1a5.entrance.portStride=1）。
         *
         * 实证：A12 的真实应答里 38 行端口各不相同（13644..13681，每个频道一个
         * 独立监听）。我们只有一台游戏服，所以所有行共用同一个端口 —— 这是目前
         * 与 A12 相比唯一还没对齐的字段。
         *
         * 打开后每行端口 = 基准端口 + 行号，形态与 A12 一致（端口本身不可达也
         * 无妨：客户端是先渲染列表、选中后才去连）。
         */
        boolean stride = "1".equals(System.getProperty("s1a5.entrance.portStride", "0"));
        boolean a4 = !com.dylan.net.ServerConfig.A4_CHANNEL_PORTS.isEmpty();
        ByteArrayOutputStream out = new ByteArrayOutputStream(6 + count * width + Math.max(0, padBytes));
        writeU16(out, groupIndex);
        writeU32(out, count);
        for (int r = 0; r < count; r++) {
            write(out, EntranceFrame.asciiFixed("Channel " + channelIds.get(r), 20));
            // A4 官方表：上限 100、当前 0（ACT5 是 1000 / 1）。
            writeU32(out, a4 ? 100 : 1000);
            writeU32(out, a4 ? 0 : 1);
            write(out, EntranceFrame.asciiFixed(host, 16));
            writeU32(out, stride ? (channelPorts.get(r) + r) : channelPorts.get(r));
            for (int k = 48; k < width; k++) {
                out.write(0);
            }
        }
        for (int k = 0; k < padBytes; k++) {
            out.write(0);
        }
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- helpers

    private static String stripComment(String line) {
        int at = line.indexOf("//");
        return at < 0 ? line : line.substring(0, at);
    }

    private static Integer firstUnsigned(String line) {
        String[] tokens = line.split("[ \t]+");
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            return parseUnsigned(token);
        }
        return null;
    }

    private static Integer parseUnsigned(String token) {
        try {
            int value = Integer.parseInt(token.trim());
            return value < 0 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void write(ByteArrayOutputStream out, byte[] data) {
        out.write(data, 0, data.length);
    }

    private static void writeU16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
    }

    private static void writeU32(ByteArrayOutputStream out, long value) {
        out.write((int) (value & 0xFF));
        out.write((int) ((value >>> 8) & 0xFF));
        out.write((int) ((value >>> 16) & 0xFF));
        out.write((int) ((value >>> 24) & 0xFF));
    }

    /** 调试用：把组表按行拆开描述。 */
    public static String describeGroupTable(byte[] table) {
        if (table == null || table.length < 6) {
            return "(空)";
        }
        long groupIndex = (table[0] & 0xFFL) | ((table[1] & 0xFFL) << 8);
        long rows = EntranceFrame.readU32(table, 2);
        StringBuilder sb = new StringBuilder();
        sb.append("groupIndex=").append(groupIndex).append(" rows=").append(rows);
        int offset = 6;
        long shown = 0;
        while (offset + 48 <= table.length && shown < rows && shown < 8) {
            String name = readAscii(table, offset, 20);
            long a = EntranceFrame.readU32(table, offset + 20);
            long b = EntranceFrame.readU32(table, offset + 24);
            String h = readAscii(table, offset + 28, 16);
            long p = EntranceFrame.readU32(table, offset + 44);
            sb.append("\n      [").append(shown).append("] ").append(name)
              .append(" a=").append(a).append(" b=").append(b)
              .append(" host=").append(h).append(" port=").append(p);
            offset += 48;
            shown++;
        }
        if (rows > shown) {
            sb.append("\n      …（还有 ").append(rows - shown).append(" 行）");
        }
        return sb.toString();
    }

    private static String readAscii(byte[] data, int offset, int width) {
        int end = offset;
        while (end < offset + width && end < data.length && data[end] != 0) {
            end++;
        }
        return new String(data, offset, end - offset, StandardCharsets.US_ASCII);
    }

    /** 调试用：服务器表前几行。 */
    public static String describeServerTable(byte[] table) {
        if (table == null || table.length < 4) {
            return "(空)";
        }
        long count = EntranceFrame.readU32(table, 0);
        StringBuilder sb = new StringBuilder("组数=" + count);
        int offset = 4;
        for (int i = 0; i < 4 && offset + 72 <= table.length; i++) {
            String group = readAscii(table, offset, 20);
            long active = EntranceFrame.readU32(table, offset + 20);
            String ch = readAscii(table, offset + 24, 20);
            long load = EntranceFrame.readU32(table, offset + 44);
            long online = EntranceFrame.readU32(table, offset + 48);
            String h = readAscii(table, offset + 52, 16);
            long p = EntranceFrame.readU32(table, offset + 68);
            sb.append("\n      [").append(i).append("] group=").append(group)
              .append(" active=").append(active).append(" channel=").append(ch)
              .append(" load=").append(load).append(" online=").append(online)
              .append(" host=").append(h).append(" port=").append(p);
            offset += 72;
        }
        return sb.toString();
    }

    /** 给日志用：频道的 host:port 一览。 */
    public Map<Integer, String> channelEndpoints() {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (int i = 0; i < channelIds.size(); i++) {
            int p = i < channelPorts.size() ? channelPorts.get(i) : 0;
            map.put(channelIds.get(i), host + ":" + p);
        }
        return map;
    }
}
