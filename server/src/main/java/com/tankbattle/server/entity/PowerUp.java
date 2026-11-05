package com.tankbattle.server.entity;

import com.tankbattle.common.config.GameConfig;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.core.GameContext;

import java.awt.Rectangle;

/**
 * 道具。生成概率与存活时长由 {@code PowerUpSystem} 决定，本类只负责倒计时与拾取判定。
 */
public final class PowerUp implements Actor {

    private final Vec pos;
    private final PowerUpType type;
    private final Rectangle border;
    private int displayTime;
    private boolean destroyed;

    public PowerUp(PowerUpType type, Vec pos, int displayTime) {
        this.type = type;
        this.pos = pos;
        this.displayTime = displayTime;
        this.border = new Rectangle(pos.x() - GameConfig.TANK_HALF, pos.y() - GameConfig.TANK_HALF,
                ServerConfig.TANK_BOX, ServerConfig.TANK_BOX);
    }

    public PowerUpType type() {
        return type;
    }

    public Vec pos() {
        return pos;
    }

    public int displayTime() {
        return displayTime;
    }

    public int textureIndex() {
        return ServerConfig.TEX_POWERUP + type.function();
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
        displayTime--;
        if (displayTime <= 0) {
            markDestroyed();
        }
    }
}
