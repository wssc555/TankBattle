package com.tankbattle.server.entity;

/**
 * 整数像素坐标值对象。
 */
public record Vec(int x, int y) {

    public Vec plus(int dx, int dy) {
        return new Vec(x + dx, y + dy);
    }

    public Vec withX(int newX) {
        return new Vec(newX, y);
    }

    public Vec withY(int newY) {
        return new Vec(x, newY);
    }
}
