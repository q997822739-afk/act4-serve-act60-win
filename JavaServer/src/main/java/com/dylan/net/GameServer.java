package com.dylan.net;

import com.dylan.store.GameStore;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public final class GameServer {

    private final int port;

    public GameServer(int port) {
        this.port = port;
    }

    private static GameStore openStore() {
        if (!ServerConfig.DB) {
            return null;
        }
        return GameStore.open(ServerConfig.DB_URL);
    }

    private static com.dylan.pvf.GameScripts openScripts() {
        if (!ServerConfig.PVF) {
            return null;
        }
        // 直接调 load 而不是 tryLoad：失败原因要跟着异常出来，不能只剩一句"载入失败"。
        try {
            return com.dylan.pvf.GameScripts.load(ServerConfig.PVF_PATH);
        } catch (java.io.IOException | RuntimeException e) {
            throw new IllegalStateException(
                    "载入 " + ServerConfig.PVF_PATH + " 失败，服务端不启动。"
                            + "确实要在没有脚本的情况下跑，加 -Ds1a5.pvf.enabled=0", e);
        }
    }

    private static void announceSource() {
        System.out.println("S1A5 —— DNF ACT5 单人服务端。GNU AGPL-3.0，"
                + "见仓库里的 LICENSE 与 NOTICE.md。一次性快照，作者不维护。");
    }

    /**
     * 顺带把频道网关（entrance）起在同一个进程里 —— 这就是"单进程双端口"。
     *
     * <p>端口 10011 是游戏本体（客户端登录走这里）；7001 只在客户端的
     * **频道选择界面**被连接，客户端在那个界面会无视命令行地址去连硬编码的
     * {@code 121.14.107.176:7001}。没有东西应答，界面就永远停在
     * 「正在接收频道信息」，几秒后客户端退出。
     *
     * <p>网关是**尽力而为**的：端口被占或脚本读不到时只告警，不影响游戏本体。
     * 用 {@code -Ds1a5.entrance.enabled=0} 关掉。
     */
    private static com.dylan.net.entrance.EntranceService startEntrance() {
        if (!ServerConfig.ENTRANCE) {
            System.out.println("[entrance] 已禁用（-Ds1a5.entrance.enabled=0）");
            return null;
        }
        com.dylan.net.entrance.EntranceService entrance =
                new com.dylan.net.entrance.EntranceService(
                        ServerConfig.ENTRANCE_PORT, ServerConfig.ENTRANCE_MAX_CONNECTIONS,
                        ServerConfig.ENTRANCE_CHANNEL);
        // start() 不阻塞：accept 跑在自己的（守护）线程上，所以这里不能挂住。
        if (!entrance.start()) {
            return null;
        }
        // 让优雅停机也收网关 —— 否则 Ctrl+C 时 run() 的 finally 可能来不及跑，
        // 网关连接会被 JVM 直接掐断。
        GracefulShutdown.onStop(entrance::stop);
        return entrance;
    }

    public void run() throws InterruptedException {
        announceSource();

        com.dylan.net.entrance.EntranceService entrance = null;
        GameStore store = openStore();
        com.dylan.game.GmManager.init(store);
        // 上一个服务端进程可能崩溃退出（没走小退/断线），server_presence 里会留下过期行 ——
        // 那会让网页 GM 误判成"角色还在游戏里"而拒写。新进程起来先清空整表（见 PlayerPresence）。
        com.dylan.game.PlayerPresence.resetAll(store);
        com.dylan.game.ItemHistoryRecorder.start(store);
        com.dylan.pvf.GameScripts scripts = openScripts();
        com.dylan.game.NameFilter.init(scripts, store);
        com.dylan.account.AccountService accounts = store == null ? null
                : new com.dylan.account.AccountService(store);
        EventLoopGroup boss = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        EventLoopGroup workers = new MultiThreadIoEventLoopGroup(
                ServerConfig.WORKER_THREADS, NioIoHandler.newFactory());
        try {
            ServerBootstrap b = new ServerBootstrap()
                    .group(boss, workers)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 64)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast("encode", new DownstreamPacketEncoder())
                                    .addLast("session", new GameSessionHandler(
                                            store, scripts, accounts));
                        }
                    });

            // A4 每个频道一个端口（ServerConfig.A4_CHANNEL_PORTS）。命令行端口不在表里就额外监听它。
            java.util.Set<Integer> ports = new java.util.LinkedHashSet<>();
            ports.add(port);
            ports.addAll(ServerConfig.A4_CHANNEL_PORTS.values());
            Channel server = null;
            for (int p : ports) {
                Channel bound = b.bind(p).sync().channel();
                System.out.println("游戏服监听 " + p + "（A4 频道 " + ServerConfig.a4ChannelForPort(p) + "）");
                // NOTI 1 把同一个端口当 NAT 探测地址发给客户端，UDP 也要应答，见 UdpProbeServer。
                try {
                    Channel udp = UdpProbeServer.bind(workers, p);
                    bound.closeFuture().addListener(f -> udp.close());
                    System.out.println("NAT 探测 UDP 监听 " + p);
                } catch (Exception e) {
                    System.out.println("NAT 探测 UDP " + p + " 绑定失败：" + e);
                }
                if (server == null) {
                    server = bound;
                } else {
                    Channel primary = server;
                    primary.closeFuture().addListener(f -> bound.close());
                }
            }
            GracefulShutdown.arm(server);
            // 频道网关：客户端在「选择频道」界面硬连 7001，没有它就停在"正在接收频道信息"。
            entrance = startEntrance();
            // 外网组队中转：UDP 监听在网关同一个端口号上（登录回包 / NOTI 11 都指这里），
            // 按 MTUPD 头里的身份转发。见 com.dylan.game.UdpPartyRelay。
            com.dylan.game.UdpPartyRelay.start();
            com.dylan.game.event.EventScheduler.start(store);
            // 在线收信：周期性把外部进程新插入的邮件推给在游戏里的角色（不用小退）。
            com.dylan.game.MailPoller.start(store);
            server.closeFuture().sync();
        } finally {
            if (entrance != null) {
                entrance.stop();
            }
            com.dylan.game.UdpPartyRelay.stop();
            com.dylan.game.MailPoller.stop();
            com.dylan.game.event.EventScheduler.stop();
            com.dylan.game.MaintenanceService.stop();
            workers.shutdownGracefully();
            boss.shutdownGracefully();
            com.dylan.game.ItemHistoryRecorder.stop();
            if (store != null) {
                store.close();
            }
            if (scripts != null) {
                try {
                    scripts.close();
                } catch (java.io.IOException e) {
                }
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        int port = ServerConfig.PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                return;
            }
        }
        new GameServer(port).run();
    }
}
