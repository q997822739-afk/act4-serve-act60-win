package com.dylan.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.dylan.protocol.crypto.KeyParams;
import com.dylan.protocol.crypto.KeyRecovery;
import com.dylan.protocol.io.PacketWriter;

public final class UpstreamFormatProbe {

    private UpstreamFormatProbe() {
    }

    public record Hit(UpstreamLayout layout, int packetId, int bodyLength,
                      KeyParams key, byte[] plaintext) {

        @Override
        public String toString() {
            return String.format("%s  id=%d  body=%dB  %s",
                    layout.name(), packetId, bodyLength, key);
        }
    }

    public static List<Hit> probe(byte[] frame) {
        List<Hit> hits = new ArrayList<>();
        for (UpstreamLayout layout : UpstreamLayout.CANDIDATES) {
            probeOne(frame, layout).ifPresent(hits::add);
        }
        return hits;
    }

    private static Optional<Hit> probeOne(byte[] frame, UpstreamLayout layout) {
        int header = layout.headerSize();
        if (frame.length <= header) {
            return Optional.empty();
        }
        if (layout.crcOffset() + 4 > frame.length) {
            return Optional.empty();
        }

        int bodyLength;
        if (layout.hasLength()) {
            if (layout.lengthOffset() + 4 > frame.length) {
                return Optional.empty();
            }
            int total = layout.readU32(frame, layout.lengthOffset());
            if (total != frame.length) {
                return Optional.empty();
            }
            bodyLength = total - header;
        } else {
            bodyLength = frame.length - header;
        }
        if (bodyLength <= 0 || bodyLength > PacketWriter.MAX_BODY_LENGTH) {
            return Optional.empty();
        }

        int crc = layout.readU32(frame, layout.crcOffset());
        byte[] prefix = layout.crcPrefix(frame);

        return KeyRecovery.recover(frame, header, bodyLength, prefix, crc)
                .map(key -> new Hit(layout, layout.readId(frame), bodyLength, key,
                        com.dylan.protocol.crypto.BodyCipher
                                .decryptToCopy(frame, header, bodyLength, key)));
    }

    public static String describe(byte[] frame, List<Hit> hits) {
        StringBuilder sb = new StringBuilder();
        sb.append("上行格式判定：").append(frame.length).append(" 字节，")
                .append(hits.size()).append(" 个布局命中");
        if (hits.isEmpty()) {
            sb.append("\n  没有任何候选布局能让 CRC 对上。可能的原因："
                    + "\n    a) 这一批不是一个完整的包（两个包粘在一起，或者只到了半个）"
                    + "\n    b) 真实布局不在候选表里 —— 把 dump 出来的字节贴回去，"
                    + "往 UpstreamLayout 的候选表里加一条"
                    + "\n    c) 上行走了 DProto（id 260）那条独立密码通道");
            return sb.toString();
        }
        for (Hit h : hits) {
            sb.append("\n  ✔ ").append(h)
                    .append("\n      ").append(h.layout().note())
                    .append("\n      明文前 64 字节：").append(Hex.dumpInline(h.plaintext(), 64));
        }
        if (hits.size() > 1) {
            sb.append("\n  ⚠ 多个布局同时命中，单凭这一个包分不出来；"
                    + "再抓几个不同长度的包，只有真布局会一直命中");
        }
        return sb.toString();
    }
}
