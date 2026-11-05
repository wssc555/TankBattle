package com.tankbattle.server.entity;

import com.tankbattle.server.core.GameContext;

import java.awt.Rectangle;

/**
 * 草地。纯装饰：无碰撞、不进世界实体表，单独存放于 {@code GameContext.decor()}，
 * 渲染时最后绘制以覆盖坦克。
 */
public final class Grass implements Actor {

    private static final Rectangle EMPTY = new Rectangle(0, 0, 0, 0);

    private final Vec pos;
    private boolean destroyed;

    public Grass(int x, int y) {
        this.pos = new Vec(x, y);
    }

    public Vec pos() {
        return pos;
    }

    @Override
    public Rectangle border() {
        return EMPTY;
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
        // 纯装饰
    }
}
