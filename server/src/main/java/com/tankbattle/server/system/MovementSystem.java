package com.tankbattle.server.system;

import com.tankbattle.common.model.Direction;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.core.GameContext;
import com.tankbattle.server.entity.Actor;
import com.tankbattle.server.entity.EnemyTank;
import com.tankbattle.server.entity.PlayerTank;
import com.tankbattle.server.entity.PowerUp;
import com.tankbattle.server.entity.Tank;
import com.tankbattle.server.entity.TankIntent;
import com.tankbattle.server.entity.Vec;
import com.tankbattle.server.entity.Wall;

import java.awt.Rectangle;

/**
 * 移动系统：意图 → 试位移 → 碰撞回退 → 虚拟网格对齐。
 * <p>
 * 玩家与敌坦共用同一套位移/回退规则，差异只在速度来源与碰撞响应。
 */
public final class MovementSystem {

    private MovementSystem() {
    }

    // ------------------------------------------------------------------
    // 玩家
    // ------------------------------------------------------------------

    public static void movePlayer(GameContext ctx, PlayerTank tank) {
        TankIntent intent = tank.intent();
        Vec prev = tank.pos();
        Rectangle prevBorder = Tank.boxAt(prev);

        Vec target = prev;
        boolean moving = true;
        int speed = tank.speed();

        if (intent.up()) {
            if (!tank.direction().vertical()) {
                target = target.withX(tank.vpos().x());
            }
            target = target.plus(0, -speed);
            tank.setDirection(Direction.UP);
        } else if (intent.down()) {
            if (!tank.direction().vertical()) {
                target = target.withX(tank.vpos().x());
            }
            target = target.plus(0, speed);
            tank.setDirection(Direction.DOWN);
        } else if (intent.left()) {
            if (tank.direction().vertical()) {
                target = target.withY(tank.vpos().y());
            }
            target = target.plus(-speed, 0);
            tank.setDirection(Direction.LEFT);
        } else if (intent.right()) {
            if (tank.direction().vertical()) {
                target = target.withY(tank.vpos().y());
            }
            target = target.plus(speed, 0);
            tank.setDirection(Direction.RIGHT);
        } else {
            moving = false;
        }

        // 速度在位移之后调整（第一帧位移 0，随后 1→2→3 加速）
        if (moving) {
            if (tank.speed() < ServerConfig.PLAYER_MAX_SPEED) {
                tank.setSpeed(tank.speed() + 1);
            }
        } else if (tank.speed() > 0) {
            tank.setSpeed(tank.speed() - 1);
        }

        tank.moveTo(target);
        // 先刷新索引，保证后续坦克查询到的是本帧真实位置（行为等价于原全表扫描）
        ctx.grid().refresh(tank);

        if (!tank.border().intersects(ServerConfig.playerArea())) {
            tank.restoreToVpos();
            ctx.grid().refresh(tank);
            return;
        }

        Rectangle border = tank.border();
        for (Actor actor : ctx.query(border, tank)) {
            if (!border.intersects(actor.border())) {
                continue;
            }
            switch (CollisionSystem.classify(actor)) {
                case POWER_UP -> ctx.collect(tank, (PowerUp) actor);
                case WALL -> {
                    if (CollisionSystem.hitsQuadrant((Wall) actor, border)) {
                        tank.restoreToVpos();
                        ctx.grid().refresh(tank);
                        return;
                    }
                }
                case STATIC_BLOCK -> {
                    tank.restoreToVpos();
                    ctx.grid().refresh(tank);
                    return;
                }
                case TANK -> {
                    boolean enemy = actor instanceof EnemyTank;
                    if (enemy || !prevBorder.intersects(actor.border())) {
                        tank.restoreToPrev(prev);
                        ctx.grid().refresh(tank);
                        return;
                    }
                }
                case OTHER -> {
                    // 子弹、爆炸、草地不参与阻挡
                }
            }
        }

        tank.snapToVirtualGrid();
        ctx.grid().refresh(tank);
    }

    // ------------------------------------------------------------------
    // 敌坦克
    // ------------------------------------------------------------------

    public static void moveEnemy(GameContext ctx, EnemyTank tank) {
        Vec prev = tank.pos();
        Rectangle prevBorder = Tank.boxAt(prev);

        tank.moveTo(prev.plus(tank.direction().dx() * tank.speed(),
                tank.direction().dy() * tank.speed()));
        ctx.grid().refresh(tank);

        if (!tank.border().intersects(ServerConfig.playerArea())) {
            tank.randomTurn(ctx);
            tank.restoreToVpos();
            ctx.grid().refresh(tank);
            return;
        }

        Rectangle border = tank.border();
        for (Actor actor : ctx.query(border, tank)) {
            if (!border.intersects(actor.border())) {
                continue;
            }
            switch (CollisionSystem.classify(actor)) {
                case WALL -> {
                    if (CollisionSystem.hitsQuadrant((Wall) actor, border)) {
                        bounce(ctx, tank);
                        return;
                    }
                }
                case STATIC_BLOCK -> {
                    bounce(ctx, tank);
                    return;
                }
                case TANK -> {
                    if (!prevBorder.intersects(actor.border())) {
                        tank.restoreToPrev(prev);
                        turnAfterBump(ctx, tank);
                        return;
                    }
                }
                case POWER_UP, OTHER -> {
                    // 敌坦克不拾取道具
                }
            }
        }

        tank.snapToVirtualGrid();
        ctx.grid().refresh(tank);
    }

    /**
     * 撞墙 / 河 / 基地：10% 概率随机换向，并退回虚拟网格位置。
     */
    private static void bounce(GameContext ctx, EnemyTank tank) {
        if (ctx.random().nextDouble() > ServerConfig.ENEMY_TURN_CHANCE) {
            tank.setDirection(Direction.fromCode(ctx.random().nextInt(4)));
        }
        tank.restoreToVpos();
        ctx.grid().refresh(tank);
    }

    /**
     * 撞坦克：退回原位 + 随机换向（跨轴时再拉回虚拟坐标）。
     */
    private static void turnAfterBump(GameContext ctx, EnemyTank tank) {
        Direction next = Direction.fromCode(ctx.random().nextInt(4));
        if (tank.direction() != next) {
            if (tank.direction().axis() != next.axis()) {
                tank.restoreToVpos();
            }
            tank.setDirection(next);
        }
        ctx.grid().refresh(tank);
    }
}
