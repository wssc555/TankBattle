package com.tankbattle.server.system;

import com.tankbattle.common.model.Direction;
import com.tankbattle.server.entity.BrickWall;
import com.tankbattle.server.util.GridMath;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 虚拟网格吸附边界值与象限级碰撞判定。
 */
class CollisionSystemTest {

    @Test
    void snapKeepsAlignedPositions() {
        assertEquals(23, GridMath.snap(23));
        assertEquals(48, GridMath.snap(48));
        assertEquals(35, GridMath.snap(35));
        assertEquals(198, GridMath.snap(198));
    }

    @Test
    void snapRoundsNearbyCoordinatesToGrid() {
        // 余数 < 7 吸附到格心 - 12.5 的下边界，最终收敛到 23 / 48
        assertEquals(23, GridMath.snap(20));
        assertEquals(48, GridMath.snap(45));
        // 余数 > 18 吸附到下一格
        assertEquals(48, GridMath.snap(51));
    }

    @Test
    void snapClampsOutOfRangeCoordinates() {
        assertEquals(23, GridMath.snap(10));
        assertEquals(23, GridMath.snap(16));
        assertEquals(498, GridMath.snap(492));
        assertEquals(498, GridMath.snap(499));
    }

    @Test
    void snapBoundaryValuesMatchVirtualGridSpec() {
        // 吸附边界值 —— 余数恰为 7/18 落中带（13），余数 6 吸附下界、19 吸附上界
        assertEquals(23, GridMath.snap(17), "v=17（余数恰 7，且为 MIN_V 下界）→ 中带 13");
        assertEquals(23, GridMath.snap(28), "余数恰 18（HIGH 上界）→ 中带 13");
        assertEquals(35, GridMath.snap(41), "余数 6（LOW 下界之一下一格）→ 吸附下界");
        assertEquals(60, GridMath.snap(54), "余数 19（HIGH 之上）→ 吸附上界");
    }

    @Test
    void quadrantDetectionUsesElevenPixelBoxes() {
        BrickWall wall = new BrickWall(100, 100, BrickWall.FULL_BITS);
        assertTrue(CollisionSystem.hitsQuadrant(wall, new Rectangle(91, 85, 5, 13)));
        assertFalse(CollisionSystem.hitsQuadrant(wall, new Rectangle(0, 0, 5, 5)));
        // 象限之间的 1px 缝隙（x/y = 100）不应命中
        assertFalse(CollisionSystem.hitsQuadrant(wall, new Rectangle(100, 100, 1, 1)));
    }

    @Test
    void destroyedWallNeverBlocks() {
        BrickWall wall = new BrickWall(100, 100, BrickWall.FULL_BITS);
        wall.damage(new Rectangle(89, 85, 25, 13), Direction.DOWN, 2);
        wall.damage(new Rectangle(89, 98, 25, 33), Direction.DOWN, 2);
        assertTrue(wall.isDestroyed());
        assertFalse(CollisionSystem.hitsQuadrant(wall, new Rectangle(89, 89, 25, 25)));
    }
}
