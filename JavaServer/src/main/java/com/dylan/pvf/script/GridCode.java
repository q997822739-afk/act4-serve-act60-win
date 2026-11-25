package com.dylan.pvf.script;

public final class GridCode {
    private GridCode() {
    }

    public static final int CODES = 16;

    public static final int DIR_EAST = 1;
    public static final int DIR_NORTH = 2;
    public static final int DIR_WEST = 4;
    public static final int DIR_SOUTH = 8;

    public static final int ALL_DIRECTIONS = 15;

    public static final int EMPTY = 0;

    public static int parse(String code) {
        if (code == null || code.length() < 1) {
            return -1;
        }
        char c = Character.toUpperCase(code.charAt(0));
        if (c < 'A' || c >= 'A' + CODES) {
            return -1;
        }
        return c - 'A';
    }

    public static String format(int gridCode) {
        if (gridCode < 0 || gridCode >= CODES) {
            return "??";
        }
        char c = (char) ('A' + gridCode);
        return "" + c + c;
    }

    public static int invadeDirection(int fromX, int fromY, int toX, int toY) {
        int direction = 0;
        if (fromX == toX) {
            if (fromY > toY) {
                direction = DIR_SOUTH;
            }
            if (fromY < toY) {
                direction = DIR_NORTH;
            }
        }
        if (fromY == toY) {
            if (fromX < toX) {
                direction = DIR_WEST;
            }
            if (fromX > toX) {
                return DIR_EAST;
            }
        }
        return direction;
    }
}
