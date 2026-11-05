package com.tankbattle.common.protocol;

import com.tankbattle.common.model.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 方向码 0..3 映射、非法码抛 IAE，以及 axis/dx/dy/reverse 的取值。
 */
class DirectionTest {

    @Test
    void mapsValidCodes() {
        assertEquals(Direction.UP, Direction.fromCode(0));
        assertEquals(Direction.DOWN, Direction.fromCode(1));
        assertEquals(Direction.LEFT, Direction.fromCode(2));
        assertEquals(Direction.RIGHT, Direction.fromCode(3));
        assertEquals(0, Direction.UP.code());
        assertEquals(3, Direction.RIGHT.code());
    }

    @Test
    void rejectsInvalidCodes() {
        assertThrows(IllegalArgumentException.class, () -> Direction.fromCode(4));
        assertThrows(IllegalArgumentException.class, () -> Direction.fromCode(-1));
        assertThrows(IllegalArgumentException.class, () -> Direction.fromCode(100));
    }

    @Test
    void axisMatchesLegacyDivision() {
        // axis()：上下=0、左右=1
        assertEquals(0, Direction.UP.axis());
        assertEquals(0, Direction.DOWN.axis());
        assertEquals(1, Direction.LEFT.axis());
        assertEquals(1, Direction.RIGHT.axis());
    }

    @Test
    void deltasAndReverse() {
        assertEquals(-1, Direction.UP.dy());
        assertEquals(1, Direction.DOWN.dy());
        assertEquals(-1, Direction.LEFT.dx());
        assertEquals(1, Direction.RIGHT.dx());
        for (Direction d : Direction.values()) {
            assertEquals(d, d.reverse().reverse());
        }
        assertTrue(Direction.UP.vertical());
        assertTrue(Direction.DOWN.vertical());
        assertFalse(Direction.LEFT.vertical());
        assertFalse(Direction.RIGHT.vertical());
    }
}
