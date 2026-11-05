package com.tankbattle.common.level;

import com.tankbattle.common.config.GameConfig;

/**
 * 地图坐标 ↔ 线性下标 / 槽位键的纯函数集合。
 * <p>
 * 统一 {@code col = i % 20}、{@code row = i / 20}、{@code x = 23 + col*25}、{@code y = 23 + row*25}；
 * 所有符号分支共用该列号公式，避免坐标错列。
 */
public final class Cell {

    private Cell() {
    }

    /**
     * 线性下标 → 列。
     */
    public static int col(int index) {
        return index % GameConfig.MAP_COLS;
    }

    /**
     * 线性下标 → 行。
     */
    public static int row(int index) {
        return index / GameConfig.MAP_COLS;
    }

    /**
     * 线性下标 → 像素 x。
     */
    public static int x(int index) {
        return GameConfig.MAP_ORIGIN + col(index) * GameConfig.GRID;
    }

    /**
     * 线性下标 → 像素 y。
     */
    public static int y(int index) {
        return GameConfig.MAP_ORIGIN + row(index) * GameConfig.GRID;
    }

    /**
     * 列/行 → 线性下标。
     */
    public static int index(int col, int row) {
        return row * GameConfig.MAP_COLS + col;
    }

    /**
     * 像素坐标 → 状态键（行优先，0..399）。
     * <p>
     * 墙体消息 {@code w}/{@code s} 与 {@code SceneState} 的 Map 均以此为键，O(1) 更新。
     * 仅接受地图内坐标（23..498 像素）；越界输入会产生非法键且不报错，
     * 调用方须保证坐标来自合法地图消息，协议侧已有 20×20 范围约束。
     */
    public static int key(int x, int y) {
        int col = (x - GameConfig.MAP_ORIGIN) / GameConfig.GRID;
        int row = (y - GameConfig.MAP_ORIGIN) / GameConfig.GRID;
        return index(col, row);
    }

    /**
     * 键 → 像素 x（键即线性下标）。
     */
    public static int xOfKey(int key) {
        return x(key);
    }

    /**
     * 键 → 像素 y。
     */
    public static int yOfKey(int key) {
        return y(key);
    }
}
