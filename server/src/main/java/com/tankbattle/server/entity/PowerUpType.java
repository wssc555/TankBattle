package com.tankbattle.server.entity;

/**
 * 7 种道具。{@link #weight()} 的权重依次为 3:3:3:3:3:1:1。
 */
public enum PowerUpType {

    STAR(0, 3),
    FORTIFY(1, 3),
    NUKE(2, 3),
    SHIELD(3, 3),
    FREEZE(4, 3),
    MEGA_STAR(5, 1),
    LIFE(6, 1);

    private final int function;
    private final int weight;

    PowerUpType(int function, int weight) {
        this.function = function;
        this.weight = weight;
    }

    public int function() {
        return function;
    }

    public int weight() {
        return weight;
    }

    public static PowerUpType ofFunction(int function) {
        return switch (function) {
            case 0 -> STAR;
            case 1 -> FORTIFY;
            case 2 -> NUKE;
            case 3 -> SHIELD;
            case 4 -> FREEZE;
            case 5 -> MEGA_STAR;
            case 6 -> LIFE;
            default -> throw new IllegalArgumentException("非法道具编号: " + function);
        };
    }
}
