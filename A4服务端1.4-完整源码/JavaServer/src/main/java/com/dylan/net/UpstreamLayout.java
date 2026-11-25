package com.dylan.net;

import java.util.List;

import com.dylan.protocol.crypto.KeyRecovery;

public record UpstreamLayout(String name, int headerSize, int idOffset, int idWidth,
                             int lengthOffset, int crcOffset,
                             int seqOffset, int seqWidth, String note) {

    public boolean hasLength() {
        return lengthOffset >= 0;
    }

    public byte[] crcPrefix(byte[] frame) {
        if (seqOffset < 0 || seqOffset + seqWidth > frame.length) {
            return KeyRecovery.NO_PREFIX;
        }
        byte[] out = new byte[seqWidth];
        System.arraycopy(frame, seqOffset, out, 0, seqWidth);
        return out;
    }

    public int readId(byte[] frame) {
        int v = frame[idOffset] & 0xFF;
        if (idWidth == 2) {
            v |= (frame[idOffset + 1] & 0xFF) << 8;
        }
        return v;
    }

    public int readU32(byte[] frame, int offset) {
        return (frame[offset] & 0xFF)
                | ((frame[offset + 1] & 0xFF) << 8)
                | ((frame[offset + 2] & 0xFF) << 16)
                | ((frame[offset + 3] & 0xFF) << 24);
    }

    public static final List<UpstreamLayout> CANDIDATES = List.of(
            new UpstreamLayout("ACT5_H7", 7, 1, 2, -1, 3, -1, 0,
                    "静态分析的直接读数：cat(1)|id(2)|crc(4)|body@7，无长度字段，CRC 只覆盖 body"),

            new UpstreamLayout("ACT5_H11_LEN", 11, 1, 2, 3, 7, -1, 0,
                    "若发送虚函数在外面补了长度：cat(1)|id(2)|len(4)|crc(4)|body@11"),

            new UpstreamLayout("ACT5_H13_LEN_SEQ", 13, 1, 2, 3, 7, 11, 2,
                    "把 60 版 12 字节头整体平移一格（id 变 u16）：cat|id(2)|len|crc|seq(2)|body@13"),

            new UpstreamLayout("ACT5_H15_SEQ32", 15, 1, 2, 3, 11, 7, 4,
                    "id u8→u16 且 seq u16→u32：cat|id(2)|len|seq(4)|crc|body@15，"
                            + "和实测的 crc@+11、body@+15 对得上"),

            new UpstreamLayout("LEGACY_H12", 12, 1, 1, 2, 6, 10, 2,
                    "60 版原样，作对照组。它若命中说明上行根本没改，只有下行改了")
    );
}
