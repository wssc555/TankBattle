package com.tankbattle.common.level;

import com.tankbattle.common.config.GameConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 地图符号 ↔ {@link Tile} ↔ {@code M} 指令字母表 的双向映射。
 * <p>
 * 本地地图符号（服务端文本块）：{@code __ ## #0..#3 ss s0..s3 $$ ==}。
 * {@code M} 负载字母表：{@code . # a-d S e-h G R}，负载长度必须为 400，禁止分号。
 */
public final class MapSymbols {

    /**
     * {@code M} 指令允许的字符集。
     */
    public static final String MAP_ALPHABET = ".#abcdSefghGR";

    private MapSymbols() {
    }

    /**
     * 本地地图符号 → Tile。
     *
     * @throws IllegalArgumentException 遇到未知符号
     */
    public static Tile parseSymbol(String symbol) {
        return switch (symbol) {
            case "__" -> Tile.EMPTY;
            case "##" -> Tile.BRICK_FULL;
            case "#0" -> Tile.BRICK_0;
            case "#1" -> Tile.BRICK_1;
            case "#2" -> Tile.BRICK_2;
            case "#3" -> Tile.BRICK_3;
            case "ss" -> Tile.STEEL_FULL;
            case "s0" -> Tile.STEEL_0;
            case "s1" -> Tile.STEEL_1;
            case "s2" -> Tile.STEEL_2;
            case "s3" -> Tile.STEEL_3;
            case "$$" -> Tile.GRASS;
            case "==" -> Tile.RIVER;
            case "b0" -> Tile.BASE;
            case "b1" -> Tile.BASE_GUARD_LEFT;
            case "b2" -> Tile.BASE_GUARD_RIGHT;
            case "b3" -> Tile.BASE_GUARD_TOP;
            case "b4" -> Tile.BASE_GUARD_BOTTOM;
            default -> throw new IllegalArgumentException("非法地图符号: [" + symbol + "]");
        };
    }

    /**
     * Tile → 本地地图符号（测试与工具用）。
     */
    public static String symbolOf(Tile tile) {
        return switch (tile) {
            case EMPTY -> "__";
            case BRICK_FULL -> "##";
            case BRICK_0 -> "#0";
            case BRICK_1 -> "#1";
            case BRICK_2 -> "#2";
            case BRICK_3 -> "#3";
            case STEEL_FULL -> "ss";
            case STEEL_0 -> "s0";
            case STEEL_1 -> "s1";
            case STEEL_2 -> "s2";
            case STEEL_3 -> "s3";
            case GRASS -> "$$";
            case RIVER -> "==";
            case BASE -> "b0";
            case BASE_GUARD_LEFT -> "b1";
            case BASE_GUARD_RIGHT -> "b2";
            case BASE_GUARD_TOP -> "b3";
            case BASE_GUARD_BOTTOM -> "b4";
        };
    }

    /**
     * {@code M} 负载字符 → Tile。
     */
    public static Tile fromMapChar(char c) {
        return switch (c) {
            case '.' -> Tile.EMPTY;
            case '#' -> Tile.BRICK_FULL;
            case 'a' -> Tile.BRICK_0;
            case 'b' -> Tile.BRICK_1;
            case 'c' -> Tile.BRICK_2;
            case 'd' -> Tile.BRICK_3;
            case 'S' -> Tile.STEEL_FULL;
            case 'e' -> Tile.STEEL_0;
            case 'f' -> Tile.STEEL_1;
            case 'g' -> Tile.STEEL_2;
            case 'h' -> Tile.STEEL_3;
            case 'G' -> Tile.GRASS;
            case 'R' -> Tile.RIVER;
            default -> throw new IllegalArgumentException("非法地图字符: " + c);
        };
    }

    /**
     * Tile → {@code M} 负载字符。
     */
    public static char toMapChar(Tile tile) {
        return switch (tile) {
            case EMPTY -> '.';
            case BRICK_FULL -> '#';
            case BRICK_0 -> 'a';
            case BRICK_1 -> 'b';
            case BRICK_2 -> 'c';
            case BRICK_3 -> 'd';
            case STEEL_FULL -> 'S';
            case STEEL_0 -> 'e';
            case STEEL_1 -> 'f';
            case STEEL_2 -> 'g';
            case STEEL_3 -> 'h';
            case GRASS -> 'G';
            case RIVER -> 'R';
            default -> throw new IllegalArgumentException("不可编码为 M 的地形: " + tile);
        };
    }

    /**
     * 解析 {@code M} 负载为 400 个 Tile。
     *
     * @throws IllegalArgumentException 长度不为 400 或含字母表之外的字符
     */
    public static List<Tile> parseMapData(String cells400) {
        if (cells400 == null || cells400.length() != GameConfig.CELL_COUNT) {
            throw new IllegalArgumentException(
                    "地图长度不符: 期望 " + GameConfig.CELL_COUNT + "，实际 "
                            + (cells400 == null ? -1 : cells400.length()));
        }
        List<Tile> tiles = new ArrayList<>(GameConfig.CELL_COUNT);
        for (int i = 0; i < cells400.length(); i++) {
            tiles.add(fromMapChar(cells400.charAt(i)));
        }
        return List.copyOf(tiles);
    }

    /**
     * 400 个 Tile → {@code M} 负载字符串。
     */
    public static String toMapData(List<Tile> cells) {
        if (cells.size() != GameConfig.CELL_COUNT) {
            throw new IllegalArgumentException("地图格数不符: " + cells.size());
        }
        StringBuilder out = new StringBuilder(GameConfig.CELL_COUNT);
        for (Tile tile : cells) {
            out.append(toMapChar(tile));
        }
        return out.toString();
    }
}
