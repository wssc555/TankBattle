package com.tankbattle.client.state;

import com.tankbattle.client.state.views.BaseView;
import com.tankbattle.client.state.views.WallView;
import com.tankbattle.common.config.GameConfig;
import com.tankbattle.common.level.Cell;
import com.tankbattle.common.level.LevelDefinition;
import com.tankbattle.common.level.MapSymbols;
import com.tankbattle.common.level.Tile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 静态层快照：墙 / 钢墙 / 河流 / 草地 / 基地。
 * <p>
 * 用 {@link Cell#key(int, int)} 哈希索引，{@code w}/{@code s} 更新 O(1)。
 * 普通帧复用同一实例，仅事件帧复制被修改的那一张 Map（结构共享）。{@code version} 自增触发静态层重烘焙。
 */
public record SceneState(
        int level,
        int version,
        Map<Integer, WallView.Brick> bricks,
        Map<Integer, WallView.Steel> steels,
        List<Integer> rivers,
        List<Integer> grass,
        BaseView base) {

    public static final SceneState EMPTY = new SceneState(
            0, 0, Map.of(), Map.of(), List.of(), List.of(), BaseView.EMPTY);

    public SceneState {
        bricks = Map.copyOf(bricks);
        steels = Map.copyOf(steels);
        rivers = List.copyOf(rivers);
        grass = List.copyOf(grass);
    }

    /**
     * 砖墙位图更新；同格若有钢墙（护甲还原）一并移除。
     */
    public SceneState withBrick(int key, int bits) {
        Map<Integer, WallView.Brick> brickCopy = new HashMap<>(bricks);
        brickCopy.put(key, WallView.brick(key, bits));
        Map<Integer, WallView.Steel> steelCopy = steels;
        if (steels.containsKey(key)) {
            steelCopy = new HashMap<>(steels);
            steelCopy.remove(key);
        }
        return new SceneState(level, version + 1, brickCopy, steelCopy, rivers, grass, base);
    }

    /**
     * 钢墙位图更新；同格砖墙（护甲钢化）移除，避免两层重叠渲染。
     */
    public SceneState withSteel(int key, int bits) {
        Map<Integer, WallView.Steel> steelCopy = new HashMap<>(steels);
        steelCopy.put(key, WallView.steel(key, bits));
        Map<Integer, WallView.Brick> brickCopy = bricks;
        if (bricks.containsKey(key)) {
            brickCopy = new HashMap<>(bricks);
            brickCopy.remove(key);
        }
        return new SceneState(level, version + 1, brickCopy, steelCopy, rivers, grass, base);
    }

    /**
     * 基地状态更新；坐标取消息值，不依赖槽位。
     */
    public SceneState withBase(int x, int y, boolean destroyed) {
        return new SceneState(level, version + 1, bricks, steels, rivers, grass,
                new BaseView(x, y, destroyed));
    }

    /**
     * 本地回退：由 common 关卡模板构造场景。
     */
    public static SceneState fromLevel(LevelDefinition definition) {
        return fromTiles(definition.index(), definition.cells());
    }

    /**
     * V2 权威地图：由 {@code M} 负载构造场景。
     */
    public static SceneState fromMapData(int level, String cells400) {
        return fromTiles(level, MapSymbols.parseMapData(cells400));
    }

    private static SceneState fromTiles(int level, List<Tile> tiles) {
        Map<Integer, WallView.Brick> bricks = new HashMap<>();
        Map<Integer, WallView.Steel> steels = new HashMap<>();
        List<Integer> rivers = new ArrayList<>();
        List<Integer> grass = new ArrayList<>();

        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            switch (tile) {
                case BRICK_FULL, BRICK_0, BRICK_1, BRICK_2, BRICK_3 ->
                        bricks.put(i, WallView.brick(i, tile.brickBits()));
                case STEEL_FULL, STEEL_0, STEEL_1, STEEL_2, STEEL_3 ->
                        steels.put(i, WallView.steel(i, tile.steelBits()));
                case GRASS -> grass.add(i);
                case RIVER -> rivers.add(i);
                default -> {
                    // 空地 / 基地标记由固定区域单独处理
                }
            }
        }
        addBaseArmor(bricks);
        return new SceneState(level, 0, bricks, steels, rivers, grass,
                new BaseView(GameConfig.BASE_X, GameConfig.BASE_Y, false));
    }

    /**
     * 基地四周 4 面半砖护甲（图层 4，固定坐标）。
     */
    private static void addBaseArmor(Map<Integer, WallView.Brick> bricks) {
        int[][] armor = {
                {GameConfig.ARMOR_X0, GameConfig.ARMOR_Y0, GameConfig.ARMOR_O0},
                {GameConfig.ARMOR_X1, GameConfig.ARMOR_Y1, GameConfig.ARMOR_O1},
                {GameConfig.ARMOR_X2, GameConfig.ARMOR_Y2, GameConfig.ARMOR_O2},
                {GameConfig.ARMOR_X3, GameConfig.ARMOR_Y3, GameConfig.ARMOR_O3}
        };
        for (int[] cell : armor) {
            int key = Cell.key(cell[0], cell[1]);
            bricks.put(key, WallView.brick(key, orientationBits(cell[2])));
        }
    }

    /**
     * 护甲朝向 → 初始已毁位图（与服务端 BrickWall 朝向语义一致）。
     */
    private static int orientationBits(int orientation) {
        return switch (orientation) {
            case 0 -> Tile.BRICK_0.brickBits();
            case 1 -> Tile.BRICK_1.brickBits();
            case 2 -> Tile.BRICK_2.brickBits();
            case 3 -> Tile.BRICK_3.brickBits();
            default -> throw new IllegalArgumentException("非法朝向: " + orientation);
        };
    }
}
