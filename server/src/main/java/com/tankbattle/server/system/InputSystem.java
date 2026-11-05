package com.tankbattle.server.system;

import com.tankbattle.common.model.Direction;
import com.tankbattle.server.entity.TankIntent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 输入系统：P1 键盘意图。
 * 这里只存放线程安全的按键位图（EDT 写、游戏线程读）。
 */
public final class InputSystem {

    private static final int UP = 1;
    private static final int DOWN = 1 << 1;
    private static final int LEFT = 1 << 2;
    private static final int RIGHT = 1 << 3;
    private static final int FIRE = 1 << 4;

    private final AtomicInteger state = new AtomicInteger();

    /**
     * 方向键按下：互斥，仅保留当前方向。
     */
    public void press(Direction direction) {
        int mask = maskOf(direction);
        state.updateAndGet(bits -> (bits & ~(UP | DOWN | LEFT | RIGHT)) | mask);
    }

    public void release(Direction direction) {
        int mask = maskOf(direction);
        state.updateAndGet(bits -> bits & ~mask);
    }

    public void setFire(boolean pressed) {
        state.updateAndGet(bits -> pressed ? (bits | FIRE) : (bits & ~FIRE));
    }

    /**
     * 失焦时清零，避免按键粘滞。
     */
    public void clear() {
        state.set(0);
    }

    public TankIntent intent() {
        int bits = state.get();
        return new TankIntent(
                (bits & UP) != 0,
                (bits & DOWN) != 0,
                (bits & LEFT) != 0,
                (bits & RIGHT) != 0,
                (bits & FIRE) != 0);
    }

    private static int maskOf(Direction direction) {
        return switch (direction) {
            case UP -> UP;
            case DOWN -> DOWN;
            case LEFT -> LEFT;
            case RIGHT -> RIGHT;
        };
    }
}
