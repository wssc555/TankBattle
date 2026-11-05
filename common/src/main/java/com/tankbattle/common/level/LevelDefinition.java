package com.tankbattle.common.level;

import com.tankbattle.common.config.GameConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 一张关卡的不可变模板：400 个地形格 + 主题描述。
 * <p>
 * 网格索引为线性下标 {@code i}，坐标换算见 {@link Cell}。运行期不修改模板。
 */
public record LevelDefinition(int index, String theme, List<Tile> cells) {

    public LevelDefinition {
        if (cells.size() != GameConfig.CELL_COUNT) {
            throw new IllegalArgumentException("地图格数必须为 " + GameConfig.CELL_COUNT + "，实际 " + cells.size());
        }
        cells = List.copyOf(cells);
    }

    public int rows() {
        return GameConfig.MAP_ROWS;
    }

    public int cols() {
        return GameConfig.MAP_COLS;
    }

    public Tile at(int cellIndex) {
        return cells.get(cellIndex);
    }

    /**
     * 统计某类地形的格数。
     */
    public long count(Tile tile) {
        return cells.stream().filter(t -> t == tile).count();
    }

    /**
     * 某类地形的全部线性下标。
     */
    public List<Integer> indexesOf(Tile tile) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i) == tile) {
                result.add(i);
            }
        }
        return List.copyOf(result);
    }

    /**
     * 草地格下标（图层 6 统一覆盖）。
     */
    public List<Integer> grassCells() {
        return indexesOf(Tile.GRASS);
    }

    /**
     * 河流格下标（静态层）。
     */
    public List<Integer> riverCells() {
        return indexesOf(Tile.RIVER);
    }

    /**
     * 编码为 {@code M} 指令的 400 字符负载。
     */
    public String mapData() {
        return MapSymbols.toMapData(cells);
    }
}
