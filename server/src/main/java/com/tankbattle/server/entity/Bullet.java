package com.tankbattle.server.entity;

import com.tankbattle.common.model.Direction;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.core.GameContext;
import com.tankbattle.server.system.CombatSystem;

import java.awt.Rectangle;

/**
 * 子弹。命中规则由 {@link CombatSystem} 结算，本类只负责推进与出界判定。
 * <p>
 * 碰撞盒尺寸与偏移：纵向 5×13、偏移 (-2,-5)；横向 13×5、偏移 (-5,-2)。
 */
public final class Bullet implements Actor {

    private final Direction direction;
    private final int speed;
    private final int power;
    private final Tank owner;
    private final Rectangle border;

    private Vec pos;
    private boolean destroyed;

    public Bullet(Vec pos, Direction direction, int speed, int power, Tank owner) {
        this.pos = pos;
        this.direction = direction;
        this.speed = speed;
        this.power = power;
        this.owner = owner;
        this.border = direction.vertical()
                ? new Rectangle(pos.x() - 2, pos.y() - 5,
                ServerConfig.BULLET_BORDER_V_W, ServerConfig.BULLET_BORDER_V_H)
                : new Rectangle(pos.x() - 5, pos.y() - 2,
                ServerConfig.BULLET_BORDER_H_W, ServerConfig.BULLET_BORDER_H_H);
    }

    public Vec pos() {
        return pos;
    }

    public Direction direction() {
        return direction;
    }

    public int speed() {
        return speed;
    }

    /**
     * 威力 1 = 普通；2 = 超级子弹。
     */
    public int power() {
        return power;
    }

    public Tank owner() {
        return owner;
    }

    public boolean fromPlayer() {
        return owner instanceof PlayerTank;
    }

    public boolean fromEnemy() {
        return owner instanceof EnemyTank;
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
        CombatSystem.resolve(ctx, this);
    }

    /**
     * 沿朝向推进 {@link #speed} 像素；碰撞盒按同样位移平移。
     */
    public void advance() {
        int dx = direction.dx() * speed;
        int dy = direction.dy() * speed;
        pos = pos.plus(dx, dy);
        border.x += dx;
        border.y += dy;
    }

    /**
     * 出界判定：碰撞盒与子弹活动区域不相交。
     */
    public boolean outOfBounds() {
        return !border.intersects(ServerConfig.bulletArea());
    }

    /**
     * 归还弹量配额。
     */
    public void notifyOwner() {
        if (owner != null) {
            owner.returnBullet();
        }
    }
}
