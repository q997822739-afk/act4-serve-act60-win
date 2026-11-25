package com.dylan.game;

import java.util.Optional;

public enum ClearRank {
    F(0),
    E(1),
    D(2),
    C(3),
    B(4),
    A(5),
    S(6),
    SS(7),
    SSS(8);

    private static final ClearRank[] BY_VALUE = values();

    private final int value;

    ClearRank(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public boolean atLeast(ClearRank other) {
        return value >= other.value;
    }

    public static Optional<ClearRank> parse(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        for (ClearRank rank : BY_VALUE) {
            if (rank.name().equalsIgnoreCase(name.trim())) {
                return Optional.of(rank);
            }
        }
        return Optional.empty();
    }
}
