package com.tankbattle.server.entity;

import com.tankbattle.common.protocol.S2c;
import com.tankbattle.common.model.Direction;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.core.GameContext;
import com.tankbattle.server.core.GameEvent;
import com.tankbattle.server.system.CombatSystem;
import com.tankbattle.server.system.MovementSystem;

import java.util.Random;

/**
 * 敌方坦克：4 型参数、随机巡游 AI、闪烁掉道具、全局冻结。
 */
public final class EnemyTank extends Tank {

    private final EnemyType type;
    private int health;
    private boolean flashing;
    private int interval;
    private int bullets = 1;
    private int cooldown;

    public EnemyTank(EnemyType type, boolean flashing, int x, int y, Random random) {
        super(new Vec(x, y), Direction.fromCode(random.nextInt(4)));
        this.type = type;
        this.flashing = flashing;
        this.health = type.health();
        this.speed = type.speed();
        this.interval = random.nextInt(ServerConfig.ENEMY_AI_INTERVAL_MAX);
    }

    @Override
    public void tick(GameContext ctx) {
        if (ctx.enemiesFrozen()) {
            return;
        }

        if (interval > 0) {
            interval--;
        }
        if (interval == 0) {
            interval = ctx.random().nextInt(ServerConfig.ENEMY_AI_INTERVAL_MAX);
            Direction next = Direction.fromCode(ctx.random().nextInt(4));
            if (direction != next) {
                if (direction.axis() != next.axis()) {
                    restoreToVpos();
                }
                direction = next;
            }
        }

        if (cooldown > 0) {
            cooldown--;
        }
        if (ctx.random().nextDouble() > type.fireThreshold() && cooldown == 0 && bullets > 0) {
            CombatSystem.fire(ctx, this, type.bulletSpeed(), 1);
            cooldown = type.cooldown();
            bullets--;
        }

        MovementSystem.moveEnemy(ctx, this);
    }

    @Override
    public int textureIndex(int gameFlow) {
        int base = type.texBase();
        if (type == EnemyType.HEAVY) {
            base += (type.health() - health) * ServerConfig.TEX_HEAVY_ARMOR_STEP;
        }
        if (flashing && gameFlow % ServerConfig.ENEMY_FLASH_PERIOD > ServerConfig.ENEMY_FLASH_THRESHOLD) {
            base += type == EnemyType.HEAVY ? 16 : ServerConfig.TEX_FLASH_OFFSET;
        }
        return base + direction.code();
    }

    @Override
    public boolean invulnerable() {
        return false;
    }

    @Override
    public void hurt(GameContext ctx) {
        if (flashing) {
            ctx.spawnPowerUp();
        }
        flashing = false;

        boolean death = type != EnemyType.HEAVY || health == 0;
        if (!death) {
            health--;
        }
        if (death) {
            ctx.level().decrementAliveEnemies();
            ctx.level().incrementDeathCount();
            ctx.removeActor(this);
            ctx.spawnExplosion(S2c.BombSize.BIG, pos);
            ctx.emit(new GameEvent.EnemyKilled(this));
        }
    }

    /**
     * 本次命中是否会致死（用于击杀计分）。
     */
    public boolean willDieOnNextHit() {
        return type != EnemyType.HEAVY || health == 0;
    }

    @Override
    public void returnBullet() {
        bullets++;
    }

    public void consumeBullet() {
        bullets--;
    }

    public EnemyType type() {
        return type;
    }

    public int health() {
        return health;
    }

    public boolean flashing() {
        return flashing;
    }

    public int interval() {
        return interval;
    }

    public void resetInterval(int value) {
        this.interval = value;
    }

    public void randomTurn(GameContext ctx) {
        this.direction = Direction.fromCode(ctx.random().nextInt(4));
        this.interval = ctx.random().nextInt(ServerConfig.ENEMY_AI_INTERVAL_AFTER_BOUNCE);
    }
}
