package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;
import com.dylan.protocol.io.PacketWriter;

import java.util.List;

public record Noti98RemoveMailBody(List<Long> mailIdx) {

    public static final int PACKET_ID = 98;

    public Noti98RemoveMailBody {
        mailIdx = List.copyOf(mailIdx == null ? List.of() : mailIdx);
        if (mailIdx.isEmpty()) {
            throw new PacketFormatException("NOTI " + PACKET_ID
                    + " 至少要带一封，没有要抹掉的邮件就别发这条包");
        }
    }

    public static Noti98RemoveMailBody of(long id) {
        return new Noti98RemoveMailBody(List.of(id));
    }

    public byte[] toBytes() {
        PacketWriter w = new PacketWriter(4 + mailIdx.size() * 4);
        w.u32(mailIdx.size());
        for (long id : mailIdx) {
            w.u32((int) id);
        }
        return w.toArray();
    }
}
