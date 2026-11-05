package com.tankbattle.client.state.views;

/**
 * 墙体一帧快照。位图用 {@code int} 表示。
 * <p>
 * {@code key} 为线性下标 {@code Cell.key(x,y)}；坐标由 key 反算，保证 w/s 更新 O(1) 且不误删同格其他实体。
 */
public sealed interface WallView {

    int key();

    /**
     * 位 i 为 1 表示该子块已毁。
     */
    int destroyedBits();

    /**
     * 砖墙：4×4 共 16 子块。
     */
    record Brick(int key, int destroyedBits) implements WallView {
    }

    /**
     * 钢墙：2×2 共 4 象限。
     */
    record Steel(int key, int destroyedBits) implements WallView {
    }

    static Brick brick(int key, int destroyedBits) {
        return new Brick(key, destroyedBits);
    }

    static Steel steel(int key, int destroyedBits) {
        return new Steel(key, destroyedBits);
    }
}
