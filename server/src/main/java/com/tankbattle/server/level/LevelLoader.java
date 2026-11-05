package com.tankbattle.server.level;

import com.tankbattle.common.config.GameConfig;
import com.tankbattle.common.level.Cell;
import com.tankbattle.common.level.LevelDefinition;
import com.tankbattle.common.level.LevelRepository;
import com.tankbattle.common.level.Tile;
import com.tankbattle.server.core.GameContext;
import com.tankbattle.server.core.LevelState;
import com.tankbattle.server.entity.Base;
import com.tankbattle.server.entity.BrickWall;
import com.tankbattle.server.entity.Grass;
import com.tankbattle.server.entity.PlayerTank;
import com.tankbattle.server.entity.River;
import com.tankbattle.server.entity.SteelWall;
import com.tankbattle.server.entity.Wall;

import java.util.ArrayList;
import java.util.List;

/**
 * 关卡装配：把 {@code common} 的不可变地图模板实例化为服务端实体。
 * <p>
 * 装配顺序：清空世界 → 4 面护甲 + 基地 → 解析地图 → 追加 P1/P2。
 * 坐标统一由 {@link Cell} 计算（{@code col = i % 20}）。
 */
public final class LevelLoader {

    private static final int[][] ARMOR = {
            {GameConfig.ARMOR_X0, GameConfig.ARMOR_Y0, GameConfig.ARMOR_O0},
            {GameConfig.ARMOR_X1, GameConfig.ARMOR_Y1, GameConfig.ARMOR_O1},
            {GameConfig.ARMOR_X2, GameConfig.ARMOR_Y2, GameConfig.ARMOR_O2},
            {GameConfig.ARMOR_X3, GameConfig.ARMOR_Y3, GameConfig.ARMOR_O3},
    };

    private LevelLoader() {
    }

    /**
     * 加载下一关（关卡序号自增并按曲线提难度）。
     *
     * @return 本次使用的关卡模板
     */
    public static LevelDefinition load(GameContext ctx) {
        LevelState level = ctx.level();
        level.advanceLevel();

        LevelDefinition definition = LevelRepository.get(level.displayLevel());
        level.setSequence(EnemySequences.of(level.displayLevel()));

        ctx.actors().clear();
        ctx.decor().clear();
        ctx.grid().clear();

        List<Wall> armor = new ArrayList<>(ARMOR.length);
        for (int[] spec : ARMOR) {
            armor.add(new BrickWall(spec[0], spec[1], BrickWall.bitsForOrientation(spec[2])));
        }
        for (Wall wall : armor) {
            ctx.addActor(wall);
        }

        Base base = new Base();
        base.attachArmor(armor);
        ctx.installBase(base);
        ctx.addActor(base);

        for (int i = 0; i < definition.cells().size(); i++) {
            instantiate(ctx, definition.at(i), Cell.x(i), Cell.y(i));
        }

        PlayerTank p1 = ctx.p1();
        if (p1 != null) {
            ctx.addActor(p1);
        }
        PlayerTank p2 = ctx.p2();
        if (p2 != null) {
            ctx.addActor(p2);
        }

        ctx.rebuildStaticIndex();
        return definition;
    }

    private static void instantiate(GameContext ctx, Tile tile, int x, int y) {
        if (tile.isBrick()) {
            ctx.addActor(new BrickWall(x, y, tile.brickBits()));
        } else if (tile.isSteel()) {
            ctx.addActor(new SteelWall(x, y, tile.steelBits()));
        } else if (tile == Tile.RIVER) {
            ctx.addActor(new River(x, y));
        } else if (tile == Tile.GRASS) {
            ctx.decor().add(new Grass(x, y));
        } else if (tile != Tile.EMPTY) {
            // BASE / BASE_GUARD_* 由适配器单独放置，地图模板中不应出现
            throw new IllegalStateException("地图模板含不可实例化地形: " + tile);
        }
    }
}
