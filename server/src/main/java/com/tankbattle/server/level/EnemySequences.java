package com.tankbattle.server.level;

import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.entity.EnemyType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 8 关敌人出场序列（服务端权威）。
 * <p>
 * 与地图解耦：地图模板由 {@code common} 提供，出场顺序属于难度设计，只在服务端使用。
 */
public final class EnemySequences {

    private static final Map<Integer, int[]> TABLES = Map.of(
            1, new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2},
            2, new int[]{1, 1, 2, 2, 1, 1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 3, 3, 3, 3},
            3, new int[]{1, 1, 1, 2, 2, 2, 4, 4, 2, 2, 2, 2, 1, 1, 1, 1, 2, 2, 4, 4},
            4, new int[]{3, 3, 3, 3, 2, 2, 2, 3, 3, 1, 1, 1, 3, 3, 3, 1, 1, 4, 4, 4},
            5, new int[]{2, 2, 2, 3, 3, 3, 2, 2, 2, 4, 4, 4, 3, 3, 3, 3, 3, 2, 2, 2},
            6, new int[]{4, 4, 4, 4, 2, 2, 2, 4, 4, 1, 1, 1, 3, 3, 3, 1, 1, 4, 4, 4},
            7, new int[]{3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 2, 4, 2, 4, 2, 4, 2, 4, 2, 4},
            8, new int[]{3, 4, 4, 2, 3, 4, 4, 2, 3, 4, 4, 2, 3, 4, 4, 2, 3, 4, 4, 2});

    private EnemySequences() {
    }

    /**
     * 取某关（1..8）的出场序列。
     *
     * @throws IllegalArgumentException 关卡编号越界
     */
    public static List<EnemyType> of(int levelIndex) {
        int[] codes = TABLES.get(levelIndex);
        if (codes == null) {
            throw new IllegalArgumentException("关卡编号越界: " + levelIndex);
        }
        if (codes.length != ServerConfig.ENEMY_PER_LEVEL) {
            throw new IllegalStateException("敌人序列长度必须为 " + ServerConfig.ENEMY_PER_LEVEL);
        }
        List<EnemyType> sequence = new ArrayList<>(codes.length);
        for (int code : codes) {
            sequence.add(EnemyType.ofCode(code));
        }
        return List.copyOf(sequence);
    }
}
