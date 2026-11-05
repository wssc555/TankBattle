package com.tankbattle.server.entity;

import com.tankbattle.common.config.GameConfig;
import com.tankbattle.common.model.Direction;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.core.GameContext;
import com.tankbattle.server.util.GridMath;

import java.awt.Rectangle;

/**
 * 坦克公共状态：真实坐标、虚拟网格坐标、朝向、速度、碰撞盒。
 * <p>
 * 虚拟网格坐标 {@code vpos}：转向跨轴时先把真实坐标拉回虚拟坐标，
 * 从而让坦克沿着 25px 网格行走。
 */
public abstract sealed class Tank implements Actor permits PlayerTank, EnemyTank {

    protected Vec pos;
    protected Vec vpos;
    protected Direction direction;
    protected int speed;
    protected Rectangle border;
    protected boolean removed;

    protected Tank(Vec pos, Direction direction) {
        this.pos = pos;
        this.vpos = pos;
        this.direction = direction;
        this.border = boxAt(pos);
    }

    public static Rectangle boxAt(Vec p) {
        return new Rectangle(p.x() - GameConfig.TANK_HALF, p.y() - GameConfig.TANK_HALF,
                ServerConfig.TANK_BOX, ServerConfig.TANK_BOX);
    }

    public Vec pos() {
        return pos;
    }

    public Vec vpos() {
        return vpos;
    }

    public Direction direction() {
        return direction;
    }

    public int speed() {
        return speed;
    }

    public void setSpeed(int speed) {
        this.speed = speed;
    }

    @Override
    public Rectangle border() {
        return border;
    }

    @Override
    public boolean destroyed() {
        return removed;
    }

    @Override
    public void markDestroyed() {
        this.removed = true;
    }

    /**
     * 纹理索引（协议 n 指令的第三段）。闪烁类敌人需要当前帧计数。
     */
    public abstract int textureIndex(int gameFlow);

    /**
     * 玩家：无敌帧未结束；敌人：闪烁中（用于派生 i 指令）。
     */
    public abstract boolean invulnerable();

    /**
     * 被子弹命中。由 CombatSystem 调用。
     */
    public abstract void hurt(GameContext ctx);

    /**
     * 开火后归还弹量配额。
     */
    public abstract void returnBullet();

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    /**
     * 移动到新坐标并同步碰撞盒。
     */
    public void moveTo(Vec target) {
        this.pos = target;
        syncBorder();
    }

    /**
     * 撤销到虚拟网格位置（撞墙 / 出界时）。
     */
    public void restoreToVpos() {
        this.pos = vpos;
        syncBorder();
    }

    /**
     * 撤回上一帧位置（撞坦克时）。
     */
    public void restoreToPrev(Vec prev) {
        this.pos = prev;
        syncBorder();
    }

    /**
     * 重新计算虚拟网格坐标。
     */
    public void snapToVirtualGrid() {
        this.vpos = new Vec(GridMath.snap(pos.x()), GridMath.snap(pos.y()));
    }

    public void teleport(Vec target) {
        this.pos = target;
        this.vpos = target;
        syncBorder();
    }

    protected void syncBorder() {
        this.border = boxAt(pos);
    }
}
