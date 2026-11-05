package com.tankbattle.server.entity;

import com.tankbattle.common.protocol.S2c;
import com.tankbattle.common.model.Direction;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.core.GameContext;
import com.tankbattle.server.system.CombatSystem;
import com.tankbattle.server.system.MovementSystem;

/**
 * 玩家坦克：出生、生命、复活、无敌、双血、4 级状态。
 */
public final class PlayerTank extends Tank {

    private final int player;
    private final Vec spawn;

    private TankIntent intent = TankIntent.NONE;
    private PlayerStatus status = PlayerStatus.ONE;
    private int life = ServerConfig.PLAYER_LIFE;
    private int health = ServerConfig.PLAYER_HEALTH;
    private int bullets = ServerConfig.PLAYER_BULLETS;
    private int cooldown;
    private int invuln = ServerConfig.PLAYER_INVULN_FRAMES;
    private boolean frozen;
    private int score;

    public PlayerTank(int player) {
        super(spawnOf(player), Direction.UP);
        this.player = player;
        this.spawn = spawnOf(player);
        this.speed = 0;
    }

    public static Vec spawnOf(int player) {
        return player == 1
                ? new Vec(ServerConfig.SPAWN_P1_X, ServerConfig.SPAWN_P1_Y)
                : new Vec(ServerConfig.SPAWN_P2_X, ServerConfig.SPAWN_P2_Y);
    }

    @Override
    public void tick(GameContext ctx) {
        if (cooldown > 0) {
            cooldown--;
        }
        if (invuln > 0) {
            invuln--;
        }
        if (frozen) {
            return;
        }
        CombatSystem.tryFire(ctx, this);
        MovementSystem.movePlayer(ctx, this);
    }

    @Override
    public int textureIndex(int gameFlow) {
        int base = player == 1 ? ServerConfig.TEX_PLAYER1 : ServerConfig.TEX_PLAYER2;
        return base + (status.level() - 1) * ServerConfig.TEX_PLAYER_STATUS_STEP + direction.code();
    }

    @Override
    public boolean invulnerable() {
        return invuln > 0;
    }

    @Override
    public void hurt(GameContext ctx) {
        if (invuln != 0) {
            return;
        }
        if (health == 1) {
            ctx.spawnExplosion(S2c.BombSize.BIG, pos);
            life--;
            if (life == 0) {
                // 出局：仍以 (100000,100000) 参与同步，使客户端在画面外隐藏该坦克；
                // 冻结并清空意图/弹药/冷却，防止残存 fire 意图在出局点每帧生成即删的子弹
                teleport(new Vec(ServerConfig.PLAYER_OUT_X, ServerConfig.PLAYER_OUT_Y));
                frozen = true;
                intent = TankIntent.NONE;
                bullets = 0;
                cooldown = 0;
                return;
            }
            direction = Direction.UP;
            status = PlayerStatus.ONE;
            health = ServerConfig.PLAYER_HEALTH;
            bullets = ServerConfig.PLAYER_BULLETS;
            invuln = ServerConfig.PLAYER_INVULN_FRAMES;
            teleport(spawn);
        } else {
            health--;
            status = PlayerStatus.THREE;
        }
    }

    /**
     * 普通星星：1→2→3→4，升到 3 时同屏弹量 +1。
     */
    public void upgrade() {
        switch (status) {
            case ONE -> status = PlayerStatus.TWO;
            case TWO -> {
                status = PlayerStatus.THREE;
                bullets++;
            }
            case THREE -> status = PlayerStatus.FOUR;
            case FOUR -> {
                // 已满级，无变化
            }
        }
    }

    /**
     * 超级星星：直接满级 + 双血量，不加命。
     */
    public void applyMegaStar() {
        if (status.level() < 3) {
            bullets++;
        }
        status = PlayerStatus.FOUR;
        health = ServerConfig.PLAYER_MAX_HEALTH;
    }

    /**
     * 过关时复位；状态、血量与弹量保持不变。
     */
    public void resetForNextLevel() {
        direction = Direction.UP;
        invuln = ServerConfig.PLAYER_INVULN_FRAMES;
        teleport(spawn);
    }

    /**
     * 冻结/解冻（暂停、过关动画、出局、等待重连共用）。
     */
    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public boolean frozen() {
        return frozen;
    }

    public void setIntent(TankIntent intent) {
        this.intent = intent;
    }

    public TankIntent intent() {
        return intent;
    }

    public int player() {
        return player;
    }

    public PlayerStatus status() {
        return status;
    }

    public int life() {
        return life;
    }

    public int health() {
        return health;
    }

    public int bullets() {
        return bullets;
    }

    public void setBullets(int bullets) {
        this.bullets = bullets;
    }

    public void consumeBullet() {
        bullets--;
    }

    @Override
    public void returnBullet() {
        bullets++;
    }

    public void grantShield(int frames) {
        this.invuln = frames;
    }

    public void startCooldown(int frames) {
        this.cooldown = frames;
    }

    public int cooldown() {
        return cooldown;
    }

    public void increaseLife() {
        life++;
    }

    public int score() {
        return score;
    }

    public void addScore(int value) {
        this.score += value;
    }

    /**
     * 重开局：完全复位，复用当前实例以便外部引用不失效。
     */
    public void resetAll() {
        this.direction = Direction.UP;
        this.status = PlayerStatus.ONE;
        this.life = ServerConfig.PLAYER_LIFE;
        this.health = ServerConfig.PLAYER_HEALTH;
        this.bullets = ServerConfig.PLAYER_BULLETS;
        this.cooldown = 0;
        this.invuln = ServerConfig.PLAYER_INVULN_FRAMES;
        this.frozen = false;
        this.intent = TankIntent.NONE;
        this.speed = 0;
        this.score = 0;
        this.removed = false;
        teleport(spawn);
    }
}
