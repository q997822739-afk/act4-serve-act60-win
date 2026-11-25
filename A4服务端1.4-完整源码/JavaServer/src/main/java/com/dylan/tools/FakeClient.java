package com.dylan.tools;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import com.dylan.net.Hex;
import com.dylan.protocol.NotiHeader;
import com.dylan.protocol.crypto.BodyCipher;
import com.dylan.protocol.crypto.Crc32;
import com.dylan.protocol.crypto.KeyParams;

public final class FakeClient {

    private FakeClient() {
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 10011;
        int cmdId = args.length > 2 ? Integer.parseInt(args[2]) : 1;

        System.out.printf("连接 %s:%d …%n", host, port);
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(host, port), 5000);
            sock.setTcpNoDelay(true);
            sock.setSoTimeout(6000);
            System.out.println("已连接，等服务端的开场白（最多 6 秒）…");

            InputStream in = sock.getInputStream();
            OutputStream out = sock.getOutputStream();

            byte[] buf = new byte[4096];
            int n;
            try {
                n = in.read(buf);
            } catch (java.net.SocketTimeoutException e) {
                n = -1;
                System.out.println("！没等到开场白（超时）");
            }
            if (n > 0) {
                byte[] greet = new byte[n];
                System.arraycopy(buf, 0, greet, 0, n);
                dumpDownstream(greet);
            }

            byte[] body = ("hello-from-fake-client-" + System.nanoTime())
                    .getBytes(StandardCharsets.US_ASCII);
            KeyParams key = new KeyParams(0x5C, 3);
            byte[] frame = buildAct5H7(cmdId, body, key);
            System.out.printf("%n→ 发一个上行包：id=%d，包体 %d 字节，%s%n  线路字节：%s%n",
                    cmdId, body.length, key, Hex.dumpInline(frame, 128));
            out.write(frame);
            out.flush();

            Thread.sleep(1200);
            System.out.println("完成。去看 logs\\s1a5.log 里的『上行格式判定』那几行。");
        }
    }

    private static void dumpDownstream(byte[] wire) {
        System.out.printf("← 收到 %d 字节%n  %s%n", wire.length, Hex.dumpInline(wire, 128));
        if (wire.length < NotiHeader.SIZE) {
            System.out.println("  短于 7 字节，不像一个完整的下行包");
            return;
        }
        int cat = wire[0] & 0xFF;
        int id = (wire[1] & 0xFF) | ((wire[2] & 0xFF) << 8);
        int total = (wire[3] & 0xFF) | ((wire[4] & 0xFF) << 8)
                | ((wire[5] & 0xFF) << 16) | ((wire[6] & 0xFF) << 24);
        System.out.printf("  解析：category=%d  packetId=%d  totalLength=%d（实收 %d）%n",
                cat, id, total, wire.length);
        if (total != wire.length) {
            System.out.println("  ！长度字段和实收对不上");
        }
        int bodyLen = Math.max(0, Math.min(total, wire.length) - NotiHeader.SIZE);
        if (bodyLen > 0) {
            byte[] plain = BodyCipher.decryptToCopy(wire, NotiHeader.SIZE, bodyLen,
                    BodyCipher.DOWNSTREAM);
            System.out.printf("  解密后的包体：%s%n", Hex.dumpInline(plain, 128));
        } else {
            System.out.println("  空体（只验包头与分发）");
        }
    }

    private static byte[] buildAct5H7(int packetId, byte[] plainBody, KeyParams key) {
        byte[] frame = new byte[7 + plainBody.length];
        frame[0] = 0x01;
        frame[1] = (byte) packetId;
        frame[2] = (byte) (packetId >>> 8);

        int crc = Crc32.of(plainBody);
        frame[3] = (byte) crc;
        frame[4] = (byte) (crc >>> 8);
        frame[5] = (byte) (crc >>> 16);
        frame[6] = (byte) (crc >>> 24);

        byte[] cipher = plainBody.clone();
        BodyCipher.encrypt(cipher, 0, cipher.length, key);
        System.arraycopy(cipher, 0, frame, 7, cipher.length);
        return frame;
    }
}
