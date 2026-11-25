package com.dylan.game;

public final class AntiBotState {
    private int antiBotSerial;
    private int dprotoSerial;
    private long antiBotCount;
    private long dprotoCount;
    private int antiBotBreaks;
    private int dprotoBreaks;

    public int antiBotSerial() {
        return antiBotSerial;
    }

    public int dprotoSerial() {
        return dprotoSerial;
    }

    public void recordAntiBot(int serial, boolean broken) {
        antiBotCount++;
        if (broken) {
            antiBotBreaks++;
        }
        if (serial >= 0) {
            antiBotSerial = serial;
        }
    }

    public void recordDproto(int serial, boolean broken) {
        dprotoCount++;
        if (broken) {
            dprotoBreaks++;
        }
        if (serial >= 0) {
            dprotoSerial = serial;
        }
    }

    @Override
    public String toString() {
        return "反外挂[280×" + antiBotCount + "（断 " + antiBotBreaks + "）"
                + " 282×" + dprotoCount + "（断 " + dprotoBreaks + "）]";
    }
}
