package com.tankbattle.server.entity;

import com.tankbattle.common.model.Direction;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 砖墙细粒度破坏。用例矩阵覆盖「方向 × 威力 × 预毁形状」。
 */
class BrickWallTest {

    private static final int WALL_X = 100;
    private static final int WALL_Y = 100;

    private static BrickWall wall() {
        return new BrickWall(WALL_X, WALL_Y, BrickWall.FULL_BITS);
    }

    @Test
    void orientationBits() {
        assertEquals(0xFF00, BrickWall.bitsForOrientation(0), "#0 上半砖：下半已毁");
        assertEquals(0x00FF, BrickWall.bitsForOrientation(1), "#1 下半砖：上半已毁");
        assertEquals(0xCCCC, BrickWall.bitsForOrientation(2), "#2 左半砖：右两列已毁");
        assertEquals(0x3333, BrickWall.bitsForOrientation(3), "#3 右半砖：左两列已毁");
        assertEquals(0x0000, wall().shapeBits(), "完整砖墙无已毁子块");
    }

    @Test
    void halfWallQuadrants() {
        BrickWall top = new BrickWall(0, 0, BrickWall.bitsForOrientation(0));
        assertNotNull(top.quadrants()[0]);
        assertNotNull(top.quadrants()[1]);
        assertNull(top.quadrants()[2]);
        assertNull(top.quadrants()[3]);
    }

    @Test
    void firstHitFromAboveDestroysTopPair() {
        BrickWall wall = wall();
        // 只压到象限 0 的 a 子块（x 91..95），且未触及守卫子块 b
        Rectangle bullet = new Rectangle(91, 85, 5, 13);
        assertTrue(wall.damage(bullet, Direction.DOWN, 1));
        assertEquals(0b0000000000000011, wall.shapeBits());
        assertNotNull(wall.quadrants()[0], "首击未穿透，象限边界保留");
    }

    @Test
    void secondHitPenetratesToBottomPair() {
        BrickWall wall = wall();
        Rectangle bullet = new Rectangle(91, 85, 5, 13);
        wall.damage(bullet, Direction.DOWN, 1);
        assertTrue(wall.damage(bullet, Direction.DOWN, 1));
        assertEquals(0b0000000000110011, wall.shapeBits());
        assertNull(wall.quadrants()[0], "上下面均已毁，象限边界清空");
    }

    @Test
    void superBulletRemovesWholeTopRow() {
        BrickWall wall = wall();
        Rectangle bullet = new Rectangle(89, 85, 25, 13);
        assertTrue(wall.damage(bullet, Direction.DOWN, 2));
        assertEquals(0b0000000011111111, wall.shapeBits());
        assertNull(wall.quadrants()[0]);
        assertNull(wall.quadrants()[1]);
    }

    @Test
    void destroyedOnlyWhenAllBitsSet() {
        BrickWall wall = wall();
        wall.damage(new Rectangle(89, 85, 25, 13), Direction.DOWN, 2);
        assertFalse(wall.isDestroyed());
        wall.damage(new Rectangle(89, 98, 25, 33), Direction.DOWN, 2);
        assertEquals(0xFFFF, wall.shapeBits());
        assertTrue(wall.isDestroyed());
        assertNull(wall.quadrants()[2]);
        assertNull(wall.quadrants()[3]);
    }

    @Test
    void damageMatrixIsMonotonic() {
        // 覆盖 方向(4) × 威力(2) × 弹着点(4) × 尺寸(2) = 64 条路径，断言位图只置位、不清零
        int[] offsets = {0, 7, 14, 21};
        for (int power = 1; power <= 2; power++) {
            for (Direction direction : Direction.values()) {
                for (int offset : offsets) {
                    for (int size : new int[]{5, 13}) {
                        BrickWall wall = wall();
                        int before = wall.shapeBits();
                        Rectangle bullet = new Rectangle(
                                WALL_X - 12 + offset,
                                WALL_Y - 12 + offset,
                                direction.vertical() ? 5 : size,
                                direction.vertical() ? size : 5);
                        boolean stopped = wall.damage(bullet, direction, power);
                        int after = wall.shapeBits();
                        assertEquals(0, before & ~after, "破坏位图只能置位，不能清零");
                        assertEquals(0, after & ~0xFFFF, "位图不得超过 16 位");
                        assertTrue(stopped || after == before, "命中后要么置位、要么拦住子弹");
                    }
                }
            }
        }
    }
}
