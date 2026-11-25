package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketFormatException;

public record QuestTrigger(int packed) {
    public static final int CHANNELS = 3;

    public static final int CHANNEL_BITS = 9;

    public static final int CHANNEL_MAX = (1 << CHANNEL_BITS) - 1;

    private static final int CHANNEL_MASK = CHANNEL_MAX;

    public static final QuestTrigger SINGLE = new QuestTrigger(1);

    public QuestTrigger {
        if (packed < 0) {
            throw new PacketFormatException("trigger 是 u32，不能是负数：" + packed);
        }
    }

    public static QuestTrigger of(int... remaining) {
        int packed = 0;
        for (int i = 0; i < remaining.length && i < CHANNELS; i++) {
            packed |= clamp(remaining[i]) << (i * CHANNEL_BITS);
        }
        return new QuestTrigger(packed);
    }

    public int channel(int channel) {
        if (channel < 0 || channel >= CHANNELS) {
            return 0;
        }
        return (packed >>> (channel * CHANNEL_BITS)) & CHANNEL_MASK;
    }

    public QuestTrigger withChannel(int channel, int remaining) {
        if (channel < 0 || channel >= CHANNELS) {
            return this;
        }
        int shift = channel * CHANNEL_BITS;
        return new QuestTrigger((packed & ~(CHANNEL_MASK << shift)) | (clamp(remaining) << shift));
    }

    public QuestTrigger decrement(int channel) {
        int remaining = channel(channel);
        return remaining > 0 ? withChannel(channel, remaining - 1) : this;
    }

    public QuestTrigger decrementWhole() {
        return packed > 0 ? new QuestTrigger(packed - 1) : this;
    }

    public QuestTrigger incrementWhole() {
        return new QuestTrigger(packed + 1);
    }

    @Override
    public String toString() {
        return packed + "(" + channel(0) + "," + channel(1) + "," + channel(2) + ")";
    }

    private static int clamp(int value) {
        if (value <= 0) {
            return 0;
        }
        return Math.min(value, CHANNEL_MAX);
    }
}
