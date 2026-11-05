package com.tankbattle.server.system;

import com.tankbattle.common.protocol.S2c;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.core.GameContext;
import com.tankbattle.server.entity.Actor;
import com.tankbattle.server.entity.EnemyTank;
import com.tankbattle.server.entity.PlayerTank;
import com.tankbattle.server.entity.PowerUp;
import com.tankbattle.server.entity.PowerUpType;
import com.tankbattle.server.entity.Vec;

import java.util.List;

/**
 * 道具系统。
 * <p>
 * 护盾与冻结时长为 {@code 300 + random(400)}；{@code powerup.classicRoll=true} 时为固定 300 帧。
 */
public final class PowerUpSystem {

    private PowerUpSystem() {
    }

    /**
     * 在随机位置生成一个按权重抽取的道具。
     */
    public static void spawn(GameContext ctx) {
        PowerUpType type = pickType(ctx);
        int x = ServerConfig.POWERUP_POS_MIN + ctx.random().nextInt(ServerConfig.POWERUP_POS_RANGE);
        int y = ServerConfig.POWERUP_POS_MIN + ctx.random().nextInt(ServerConfig.POWERUP_POS_RANGE);
        int life = ServerConfig.POWERUP_LIFE_MIN + ctx.random().nextInt(ServerConfig.POWERUP_LIFE_RANGE);
        ctx.addActor(new PowerUp(type, new Vec(x, y), life));
    }

    /**
     * 3:3:3:3:3:1:1 权重抽取（总权重 17）。
     */
    public static PowerUpType pickType(GameContext ctx) {
        int roll = ctx.random().nextInt(ServerConfig.POWERUP_WEIGHT_TOTAL);
        int acc = 0;
        for (PowerUpType type : PowerUpType.values()) {
            acc += type.weight();
            if (roll < acc) {
                return type;
            }
        }
        return PowerUpType.LIFE;
    }

    /**
     * 拾取结算：+50 分后按类型分发效果。
     */
    public static void apply(GameContext ctx, PlayerTank tank, PowerUp powerUp) {
        tank.addScore(ServerConfig.POWERUP_SCORE);
        PowerUpType type = powerUp.type();
        switch (type) {
            case STAR -> tank.upgrade();
            case FORTIFY -> {
                if (ctx.base() != null) {
                    ctx.base().fortify();
                }
            }
            case NUKE -> nuke(ctx);
            case SHIELD -> tank.grantShield(timedFrames(ctx));
            case FREEZE -> ctx.freezeEnemies(timedFrames(ctx));
            case MEGA_STAR -> tank.applyMegaStar();
            case LIFE -> tank.increaseLife();
        }
        ctx.removeActor(powerUp);
    }

    /**
     * 护盾 / 冻结时长：默认 300..699 帧，classic 模式固定 300 帧。
     */
    static int timedFrames(GameContext ctx) {
        if (ctx.settings().classicPowerUpRoll()) {
            return ServerConfig.POWERUP_TIMED_MIN;
        }
        return ServerConfig.POWERUP_TIMED_MIN + ctx.random().nextInt(ServerConfig.POWERUP_TIMED_RANGE);
    }

    /**
     * 炸弹道具：清空全场敌人，保留未出场敌人计数。
     */
    private static void nuke(GameContext ctx) {
        List<Actor> snapshot = List.copyOf(ctx.actors());
        for (Actor actor : snapshot) {
            if (actor instanceof EnemyTank enemy) {
                ctx.spawnExplosion(S2c.BombSize.BIG, enemy.pos());
                ctx.removeActor(enemy);
            }
        }
        ctx.level().setAliveEnemies(0);
        ctx.level().setDeathCount(ServerConfig.ENEMY_PER_LEVEL - ctx.level().enemyLeft());
    }
}
