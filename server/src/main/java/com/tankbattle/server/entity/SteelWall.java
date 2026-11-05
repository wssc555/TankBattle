package com.tankbattle.server.entity;

import com.tankbattle.common.config.GameConfig;
import com.tankbattle.common.model.Direction;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.core.GameContext;

import java.awt.Rectangle;

/**
 * 钢墙。4 象限位图（1 = 已毁），普通弹无伤、超级弹逐象限摧毁。
 */
public final class SteelWall implements Wall {

    /**
     * 完整钢墙。
     */
    public static final int FULL_BITS = 0;
    public static final int BITS_TOP_HALF = 0b1100;
    public static final int BITS_BOTTOM_HALF = 0b0011;
    public static final int BITS_LEFT_HALF = 0b1010;
    public static final int BITS_RIGHT_HALF = 0b0101;

    private static final int BITS = 4;

    private final Vec pos;
    private final Rectangle generalBorder;
    private final Rectangle[] quadrants = new Rectangle[4];

    private int shapeBits;
    private boolean bulletStopped;
    private boolean removed;

    public SteelWall(int x, int y, int initialBits) {
        this.pos = new Vec(x, y);
        this.generalBorder = new Rectangle(x - GameConfig.TANK_HALF, y - GameConfig.TANK_HALF,
                ServerConfig.TANK_BOX, ServerConfig.TANK_BOX);
        this.shapeBits = initialBits & 0xF;
        for (int q = 0; q < 4; q++) {
            quadrants[q] = bit(q) ? null : quadrant(q);
        }
    }

    /**
     * 朝向 0/1/2/3 → 对应的初始位图（0 上半、1 下半、2 左半、3 右半）。
     */
    public static int bitsForOrientation(int orientation) {
        return switch (orientation) {
            case 0 -> BITS_TOP_HALF;
            case 1 -> BITS_BOTTOM_HALF;
            case 2 -> BITS_LEFT_HALF;
            case 3 -> BITS_RIGHT_HALF;
            default -> FULL_BITS;
        };
    }

    private boolean bit(int index) {
        return (shapeBits >>> index & 1) != 0;
    }

    private Rectangle quadrant(int index) {
        int qx = pos.x() + ((index & 1) == 0 ? -11 : 1);
        int qy = pos.y() + ((index & 2) == 0 ? -11 : 1);
        return new Rectangle(qx, qy, ServerConfig.QUADRANT_SIZE, ServerConfig.QUADRANT_SIZE);
    }

    public Vec pos() {
        return pos;
    }

    @Override
    public int textureIndex() {
        return GameConfig.TEX_STEEL;
    }

    @Override
    public int bitCount() {
        return BITS;
    }

    @Override
    public int shapeBits() {
        return shapeBits;
    }

    @Override
    public Rectangle[] quadrants() {
        return quadrants;
    }

    @Override
    public Rectangle border() {
        return generalBorder;
    }

    @Override
    public boolean isDestroyed() {
        return shapeBits == 0xF;
    }

    @Override
    public boolean destroyed() {
        return removed;
    }

    @Override
    public void markDestroyed() {
        removed = true;
    }

    @Override
    public void tick(GameContext ctx) {
        // 静态实体
    }

    /**
     * @return 子弹是否应消失
     */
    public boolean damage(Rectangle bullet, Direction dir, int power) {
        bulletStopped = false;
        if (power == 2) {
            for (int i = 0; i < 4; i++) {
                if (quadrants[i] != null && bullet.intersects(quadrants[i])) {
                    quadrants[i] = null;
                    shapeBits |= 1 << i;
                    bulletStopped = true;
                }
            }
        } else if (power == 1) {
            for (int i = 0; i < 4; i++) {
                if (quadrants[i] != null && bullet.intersects(quadrants[i])) {
                    bulletStopped = true;
                }
            }
        }
        return bulletStopped;
    }
}
