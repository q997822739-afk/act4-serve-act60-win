package com.dylan.net;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

import java.net.InetSocketAddress;

/**
 * A4 客户端的 NAT 探测服务器（客户端读包 0085FDE6，发包 0085FB00）。
 *
 * <p>NOTI 1 里的 host + 两个端口就是这里：客户端从自己的 P2P UDP 口往它发 1 字节，
 * 等回包 {@code u8 2, u32 看到的源 ip, u16 看到的源端口}（都按网络序，客户端用 ntohl/htons 取）。
 * 拿到之后写进 CMD 2 的"外网端点"，服务端再放进 NOTI 11 发给队员。
 * 没有这个服务时探测超时，CMD 2 只能报内网地址，公网上的两人永远打不通（本机双开都是 127.0.0.1，测不出来）。
 *
 * <p>首字节 0（状态 4 的保活）不回。首字节 5 是状态 3「请从另一个端口回我」（判全锥形）：
 * 客户端 0085FFBC 超时 → 0085F4C0(4)，CMD 2 报 NAT 类型 4，组队时直接放弃 UDP、走 7001 TCP 中转
 * （2026-10-28 外网两家都是 4，UdpPairRelay 一个 UDP 包都没收到，TCP 中转 5 秒后退队）。
 * 客户端不校验回包源端口（0085FDE6 recvfrom 后只看首字节 2），所以同端口照常回，
 * 客户端判类型 2，就会往 NOTI 11 里的服务器中转端口发 UDP；中转只认源 IP，NAT 类型实际是什么都无所谓。
 */
final class UdpProbeServer {

    private UdpProbeServer() {
    }

    static Channel bind(EventLoopGroup group, int port) throws InterruptedException {
        return new Bootstrap().group(group).channel(NioDatagramChannel.class)
                .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket p) {
                        ByteBuf in = p.content();
                        if (!in.isReadable()) {
                            return;
                        }
                        int kind = in.getUnsignedByte(in.readerIndex());
                        if (kind == 0) {
                            return;
                        }
                        InetSocketAddress from = p.sender();
                        byte[] ip = from.getAddress().getAddress();
                        if (ip.length != 4) {
                            return;
                        }
                        ByteBuf out = ctx.alloc().buffer(7);
                        out.writeByte(2).writeBytes(ip).writeShort(from.getPort());
                        ctx.writeAndFlush(new DatagramPacket(out, from));
                    }
                })
                .bind(port).sync().channel();
    }
}
