package com.tankbattle.common.level;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code M} 400 字符字母表 ↔ Tile 往返、非法输入拒绝，以及 {@code #2} 等符号的坐标计算。
 * <p>
 * MapSymbols 及其测试归属 common 模块。
 */
class MapSymbolsTest {

    @Test
    void roundTripsAllLevelsThroughMapAlphabet() {
        for (int level = 1; level <= LevelRepository.count(); level++) {
            LevelDefinition definition = LevelRepository.get(level);
            String encoded = definition.mapData();
            assertEquals(400, encoded.length());
            List<Tile> decoded = MapSymbols.parseMapData(encoded);
            assertEquals(definition.cells(), decoded);
        }
    }

    @Test
    void rejectsIllegalAlphabetAndLength() {
        assertThrows(IllegalArgumentException.class, () -> MapSymbols.parseMapData(".".repeat(399)));
        assertThrows(IllegalArgumentException.class, () -> MapSymbols.parseMapData("X".repeat(400)));
    }

    @Test
    void rejectsUnknownLocalSymbol() {
        assertThrows(IllegalArgumentException.class, () -> MapSymbols.parseSymbol("zz"));
        assertEquals(Tile.EMPTY, MapSymbols.parseSymbol("__"));
        assertEquals(Tile.BRICK_2, MapSymbols.parseSymbol("#2"));
        assertEquals(Tile.STEEL_3, MapSymbols.parseSymbol("s3"));
    }

    /**
     * 验证 {@code #2} 与所有符号统一按 {@code col = i % 20} 计算列号。
     */
    @Test
    void cellIndexUsesModuloTwentyForAllSymbols() {
        int index = Cell.index(2, 1);
        assertEquals(2, Cell.col(index));
        assertEquals(1, Cell.row(index));
        assertEquals(23 + 2 * 25, Cell.x(index));
        assertEquals(23 + 1 * 25, Cell.y(index));
        assertEquals(index, Cell.key(Cell.x(index), Cell.y(index)));
    }

    @Test
    void mapsEveryTileToAProtocolCharacter() {
        for (Tile tile : List.of(Tile.EMPTY, Tile.BRICK_FULL, Tile.BRICK_0, Tile.BRICK_1,
                Tile.BRICK_2, Tile.BRICK_3, Tile.STEEL_FULL, Tile.STEEL_0, Tile.STEEL_1,
                Tile.STEEL_2, Tile.STEEL_3, Tile.GRASS, Tile.RIVER)) {
            char c = MapSymbols.toMapChar(tile);
            assertTrue(MapSymbols.MAP_ALPHABET.indexOf(c) >= 0);
            assertEquals(tile, MapSymbols.fromMapChar(c));
        }
    }
}
