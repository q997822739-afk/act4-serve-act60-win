package com.dylan.net;

import io.netty.channel.ChannelHandlerContext;

/**
 * 包日志里区分「哪一条连接」用的短标签。
 *
 * <p>2026-10-28 排查决斗场频道黑屏时加的：一个进程同时服务 7002/7003/7004 三个频道，原来
 * {@code [A4 RX] CMD 4} / {@code [A4 TX] NOTI 23} 混在一起，看不出谁是谁。现在每行带上客户端端口，
 * 配合登录时打的那行 {@code [会话]} 就能跟着一条连接看完整个流程。
 */
public final class SessionTag {

    private SessionTag() {
    }

    /** 对端端口（游戏连接一条一个），拿不到就写 "?"。 */
    public static String of(ChannelHandlerContext ctx) {
        if (ctx == null || ctx.channel() == null) {
            return "?";
        }
        java.net.SocketAddress addr = ctx.channel().remoteAddress();
        if (addr instanceof java.net.InetSocketAddress inet) {
            return Integer.toString(inet.getPort());
        }
        return "?";
    }
}
