package com.tankbattle.server.system;

import com.tankbattle.server.entity.Actor;
import com.tankbattle.server.entity.Base;
import com.tankbattle.server.entity.BrickWall;
import com.tankbattle.server.entity.EnemyTank;
import com.tankbattle.server.entity.PlayerTank;
import com.tankbattle.server.entity.PowerUp;
import com.tankbattle.server.entity.River;
import com.tankbattle.server.entity.SteelWall;
import com.tankbattle.server.entity.Wall;

import java.awt.Rectangle;

/**
 * 碰撞分类与象限级判定。
 */
public final class CollisionSystem {

    /**
     * 接触类型。
     */
    public enum Contact {
        /**
         * 道具：拾取但不阻挡。
         */
        POWER_UP,
        /**
         * 砖墙 / 钢墙：需要象限级判定。
         */
        WALL,
        /**
         * 河流 / 基地：整格阻挡。
         */
        STATIC_BLOCK,
        /**
         * 坦克：按"上一帧是否重叠"决定是否拦截。
         */
        TANK,
        /**
         * 其它（子弹、爆炸、草地）：不参与阻挡。
         */
        OTHER
    }

    private CollisionSystem() {
    }

    public static Contact classify(Actor actor) {
        return switch (actor) {
            case PowerUp ignored -> Contact.POWER_UP;
            case BrickWall ignored -> Contact.WALL;
            case SteelWall ignored -> Contact.WALL;
            case River ignored -> Contact.STATIC_BLOCK;
            case Base ignored -> Contact.STATIC_BLOCK;
            case PlayerTank ignored -> Contact.TANK;
            case EnemyTank ignored -> Contact.TANK;
            default -> Contact.OTHER;
        };
    }

    /**
     * 墙体是否有任一未毁象限与给定矩形相交（象限级 11×11 检测）。
     */
    public static boolean hitsQuadrant(Wall wall, Rectangle area) {
        if (wall.isDestroyed()) {
            return false;
        }
        Rectangle[] quadrants = wall.quadrants();
        for (Rectangle quadrant : quadrants) {
            if (quadrant != null && quadrant.intersects(area)) {
                return true;
            }
        }
        return false;
    }
}
