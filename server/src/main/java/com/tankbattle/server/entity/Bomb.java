package com.tankbattle.server.entity;

import com.tankbattle.common.protocol.S2c;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.core.GameContext;

import java.awt.Rectangle;

/**
 * 两级爆炸特效。无碰撞，仅作为快照 effects 与协议 {@code o} 指令的来源。
 * <p>
 * 尺寸语义复用 {@link S2c.BombSize}（双端一致）；帧数与抖动属于服务端模拟参数，
 * 由 {@link ServerConfig} 提供。支持对象池复用。
 */
public final class Bomb implements Actor {

    private static final Rectangle EMPTY = new Rectangle(0, 0, 0, 0);

    private S2c.BombSize size = S2c.BombSize.SMALL;
    private Vec pos = new Vec(0, 0);
    private int animationTime;
    private boolean destroyed;

    public Bomb(Vec pos, S2c.BombSize size) {
        reset(size, pos);
    }

    /**
     * 对象池出池时重置状态。
     */
    public void reset(S2c.BombSize size, Vec pos) {
        this.size = size;
        this.pos = pos;
        this.animationTime = framesOf(size);
        this.destroyed = false;
    }

    /**
     * 动画时长（帧）：big 6 / small 4。
     */
    public static int framesOf(S2c.BombSize size) {
        return size == S2c.BombSize.BIG
                ? ServerConfig.BOMB_BIG_FRAMES
                : ServerConfig.BOMB_SMALL_FRAMES;
    }

    /**
     * 每帧位置抖动幅度：big 8 / small 4。
     */
    public static int jitterOf(S2c.BombSize size) {
        return size == S2c.BombSize.BIG
                ? ServerConfig.BOMB_BIG_JITTER
                : ServerConfig.BOMB_SMALL_JITTER;
    }

    public S2c.BombSize size() {
        return size;
    }

    public Vec pos() {
        return pos;
    }

    public int animationTime() {
        return animationTime;
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
        // 计数语义：每帧先递减、减到负数才销毁——big 可见 6 帧、small 4 帧。
        // 须保持 >0，否则爆炸动画会少一帧。
        animationTime--;
        if (animationTime < 0) {
            markDestroyed();
            return;
        }
        int jitter = jitterOf(size);
        int dx = ctx.random().nextInt(jitter) - ctx.random().nextInt(jitter);
        int dy = ctx.random().nextInt(jitter) - ctx.random().nextInt(jitter);
        pos = pos.plus(dx, dy);
    }
}
