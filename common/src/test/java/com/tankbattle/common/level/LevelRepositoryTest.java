package com.tankbattle.common.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 8 关符号统计基线、L4 权威值、关卡编号模 8 归一。
 * <p>
 * LevelRepository/Tile/Cell 属 common 单一来源，服务端亦可独立验证地图基线。
 * <p>
 * L4 第 15 行第 10/11 列为 {@code ##}/{@code ##}，对应完整砖 105 块、钢墙 14 块。
 */
class LevelRepositoryTest {

    @Test
    void matchesAuthoritativeSymbolCounts() {
        LevelDefinition l1 = LevelRepository.get(1);
        assertEquals(84, l1.count(Tile.BRICK_FULL));
        assertEquals(6, steelCount(l1));

        LevelDefinition l2 = LevelRepository.get(2);
        assertEquals(58, l2.count(Tile.BRICK_FULL));
        assertEquals(4, steelCount(l2));
        assertEquals(94, l2.count(Tile.GRASS));
        assertEquals(66, l2.count(Tile.RIVER));

        LevelDefinition l3 = LevelRepository.get(3);
        assertEquals(78, l3.count(Tile.BRICK_FULL) + l3.count(Tile.BRICK_0));
        assertEquals(32, steelCount(l3));
        assertEquals(108, l3.count(Tile.GRASS));

        LevelDefinition l4 = LevelRepository.get(4);
        assertEquals(105, l4.count(Tile.BRICK_FULL));
        assertEquals(14, steelCount(l4));
        assertEquals(99, l4.count(Tile.GRASS));
        assertEquals(0, l4.count(Tile.RIVER));

        LevelDefinition l5 = LevelRepository.get(5);
        assertEquals(6, steelCount(l5));
        assertEquals(128, l5.count(Tile.RIVER));

        LevelDefinition l6 = LevelRepository.get(6);
        assertEquals(113, steelCount(l6));
        assertEquals(114, l6.count(Tile.GRASS));

        LevelDefinition l7 = LevelRepository.get(7);
        assertEquals(45, l7.count(Tile.BRICK_FULL));
        assertEquals(27, steelCount(l7));
        assertEquals(18, l7.count(Tile.GRASS));
        assertEquals(18, l7.count(Tile.RIVER));

        assertEquals(66, LevelRepository.get(8).count(Tile.BRICK_FULL));
    }

    @Test
    void levelFourAuthoritativeCellsAreFullBricks() {
        LevelDefinition l4 = LevelRepository.getExact(4);
        assertEquals(Tile.BRICK_FULL, l4.at(Cell.index(9, 14)));
        assertEquals(Tile.BRICK_FULL, l4.at(Cell.index(10, 14)));
    }

    @Test
    void normalizesLevelIndexModuloEight() {
        assertEquals(1, LevelRepository.normalize(9));
        assertEquals(2, LevelRepository.normalize(10));
        assertEquals(8, LevelRepository.normalize(0));
        assertEquals(7, LevelRepository.normalize(-1));
        assertEquals(1, LevelRepository.normalize(1));
    }

    @Test
    void rejectsOutOfRangeExactLookup() {
        assertThrows(IllegalArgumentException.class, () -> LevelRepository.getExact(9));
        assertThrows(IllegalArgumentException.class, () -> LevelRepository.getExact(0));
    }

    private static int steelCount(LevelDefinition definition) {
        return (int) (definition.count(Tile.STEEL_FULL) + definition.count(Tile.STEEL_0)
                + definition.count(Tile.STEEL_1) + definition.count(Tile.STEEL_2)
                + definition.count(Tile.STEEL_3));
    }
}
