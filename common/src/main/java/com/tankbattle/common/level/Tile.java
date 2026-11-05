package com.tankbattle.common.level;

/**
 * 地形枚举（400 格，每格一个 Tile）。
 * <p>
 * 墙体朝向语义（与服务端 {@code BrickWall#orientation} 一致）：
 * 0 上半、1 下半、2 左半、3 右半；对应"另一半已毁"的初始位图。
 */
public enum Tile {

    EMPTY,
    BRICK_FULL,
    BRICK_0,
    BRICK_1,
    BRICK_2,
    BRICK_3,
    STEEL_FULL,
    STEEL_0,
    STEEL_1,
    STEEL_2,
    STEEL_3,
    GRASS,
    RIVER,
    BASE,
    BASE_GUARD_LEFT,
    BASE_GUARD_RIGHT,
    BASE_GUARD_TOP,
    BASE_GUARD_BOTTOM;

    public boolean isBrick() {
        return this == BRICK_FULL || this == BRICK_0 || this == BRICK_1
                || this == BRICK_2 || this == BRICK_3;
    }

    public boolean isSteel() {
        return this == STEEL_FULL || this == STEEL_0 || this == STEEL_1
                || this == STEEL_2 || this == STEEL_3;
    }

    /**
     * 砖墙初始已毁位图（16 bit）；非砖墙返回 0。
     */
    public int brickBits() {
        return switch (this) {
            case BRICK_0 -> 0xFF00;   // 预毁底两行
            case BRICK_1 -> 0x00FF;   // 预毁顶两行
            case BRICK_2 -> 0xCCCC;   // 预毁右两列
            case BRICK_3 -> 0x3333;   // 预毁左两列
            default -> 0;
        };
    }

    /**
     * 钢墙初始已毁位图（4 bit）；非钢墙返回 0。
     */
    public int steelBits() {
        return switch (this) {
            case STEEL_0 -> 0xC;      // 预毁下两象限
            case STEEL_1 -> 0x3;      // 预毁上两象限
            case STEEL_2 -> 0xA;      // 预毁右两象限
            case STEEL_3 -> 0x5;      // 预毁左两象限
            default -> 0;
        };
    }
}
