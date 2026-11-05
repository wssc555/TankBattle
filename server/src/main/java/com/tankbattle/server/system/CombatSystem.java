package com.tankbattle.server.system;

import com.tankbattle.common.config.GameConfig;
import com.tankbattle.common.model.Direction;
import com.tankbattle.common.protocol.S2c;
import com.tankbattle.server.core.GameContext;
import com.tankbattle.server.core.GameEvent;
import com.tankbattle.server.entity.Actor;
import com.tankbattle.server.entity.Base;
import com.tankbattle.server.entity.BrickWall;
import com.tankbattle.server.entity.Bullet;
import com.tankbattle.server.entity.EnemyTank;
import com.tankbattle.server.entity.PlayerStatus;
import com.tankbattle.server.entity.PlayerTank;
import com.tankbattle.server.entity.SteelWall;
import com.tankbattle.server.entity.Tank;
import com.tankbattle.server.entity.Vec;

import java.util.List;

/**
 * 战斗系统：开火配额与命中规则管线。
 * <p>
 * 命中规则表：
 * <pre>
 * 目标 →   砖墙        钢墙        敌弹        玩家弹      玩家        敌坦克              基地
 * 玩家弹   毁形/消失   消失无伤    同归        同归        吸收无伤    扣血/击杀+type×100  基地毁+GameOver
 * 敌弹     毁形/消失   消失无伤    穿过        —           扣血        穿过                基地毁+GameOver
 * </pre>
 */
public final class CombatSystem {

    private CombatSystem() {
    }

    // ------------------------------------------------------------------
    // 开火
    // ------------------------------------------------------------------

    public static void tryFire(GameContext ctx, PlayerTank tank) {
        if (!tank.intent().fire() || tank.cooldown() != 0 || tank.bullets() <= 0) {
            return;
        }
        PlayerStatus status = tank.status();
        if (status == PlayerStatus.ONE) {
            tank.setBullets(1);
        }
        fire(ctx, tank, status.bulletSpeed(), status.bulletPower());
        tank.startCooldown(status.cooldownTicks());
        tank.consumeBullet();
    }

    /**
     * 在车体前缘生成一颗子弹。
     */
    public static void fire(GameContext ctx, Tank tank, int speed, int power) {
        Direction direction = tank.direction();
        Vec pos = tank.pos().plus(direction.dx() * GameConfig.TANK_HALF,
                direction.dy() * GameConfig.TANK_HALF);
        ctx.addActor(new Bullet(pos, direction, speed, power, tank));
    }

    // ------------------------------------------------------------------
    // 命中结算
    // ------------------------------------------------------------------

    public static void resolve(GameContext ctx, Bullet bullet) {
        if (bullet.outOfBounds()) {
            ctx.removeActor(bullet);
            bullet.notifyOwner();
            ctx.spawnExplosion(S2c.BombSize.SMALL, bullet.pos());
            return;
        }

        java.awt.Rectangle border = bullet.border();
        List<Actor> candidates = ctx.query(border, bullet);
        // 结算顺序（基地保护墙/地图墙 → 基地 → 玩家 → 子弹 → 敌坦克）：
        // 保证护甲墙未破时子弹先在墙上消失、绝不触达基地；同帧不重复结算。
        // 稳定排序保留格序，模拟确定性不受影响。
        candidates.sort(java.util.Comparator.comparingInt(CombatSystem::resolvePriority));

        boolean hit = false;
        for (Actor actor : candidates) {
            if (actor == bullet.owner() || !border.intersects(actor.border())) {
                continue;
            }
            switch (actor) {
                case BrickWall wall -> {
                    if (!wall.isDestroyed()) {
                        int before = wall.shapeBits();
                        if (wall.damage(border, bullet.direction(), bullet.power())) {
                            hit = true;
                        }
                        if (wall.shapeBits() != before) {
                            ctx.emit(new GameEvent.WallChanged(wall));
                        }
                    }
                }
                case SteelWall wall -> {
                    if (!wall.isDestroyed()) {
                        int before = wall.shapeBits();
                        if (wall.damage(border, bullet.direction(), bullet.power())) {
                            hit = true;
                        }
                        if (wall.shapeBits() != before) {
                            ctx.emit(new GameEvent.WallChanged(wall));
                        }
                    }
                }
                case Bullet other -> {
                    // 只要对方是玩家子弹（含 P1/P2 互射）即同归；敌弹之间互相穿过
                    if (other.fromPlayer()) {
                        hit = true;
                        ctx.removeActor(other);
                        other.notifyOwner();
                    }
                }
                case PlayerTank player -> {
                    // 只有敌方子弹才造成伤害，玩家子弹被吸收
                    if (bullet.fromEnemy()) {
                        player.hurt(ctx);
                    }
                    hit = true;
                }
                case EnemyTank enemy -> {
                    if (bullet.fromPlayer()) {
                        if (enemy.willDieOnNextHit() && bullet.owner() instanceof PlayerTank scorer) {
                            scorer.addScore(enemy.type().score());
                        }
                        enemy.hurt(ctx);
                        hit = true;
                    }
                }
                case Base base -> {
                    base.doom(ctx);
                    hit = true;
                }
                default -> {
                    // 爆炸、河流、草地等不参与命中
                }
            }
            // "命中即消失"：一发子弹同帧只结算第一个目标，
            // 防止护甲墙未破时同帧误杀基地、一弹毁多墙、穿墙杀坦克、弹弹互消后继续毁墙。
            if (hit) {
                break;
            }
        }

        if (hit) {
            ctx.removeActor(bullet);
            bullet.notifyOwner();
            ctx.spawnExplosion(S2c.BombSize.SMALL, bullet.pos());
            return;
        }

        bullet.advance();
        ctx.grid().refresh(bullet);
    }

    /**
     * 命中结算优先级：基地保护墙 → 基地 → 地图墙 → 玩家 → 子弹 → 敌人。
     * 墙（含基地护甲）最先、基地在墙之后；玩家先于子弹与敌坦克，
     * 子弹与敌坦克之间固定为子弹优先。
     */
    private static int resolvePriority(Actor actor) {
        if (actor instanceof com.tankbattle.server.entity.Wall) {
            return 0;
        }
        if (actor instanceof Base) {
            return 1;
        }
        if (actor instanceof PlayerTank) {
            return 2;
        }
        if (actor instanceof Bullet) {
            return 3;
        }
        if (actor instanceof EnemyTank) {
            return 4;
        }
        return 5;
    }
}
