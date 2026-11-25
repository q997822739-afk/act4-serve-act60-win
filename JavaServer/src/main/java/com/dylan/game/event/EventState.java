package com.dylan.game.event;

public record EventState(int no, boolean running, int param1, int param2) {

    public EventState {
        if (no < 0 || no > EventNo.MAX_EVENT_NO) {
            throw new IllegalArgumentException(
                    "活动号 " + no + " 超出官方的 0.." + EventNo.MAX_EVENT_NO);
        }
    }

    public static EventState idle(EventDefinition def) {
        return new EventState(def.no(), false, def.defaultParam1(), def.defaultParam2());
    }

    public EventState started(int newParam1, int newParam2) {
        return new EventState(no, true, newParam1, newParam2);
    }

    public EventState ended(EventDefinition def) {
        return idle(def);
    }

    public int wordParam() {
        return (param1 & 0xFFFF) | ((param2 & 0xFFFF) << 16);
    }

    public int byteParam(int index) {
        if (index < 0 || index > 3) {
            throw new IllegalArgumentException("四个 u8 的下标只能是 0..3，给的是 " + index);
        }
        return (wordParam() >>> (index * 8)) & 0xFF;
    }
}
