package com.tankbattle.common.model;

/**
 * 四方向枚举，编码与协议一致：0 上、1 下、2 左、3 右。
 */
public enum Direction {

    UP(0),
    DOWN(1),
    LEFT(2),
    RIGHT(3);

    private final int code;

    Direction(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    /**
     * 竖直方向（上/下）。
     */
    public boolean vertical() {
        return this == UP || this == DOWN;
    }

    /**
     * 纵向为 0、横向为 1（用于转向跨轴判定）。
     */
    public int axis() {
        return code / 2;
    }

    /**
     * x 方向增量：左 -1 / 右 +1 / 上下 0。
     */
    public int dx() {
        return switch (this) {
            case UP, DOWN -> 0;
            case LEFT -> -1;
            case RIGHT -> 1;
        };
    }

    /**
     * y 方向增量：上 -1 / 下 +1 / 左右 0。
     */
    public int dy() {
        return switch (this) {
            case UP -> -1;
            case DOWN -> 1;
            case LEFT, RIGHT -> 0;
        };
    }

    public Direction reverse() {
        return switch (this) {
            case UP -> DOWN;
            case DOWN -> UP;
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
        };
    }

    /**
     * 协议方向码 → 枚举。
     *
     * @throws IllegalArgumentException 码值不在 0..3
     */
    public static Direction fromCode(int code) {
        return switch (code) {
            case 0 -> UP;
            case 1 -> DOWN;
            case 2 -> LEFT;
            case 3 -> RIGHT;
            default -> throw new IllegalArgumentException("非法方向码: " + code);
        };
    }
}
