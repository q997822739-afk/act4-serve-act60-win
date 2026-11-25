package com.dylan.protocol.body;

import com.dylan.protocol.io.PacketReader;

import java.util.Arrays;

public record Cmd169DeathTowerStageRequest(int command, int[] memberValues) {
    public static final int PACKET_ID = 169;

    public static final int CMD_START_STAGE = 1;

    public static final int CMD_FINISH_STAGE = 2;

    public static final int MEMBER_VALUES = 4;

    public Cmd169DeathTowerStageRequest {
        memberValues = memberValues == null ? new int[0] : memberValues.clone();
    }

    public static Cmd169DeathTowerStageRequest parse(byte[] body) {
        PacketReader r = new PacketReader(body);
        int command = r.u8();
        if (command != CMD_FINISH_STAGE || r.remaining() < MEMBER_VALUES * 2) {
            return new Cmd169DeathTowerStageRequest(command, new int[0]);
        }
        int[] values = new int[MEMBER_VALUES];
        for (int i = 0; i < MEMBER_VALUES; i++) {
            values[i] = r.u16();
        }
        return new Cmd169DeathTowerStageRequest(command, values);
    }

    @Override
    public int[] memberValues() {
        return memberValues.clone();
    }

    public String describe() {
        return switch (command) {
            case CMD_START_STAGE -> "这一层开打了";
            case CMD_FINISH_STAGE -> "这一层清了";
            default -> "不认识的子命令";
        };
    }

    @Override
    public String toString() {
        return "CMD 169 " + describe() + "（" + command + "）"
                + (memberValues.length == 0 ? "" : " " + Arrays.toString(memberValues));
    }
}
