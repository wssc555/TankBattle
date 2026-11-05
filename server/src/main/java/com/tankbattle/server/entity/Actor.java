package com.tankbattle.server.entity;

import com.tankbattle.server.core.GameContext;

import java.awt.Rectangle;

/**
 * 实体根接口，<b>不含 draw(Graphics)</b>。
 * 渲染一律由 {@code Snapshot} 派生，模型层零渲染代码。
 */
public sealed interface Actor
        permits Tank, Wall, Bullet, River, Grass, Base, Bomb, PowerUp {

    /**
     * 当前碰撞盒（整格 25×25 或子弹的细长矩形）。
     */
    Rectangle border();

    /**
     * 是否已从世界移除。
     */
    boolean destroyed();

    void markDestroyed();

    /**
     * 每帧推进；暂停由主循环统一短路，实体内部不重复判断。
     */
    void tick(GameContext ctx);
}
