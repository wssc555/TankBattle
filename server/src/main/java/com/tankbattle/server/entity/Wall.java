package com.tankbattle.server.entity;

import java.awt.Rectangle;

/**
 * 可破坏墙体（砖墙 / 钢墙）的公共契约，统一以 {@link #isDestroyed()} 表达"整墙已毁"。
 */
public sealed interface Wall extends Actor permits BrickWall, SteelWall {

    /**
     * 4 象限碰撞盒，{@code null} 表示该象限已完全毁坏。
     */
    Rectangle[] quadrants();

    /**
     * 形状位图：砖墙 16 bit、钢墙 4 bit，<b>1 = 已毁</b>。
     */
    int shapeBits();

    /**
     * 位宽（砖墙 16、钢墙 4）。
     */
    int bitCount();

    /**
     * 整墙所有子块均已毁。
     */
    boolean isDestroyed();

    /**
     * 墙体纹理索引。
     */
    int textureIndex();
}
