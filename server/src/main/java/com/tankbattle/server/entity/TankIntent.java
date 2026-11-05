package com.tankbattle.server.entity;

/**
 * 坦克意图：P1 由键盘产生，P2 由客户端 {@code m} 指令产生。
 */
public record TankIntent(boolean up, boolean down, boolean left, boolean right, boolean fire) {

    public static final TankIntent NONE = new TankIntent(false, false, false, false, false);

    public boolean anyMove() {
        return up || down || left || right;
    }
}
