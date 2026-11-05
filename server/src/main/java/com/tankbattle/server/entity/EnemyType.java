package com.tankbattle.server.entity;

import com.tankbattle.server.config.ServerConfig;

/**
 * 4 型敌方坦克参数。
 *
 * @param code          型号 1..4
 * @param speed         移动速度
 * @param fireThreshold "不开火阈值"，实际每帧开火概率 = 1 - 阈值
 * @param bulletSpeed   子弹速度
 * @param cooldown      开火冷却帧数（Type3 为 5，其余 7）
 * @param health        初始血量（Type4 为 3，其余 0——0 表示一击必死）
 * @param texBase       纹理基址
 */
public enum EnemyType {

    BASIC(1, 2, 0.95, 7, 7, 0, 38),
    FAST(2, 4, 0.95, 7, 7, 0, 2),
    SNIPER(3, 2, 0.90, 12, 5, 0, 10),
    HEAVY(4, 2, 0.95, 7, 7, 3, 18);

    private final int code;
    private final int speed;
    private final double fireThreshold;
    private final int bulletSpeed;
    private final int cooldown;
    private final int health;
    private final int texBase;

    EnemyType(int code, int speed, double fireThreshold, int bulletSpeed, int cooldown, int health, int texBase) {
        this.code = code;
        this.speed = speed;
        this.fireThreshold = fireThreshold;
        this.bulletSpeed = bulletSpeed;
        this.cooldown = cooldown;
        this.health = health;
        this.texBase = texBase;
    }

    public int code() {
        return code;
    }

    public int speed() {
        return speed;
    }

    public double fireThreshold() {
        return fireThreshold;
    }

    public int bulletSpeed() {
        return bulletSpeed;
    }

    public int cooldown() {
        return cooldown;
    }

    public int health() {
        return health;
    }

    public int texBase() {
        return texBase;
    }

    /**
     * 击杀得分 = 型号 × 100。
     */
    public int score() {
        return code * ServerConfig.ENEMY_SCORE_UNIT;
    }

    public static EnemyType ofCode(int code) {
        return switch (code) {
            case 1 -> BASIC;
            case 2 -> FAST;
            case 3 -> SNIPER;
            case 4 -> HEAVY;
            default -> throw new IllegalArgumentException("非法敌人型号: " + code);
        };
    }
}
