package com.dylan.net.entrance;

/**
 * 把频道网关（entrance）**作为独立进程**跑起来的入口。
 *
 * <pre>
 *   java -cp "&lt;jar&gt;" com.dylan.net.entrance.EntranceMain [端口]
 *   java -cp "&lt;jar&gt;" com.dylan.net.entrance.EntranceMain selftest   # 只打印，不监听
 * </pre>
 *
 * <p>装配逻辑在 {@link EntranceService}，与内嵌进
 * {@link com.dylan.net.GameServer} 的用法**共用同一套** {@code s1a5.entrance.*} 属性。
 * 本类只负责：解析位置参数、起服务、把主线程挂住。
 *
 * <p>注意：游戏服现在**默认已经内嵌**了同一个网关（单进程双端口 10011 + 7001）。
 * 如果游戏服在跑，这里再起会撞端口 —— 那时应该直接用游戏服，或者把内嵌的关掉
 * （{@code -Ds1a5.entrance.enabled=0}）。
 */
public final class EntranceMain {

    private EntranceMain() {
    }

    public static void main(String[] args) throws Exception {
        boolean selfTest = args.length > 0 && args[0].equalsIgnoreCase("selftest");
        int port = EntranceService.num("entrance.port", 7001);
        if (!selfTest && args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("[entrance] 位置参数不是端口号，忽略：" + args[0]);
            }
        }
        int maxConnections = EntranceService.num("entrance.maxConnections", 64);

        EntranceService service = new EntranceService(port, maxConnections);

        if (selfTest) {
            // 自检要能看到装配结果，所以先跑一次装配再打印，不监听端口。
            service.load();
            printSelfTest(service);
            return;
        }

        if (!service.start()) {
            System.out.println("[entrance] 启动失败，退出。");
            System.exit(1);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(service::stop, "entrance-shutdown"));
        // 主线程挂住，等 accept 线程干活。
        synchronized (EntranceMain.class) {
            EntranceMain.class.wait();
        }
    }

    /**
     * 自检模式（{@code EntranceMain selftest}）：**不监听端口**，只把频道数据与编码结果打出来。
     *
     * <p>用途：在没有客户端、也不占端口的前提下，一次性验掉 ——
     * ① pvf 读取路径对不对；② 选中的是哪个 {@code [server]} 块、频道号有哪些；
     * ③ 归一化后的频道脚本长什么样；④ 组表的字节布局；
     * ⑤ AES 是不是 16 字节块、deflate 是不是 zlib 流。
     */
    private static void printSelfTest(EntranceService service) {
        ChannelList channels = service.channels();

        System.out.println();
        System.out.println("================= 自检（不监听端口）=================");
        System.out.println("  pvf            " + service.pvf());
        System.out.println("  频道脚本       " + channels.channelScript().length + " 字节");
        System.out.println("  服务器组号     " + channels.serverGroup());
        System.out.println("  频道号         " + channels.channelIds());
        System.out.println("  频道指向       " + channels.channelEndpoints());
        System.out.println();

        System.out.println("--- 归一化后的频道脚本（最多打 80 行）---");
        String script = new String(channels.channelScript(),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        String[] lines = script.split("\n", -1);
        for (int i = 0; i < Math.min(80, lines.length); i++) {
            System.out.println(String.format("%4d| %s", i + 1, lines[i]));
        }
        if (lines.length > 80) {
            System.out.println("     …（共 " + lines.length + " 行）");
        }
        System.out.println();

        System.out.println("--- ACT5 频道表（协议 17，应答协议号 18）---"
                + ChannelList.describeGroupTable(
                        channels.buildAct5Table(channels.channelIds().size())));
        System.out.println("--- A12 风格组表（对照）---"
                + ChannelList.describeGroupTable(
                        channels.buildGroupTable(0, channels.channelIds().size())));
        System.out.println("--- 服务器表（协议 1，应答协议号 3）---"
                + ChannelList.describeServerTable(channels.encode()));
        System.out.println();

        System.out.println("--- 编码自检 ---");
        System.out.println("  协议 11 发出去的 32 字节密钥  " + EntranceFrame.hex(EntranceCodec.KEY32));
        System.out.println("  真正用于 AES 的 16 字节        " + EntranceFrame.hex(EntranceCodec.AES_KEY));
        byte[] sample = "hello".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] encrypted = EntranceCodec.aesEncryptZeroPadded(sample);
        System.out.println("  AES-零填充(\"hello\")           " + encrypted.length
                + " 字节（应为 16）: " + EntranceFrame.hex(encrypted));
        byte[] wrapped = EntranceCodec.wrap(sample);
        System.out.println("  Wrap(\"hello\")                  " + wrapped.length
                + " 字节（zlib 流，应以 78 开头）: " + EntranceFrame.hex(wrapped));
        System.out.println("====================================================");
    }
}
