package com.tankbattle.server.entity;

/**
 * 玩家 4 级状态参数表。
 */
public enum PlayerStatus {

    ONE(1, 7, 8, 1, 1),
    TWO(2, 12, 8, 1, 1),
    THREE(3, 12, 5, 2, 1),
    FOUR(4, 12, 5, 2, 2);

    private final int level;
    private final int bulletSpeed;
    private final int cooldownTicks;
    private final int maxBullets;
    private final int bulletPower;

    PlayerStatus(int level, int bulletSpeed, int cooldownTicks, int maxBullets, int bulletPower) {
        this.level = level;
        this.bulletSpeed = bulletSpeed;
        this.cooldownTicks = cooldownTicks;
        this.maxBullets = maxBullets;
        this.bulletPower = bulletPower;
    }

    /**
     * 状态等级 1..4（协议与纹理基址按此换算）。
     */
    public int level() {
        return level;
    }

    /**
     * 子弹飞行速度。
     */
    public int bulletSpeed() {
        return bulletSpeed;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    /**
     * 同屏弹量。
     */
    public int maxBullets() {
        return maxBullets;
    }

    public int bulletPower() {
        return bulletPower;
    }

    public static PlayerStatus ofLevel(int level) {
        return switch (level) {
            case 1 -> ONE;
            case 2 -> TWO;
            case 3 -> THREE;
            case 4 -> FOUR;
            default -> throw new IllegalArgumentException("非法玩家状态: " + level);
        };
    }
}
