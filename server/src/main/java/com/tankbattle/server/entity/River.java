package com.tankbattle.server.entity;

import com.tankbattle.common.config.GameConfig;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.core.GameContext;

import java.awt.Rectangle;

/**
 * 河流。25×25 阻挡实体：坦克不可过、子弹可穿。
 */
public final class River implements Actor {

    private final Vec pos;
    private final Rectangle border;
    private boolean destroyed;

    public River(int x, int y) {
        this.pos = new Vec(x, y);
        this.border = new Rectangle(x - GameConfig.TANK_HALF, y - GameConfig.TANK_HALF,
                ServerConfig.TANK_BOX, ServerConfig.TANK_BOX);
    }

    public Vec pos() {
        return pos;
    }

    public int textureIndex() {
        return GameConfig.TEX_RIVER;
    }

    @Override
    public Rectangle border() {
        return border;
    }

    @Override
    public boolean destroyed() {
        return destroyed;
    }

    @Override
    public void markDestroyed() {
        destroyed = true;
    }

    @Override
    public void tick(GameContext ctx) {
        // 静态实体
    }
}
