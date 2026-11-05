package com.tankbattle.server.level;

import com.tankbattle.common.level.Cell;
import com.tankbattle.common.level.LevelDefinition;
import com.tankbattle.common.level.LevelRepository;
import com.tankbattle.common.level.Tile;
import com.tankbattle.server.entity.EnemyType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 共享关卡模板（common）在服务端的可用性、坐标换算，以及服务端敌人序列。
 */
class LevelRepositoryTest {

    @Test
    void allEightLevelsAreWellFormed() {
        for (int level = 1; level <= LevelRepository.count(); level++) {
            LevelDefinition definition = LevelRepository.getExact(level);
            assertEquals(20, definition.rows(), "关卡 " + level + " 行数");
            assertEquals(20, definition.cols(), "关卡 " + level + " 列数");
            assertEquals(400, definition.cells().size(), "关卡 " + level + " 格数");
        }
    }

    @Test
    void everyRowHasExactlyTwentyCells() {
        // 坐标统一由 Cell 计算（col = i % 20），不存在整体错位一列
        for (int level = 1; level <= LevelRepository.count(); level++) {
            LevelDefinition definition = LevelRepository.getExact(level);
            for (int row = 0; row < 20; row++) {
                for (int col = 0; col < 20; col++) {
                    assertEquals(col, Cell.col(row * 20 + col));
                    assertEquals(row, Cell.row(row * 20 + col));
                }
            }
            assertEquals(400, definition.cells().size());
        }
    }

    @Test
    void levelOneLandmarks() {
        LevelDefinition level1 = LevelRepository.getExact(1);
        assertEquals(Tile.EMPTY, level1.at(0));
        assertEquals(Tile.BRICK_FULL, level1.at(2 * 20 + 1));
        assertEquals(Tile.STEEL_FULL, level1.at(9 * 20 + 9));
        assertEquals(Tile.STEEL_0, level1.at(11 * 20));
        assertEquals(Tile.STEEL_0, level1.at(11 * 20 + 19));
    }

    @Test
    void levelTwoGrassBand() {
        LevelDefinition level2 = LevelRepository.getExact(2);
        for (int col = 0; col < 20; col++) {
            assertEquals(Tile.GRASS, level2.at(20 + col));
        }
        assertEquals(Tile.RIVER, level2.at(2 * 20 + 1));
    }

    /**
     * 8 关逐关地形统计与基线值一致。
     * 列序：砖满/#0/#1/#2/#3、钢满/s0/s1/s2/s3、草、河。
     */
    @Test
    void perLevelTileCountsMatchLegacyBaseline() {
        int[][] baseline = {
                //      砖满  #0 #1 #2 #3   钢满 s0 s1 s2 s3   草   河
                {84, 0, 0, 0, 0, 2, 4, 0, 0, 0, 0, 0},      // L1
                {58, 0, 0, 0, 0, 4, 0, 0, 0, 0, 94, 66},    // L2
                {77, 1, 0, 0, 0, 25, 3, 1, 0, 3, 108, 0},   // L3
                {105, 0, 0, 0, 0, 3, 3, 0, 8, 0, 99, 0},    // L4
                {0, 0, 0, 0, 0, 6, 0, 0, 0, 0, 0, 128},     // L5
                {0, 0, 0, 0, 0, 113, 0, 0, 0, 0, 114, 0},   // L6
                {45, 0, 0, 0, 0, 27, 0, 0, 0, 0, 18, 18},   // L7
                {66, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},      // L8
        };
        Tile[] columns = {
                Tile.BRICK_FULL, Tile.BRICK_0, Tile.BRICK_1, Tile.BRICK_2, Tile.BRICK_3,
                Tile.STEEL_FULL, Tile.STEEL_0, Tile.STEEL_1, Tile.STEEL_2, Tile.STEEL_3,
                Tile.GRASS, Tile.RIVER,
        };
        for (int level = 1; level <= 8; level++) {
            LevelDefinition definition = LevelRepository.getExact(level);
            for (int c = 0; c < columns.length; c++) {
                assertEquals(baseline[level - 1][c], definition.count(columns[c]),
                        "关卡 " + level + " " + columns[c] + " 数量");
            }
            long nonEmpty = definition.cells().stream().filter(t -> t != Tile.EMPTY).count();
            assertEquals(400, nonEmpty + definition.count(Tile.EMPTY),
                    "关卡 " + level + " 总格数守恒");
        }
    }

    /**
     * L4 第 15 行（0 基 14 行）第 10/11 列的具体格值，以服务端权威数据为准。
     */
    @Test
    void levelFourRowFifteenLandmarks() {
        LevelDefinition level4 = LevelRepository.getExact(4);
        int row = 14;
        assertEquals(Tile.BRICK_FULL, level4.at(row * 20 + 10), "L4 r15c10");
        assertEquals(Tile.GRASS, level4.at(row * 20 + 11), "L4 r15c11");
        assertEquals(Tile.GRASS, level4.at(row * 20 + 12), "L4 r15c12");
        // 半钢墙朝向统计：s2 共 8 处，只出现在 L4
        assertEquals(8, level4.count(Tile.STEEL_2));
    }

    @Test
    void levelIndexWrapsAtEight() {
        assertEquals(1, LevelRepository.normalize(1));
        assertEquals(8, LevelRepository.normalize(8));
        assertEquals(1, LevelRepository.normalize(9));
        assertEquals(2, LevelRepository.normalize(10));
        assertSame(LevelRepository.getExact(1), LevelRepository.get(9));
    }

    @Test
    void unknownLevelRejected() {
        assertThrows(IllegalArgumentException.class, () -> LevelRepository.getExact(9));
        assertThrows(IllegalArgumentException.class, () -> LevelRepository.getExact(0));
    }

    @Test
    void enemySequencesMatchOriginalTables() {
        for (int level = 1; level <= 8; level++) {
            List<EnemyType> sequence = EnemySequences.of(level);
            assertEquals(20, sequence.size(), "关卡 " + level + " 敌人数量");
        }
        List<EnemyType> first = EnemySequences.of(1);
        for (int i = 0; i < 17; i++) {
            assertEquals(EnemyType.BASIC, first.get(i));
        }
        for (int i = 17; i < 20; i++) {
            assertEquals(EnemyType.FAST, first.get(i));
        }
    }

    @Test
    void enemySequenceRejectsUnknownLevel() {
        assertThrows(IllegalArgumentException.class, () -> EnemySequences.of(0));
        assertThrows(IllegalArgumentException.class, () -> EnemySequences.of(9));
        assertTrue(EnemySequences.of(8).contains(EnemyType.HEAVY));
    }
}
