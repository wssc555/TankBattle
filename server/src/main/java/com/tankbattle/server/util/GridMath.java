package com.tankbattle.server.util;

/**
 * 虚拟网格吸附算法。
 * <p>
 * 纯函数，便于单测：余数 &lt;7 吸附 0、&gt;18 吸附 25、其余置 13；{@code v<17 || v>492} 强制 13。
 */
public final class GridMath {

    private GridMath() {
    }

    public static final int CELL = 25;
    public static final int ORIGIN = 10;
    public static final int LOW = 7;
    public static final int HIGH = 18;
    public static final int MID = 13;
    public static final int MIN_V = 17;
    public static final int MAX_V = 492;

    public static int snap(int v) {
        int quotient = (v - ORIGIN) / CELL;
        int remainder = (v - ORIGIN) % CELL;
        if (remainder < LOW) {
            remainder = 0;
        }
        if (remainder > HIGH) {
            remainder = CELL;
        }
        if ((remainder < 19 && remainder > 6) || v < MIN_V || v > MAX_V) {
            remainder = MID;
        }
        return quotient * CELL + remainder + ORIGIN;
    }
}
