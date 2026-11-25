package com.dylan.net;

import com.dylan.game.CommandRouter;
import com.dylan.game.GameHandlers;
import com.dylan.game.GameSession;
import com.dylan.protocol.CmdName;
import com.dylan.protocol.body.Noti1Body;
import com.dylan.protocol.crypto.A4Cipher;
import com.dylan.protocol.crypto.Crc32;
import com.dylan.store.GameStore;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class GameSessionHandler extends ChannelInboundHandlerAdapter {
    private static final CommandRouter ROUTER = GameHandlers.router();

    private final ByteArrayOutputStream batch = new ByteArrayOutputStream(512);

    /** A4 握手：收到第一个 CMD 0 后发过 NOTI 1。 */
    private boolean channelInfoSent;

    private int batchSeq;
    private long connectedAtNanos;

    private final GameStore store;
    private final com.dylan.pvf.GameScripts scripts;

    private GameSession session;

    private SessionExecutor executor;

    private final com.dylan.account.AccountService accounts;

    public GameSessionHandler(GameStore store) {
        this(store, null, null);
    }

    public GameSessionHandler(GameStore store, com.dylan.pvf.GameScripts scripts) {
        this(store, scripts, null);
    }

    public GameSessionHandler(GameStore store, com.dylan.pvf.GameScripts scripts,
                              com.dylan.account.AccountService accounts) {
        this.store = store;
        this.scripts = scripts;
        this.accounts = accounts;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        connectedAtNanos = System.nanoTime();
        if (denyWhileMaintaining(ctx)) {
            return;
        }
        this.executor = SessionExecutor.virtual(String.valueOf(ctx.channel().remoteAddress()));
        this.session = new GameSession(ctx, store, scripts, accounts, executor);
        com.dylan.game.SessionRegistry.enter(session);

        submitToSession(ctx, () -> {
            if (!ServerConfig.GREET) {
                return;
            }
            if (ServerConfig.GREET_DELAY_MS <= 0) {
                greet(ctx);
                return;
            }
            ctx.executor().schedule(() -> submitToSession(ctx, () -> greet(ctx)),
                    ServerConfig.GREET_DELAY_MS, TimeUnit.MILLISECONDS);
        });
    }

    private boolean denyWhileMaintaining(ChannelHandlerContext ctx) {
        if (!com.dylan.game.MaintenanceService.prohibitingConnect()) {
            return false;
        }
        com.dylan.game.MaintenanceService.status();
        ctx.writeAndFlush(OutboundCmdReply.of(
                        com.dylan.protocol.body.Cmd1ReplyBody.PACKET_ID,
                        com.dylan.protocol.body.Cmd1ReplyBody.rejected(
                                com.dylan.game.MaintenanceService.PROHIBIT_ERROR_CODE)))
                .addListener(io.netty.channel.ChannelFutureListener.CLOSE);
        return true;
    }

    private void submitToSession(ChannelHandlerContext ctx, Runnable task) {
        if (!executor.submit(task)) {
            ctx.close();
        }
    }

    /** A4 频道第一包是 NOTI 0（16 字节令牌）。NOTI 1 要等客户端回 CMD 0，见 {@link #answerCommands}。 */
    private void greet(ChannelHandlerContext ctx) {
        if (!ctx.channel().isActive()) {
            return;
        }
        byte[] token = new byte[16];
        byte[] name = ServerConfig.A4_CHANNEL_TOKEN.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(name, 0, token, 0, Math.min(name.length, token.length));
        ctx.writeAndFlush(OutboundNoti.of(0, token));
    }

    private void sendA4ChannelInfo(ChannelHandlerContext ctx) {
        int udp = ctx.channel().localAddress() instanceof java.net.InetSocketAddress local
                ? local.getPort() : ServerConfig.PORT;
        String host = ServerConfig.PEER_UDP_HOST.isBlank() ? "127.0.0.1" : ServerConfig.PEER_UDP_HOST;
        // A4 频道握手 NOTI 1（收到 CMD 0 后发），组号 + 频道号 + 地址 + 端口×2（probe_channel.py 逐字节一致）
        session.sendNow(1, Noti1Body.a4Channel(ServerConfig.A4_GROUP,
                ServerConfig.a4ChannelForPort(udp), host, udp));
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf in)) {
            ctx.fireChannelRead(msg);
            return;
        }
        if (session == null) {
            ReferenceCountUtil.release(in);
            return;
        }
        try {
            int n = in.readableBytes();
            byte[] chunk = new byte[n];
            in.readBytes(chunk);
            batch.write(chunk, 0, n);
        } finally {
            ReferenceCountUtil.release(in);
        }
        drainFrames(ctx);
    }

    private void drainFrames(ChannelHandlerContext ctx) {
        byte[] buffered = batch.toByteArray();
        UpstreamFrame.Drained drained = UpstreamFrame.drain(buffered, buffered.length);

        if (drained.corrupt()) {
            System.err.println("[断开] 上行帧损坏（长度或 CRC 不对）");
            ctx.close();
            return;
        }

        int consumed = drained.consumed();
        if (consumed > 0) {
            batch.reset();
            batch.write(buffered, consumed, buffered.length - consumed);
        }
        if (drained.frames().isEmpty()) {
            return;
        }

        batchSeq++;
        if (ServerConfig.WIRE_TRACE) {
            byte[] consumedBytes = java.util.Arrays.copyOf(buffered, consumed);
            UpstreamFormatProbe.probe(consumedBytes);
        }

        List<UpstreamFrame> frames = drained.frames();
        pauseReadIfBacklogged(ctx);
        submitToSession(ctx, () -> {
            answerCommands(ctx, frames);
            resumeReadIfDrained(ctx);
        });
    }

    private void pauseReadIfBacklogged(ChannelHandlerContext ctx) {
        if (executor.queueDepth() >= SessionExecutor.QUEUE_LIMIT / 2
                && ctx.channel().config().isAutoRead()) {
            ctx.channel().config().setAutoRead(false);
        }
    }

    private void resumeReadIfDrained(ChannelHandlerContext ctx) {
        if (executor.queueDepth() > SessionExecutor.QUEUE_LIMIT / 8) {
            return;
        }
        ctx.executor().execute(() -> {
            if (!ctx.channel().config().isAutoRead()) {
                ctx.channel().config().setAutoRead(true);
            }
        });
    }

    private void answerCommands(ChannelHandlerContext ctx, List<UpstreamFrame> frames) {
        boolean canReply = session != null && session.isActive();
        for (UpstreamFrame f : frames) {
            byte[] plain = decodeA4(f);
            if (plain == null) {
                System.out.println("A4 上行 CRC 对不上，丢弃 " + f);
                continue;
            }
            if (ServerConfig.WIRE_TRACE) {
                System.out.println("[A4 RX " + SessionTag.of(ctx) + "] CMD " + f.packetId() + " " + dump(plain));
            }
            if (session != null) {
                session.noteUpstream(f.packetId());
            }
            // A4 握手：客户端对 NOTI 0 回 CMD 0，服务端再发 NOTI 1。CMD 0 不进 A5 的路由。
            if (f.packetId() == 0 && !channelInfoSent) {
                channelInfoSent = true;
                if (canReply) {
                    sendA4ChannelInfo(ctx);
                }
                continue;
            }
            if (canReply && ServerConfig.LOGIN_REPLY) {
                dispatch(f, plain);
            }
        }
        if (session != null && session.isActive()) {
            session.flush();
        }
    }

    private void dispatch(UpstreamFrame f, byte[] plain) {
        try {
            ROUTER.dispatch(session, f.packetId(), plain);
        } catch (RuntimeException e) {
            session.fail("处理 " + CmdName.describe(f.packetId()) + " 时出错：" + e, e);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (session != null) {
            GameSession dying = session;
            SessionExecutor dyingExecutor = executor;
            dyingExecutor.submit(() -> {
                cleanUpSession(dying);
                dyingExecutor.close();
            });
        }

        if (ServerConfig.EXIT_ON_DISCONNECT) {
            ctx.channel().parent().close();
        }
    }


    private void cleanUpSession(GameSession session) {
        try {
            session.party().onLeaveWorld("连接断开");
            com.dylan.game.NeighborService.announceDeparture(session, "连接断开");
            session.enchant().onLeaveUser();
            session.fatigue().save();
            session.inventory().save();
            session.itemLock().save();
            session.skills().save();
            // 连接没了：向外部工具注销这个角色（网页 GM 据此放行直写，见 PlayerPresence）。
            com.dylan.game.PlayerPresence.leave(session.store(), session.characNo());
            if (session.account() != null) {
                com.dylan.game.GmManager.leaveGmMode(session.account().mId());
            }
            session.gmMode(false);
        } finally {
            session.connectionCheck().stop();
            // 城镇互见：断线时让同区域的人把这个分身收掉（NeighborService.announceDeparture）
            try {
                com.dylan.game.NeighborService.announceDeparture(session, "断线");
            } catch (RuntimeException ignored) {
                // 通知别人失败不能挡住注销
            }
            com.dylan.game.SessionRegistry.leave(session);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("[断开] 未处理异常");
        cause.printStackTrace();
        ctx.close();
    }

    private static String dump(byte[] body) {
        return ServerConfig.WIRE_TRACE ? Hex.dumpBlock(body, ServerConfig.DUMP_LIMIT) : "";
    }

    /** XOR 解密，CRC 对含序号的明文校验（0 表示不校验），再去掉 2 字节序号交给 A5 原来的解析器。 */
    static byte[] decodeA4(UpstreamFrame f) {
        byte[] plain = A4Cipher.decryptUpstream(f.body());
        if (f.crc() != 0 && f.crc() != Crc32.of(plain)) {
            return null;
        }
        if (plain.length < A4Cipher.SEQUENCE_SIZE) {
            return plain;
        }
        return java.util.Arrays.copyOfRange(plain, A4Cipher.SEQUENCE_SIZE, plain.length);
    }

    static byte[] parseHex(String hex) {
        if (hex == null || hex.isBlank()) {
            return new byte[0];
        }
        String s = hex.replaceAll("[^0-9A-Fa-f]", "");
        if (s.length() % 2 != 0) {
            throw new IllegalArgumentException("十六进制串的位数是奇数：" + hex);
        }
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
